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
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

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

    private static BufferedReader reader(final InputStream stream) {
        return new BufferedReader(new InputStreamReader(stream));
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
        try {
            final var process = new ProcessBuilder(processbuilderArgs)
                    .redirectErrorStream(true)
                    .start();

            // Collect stdout on a daemon thread so the main thread can use waitFor(timeout),
            // which is interruptible — unlike InputStream.read().
            final var readerTask = DAEMON_EXECUTOR.submit(() -> {
                try (final var stdoutStream = reader(process.getInputStream())) {
                    return Optional.ofNullable(streamToString(stdoutStream));
                }
                catch (Exception e) {
                    final var rootCause = AgentUtils.rootCause(e);
                    log.error("Error reading stdout: {}", rootCause.getMessage());
                    return Optional.<String>empty();
                }
            });

            final boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);

            if (!finished) {
                process.destroyForcibly();
                // destroyForcibly closes the write-end of the pipe; the reader sees EOF quickly.
                readerTask.cancel(true);
                return new CommandOutput(-1, "", "Execution timed out after " + timeout.toSeconds() + " seconds");
            }

            final var stdout = readerTask.get(5, TimeUnit.SECONDS).orElse("");
            statusCode = process.exitValue();
            if (statusCode == 0) {
                return new CommandOutput(statusCode, stdout, "");
            }
            return new CommandOutput(statusCode, "", stdout);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandOutput(statusCode, "", "Execution interrupted");
        }
        catch (Exception e) {
            final var rootCause = AgentUtils.rootCause(e);
            return new CommandOutput(statusCode,
                                     "",
                                     "Error executing command: " + rootCause.getMessage());
        }
    }

    private String streamToString(final BufferedReader reader) {
        return reader.lines()
                .map(lineConsumer)
                .collect(Collectors.joining(System.lineSeparator()));
    }
}
