/*
 * Copyright (c) 2025 Original Author(s)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.appform.sai.tools;

import com.phonepe.sentinelai.core.utils.AgentUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs a shell command with a wall-clock timeout.
 *
 * <p>Implementation note: {@link java.io.InputStream#read()} is <em>not</em> interruptible on
 * Linux — calling {@link Thread#interrupt()} on a thread blocked inside a pipe read has no effect.
 * We therefore collect stdout on a daemon reader thread while the calling thread uses
 * {@link Process#waitFor(long, TimeUnit)}, which <em>is</em> interruptible. On timeout we call
 * {@link Process#destroyForcibly()}, which closes the write end of the pipe and causes the daemon
 * reader to see EOF immediately.
 */
@AllArgsConstructor(access = lombok.AccessLevel.PUBLIC)
@Slf4j
public class BashCommandRunner implements Callable<BashCommandRunner.CommandOutput> {

    private static final long READER_GRACE_SECONDS = 2;

    private static final ExecutorService DAEMON_EXECUTOR = Executors.newCachedThreadPool(r -> {
        final var t = new Thread(r);
        t.setDaemon(true);
        t.setName("bash-command-runner-reader");
        return t;
    });

    @Value
    public static class CommandOutput {
        int statusCode;
        String stdout;
        String stderr;
    }

    private final String command;
    private final Duration timeout;
    private final UnaryOperator<String> lineConsumer;

    private static void killTree(final Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private static BufferedReader reader(final InputStream stream) {
        return new BufferedReader(new InputStreamReader(stream));
    }

    private static String snapshot(final List<String> collected) {
        synchronized (collected) {
            return String.join(System.lineSeparator(), collected);
        }
    }

    @Override
    public CommandOutput call() {
        final var isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        final var processbuilderArgs = isWindows
                ? new String[]{
                        "cmd.exe", "/c", command
                }
                : new String[]{
                        "/bin/bash", "-c", command
                };
        var statusCode = -1;
        final var collected = new ArrayList<String>();
        final var taskLatch = new CountDownLatch(1);
        Process process = null;
        try {
            process = new ProcessBuilder(processbuilderArgs)
                    .redirectErrorStream(true)
                    .start();
            final var started = process;
            // Nobody writes to the command: close stdin so programs that read it see EOF, not a hang.
            process.getOutputStream().close();

            // Collect stdout on a daemon thread so the main thread can use waitFor(timeout),
            // which is interruptible — unlike InputStream.read(). Lines are stored as they arrive so
            // partial output survives a timeout or a background child that keeps the pipe open.
            DAEMON_EXECUTOR.submit(() -> {
                try (final var stdoutStream = reader(started.getInputStream())) {
                    stdoutStream.lines().forEach(line -> {
                        final var consumed = lineConsumer.apply(line);
                        synchronized (collected) {
                            collected.add(consumed);
                        }
                    });
                }
                catch (Exception e) {
                    log.error("Error reading stdout: {}", AgentUtils.rootCause(e).getMessage());
                }
                finally {
                    taskLatch.countDown();
                }
            });

            final boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);

            if (!finished) {
                // Kill the whole tree: a surviving child keeps the pipe open and the reader blocked.
                killTree(process);
                taskLatch.await(READER_GRACE_SECONDS, TimeUnit.SECONDS);
                return new CommandOutput(-1,
                                         snapshot(collected),
                                         "Execution timed out after " + timeout.toSeconds() + " seconds");
            }

            // The shell has exited. A background child that inherited the pipe may keep it open, so
            // wait only briefly and return what was received instead of failing.
            final var drained = taskLatch.await(READER_GRACE_SECONDS, TimeUnit.SECONDS);
            if (!drained) {
                log.warn("Output stream still open after command exit (background process?): {}", command);
            }
            final var stdout = snapshot(collected);
            statusCode = process.exitValue();
            if (statusCode == 0) {
                return new CommandOutput(statusCode, stdout, "");
            }
            return new CommandOutput(statusCode, "", stdout);
        }
        catch (InterruptedException e) {
            if (null != process) {
                killTree(process);
                try {
                    taskLatch.await(READER_GRACE_SECONDS, TimeUnit.SECONDS);
                }
                catch (InterruptedException interrupted) {
                    e.addSuppressed(interrupted);
                }
            }
            Thread.currentThread().interrupt();
            return new CommandOutput(statusCode, "", "Execution interrupted");
        }
        catch (Exception e) {
            final var rootCause = AgentUtils.rootCause(e);
            return new CommandOutput(statusCode,
                                     "",
                                     "Error executing command: " + rootCause);
        }
    }
}
