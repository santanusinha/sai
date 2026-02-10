/*
 * Copyright 2026 authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.appform.sai.tools;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import com.phonepe.sentinelai.core.utils.AgentUtils;

import dev.failsafe.Failsafe;
import dev.failsafe.Timeout;
import dev.failsafe.TimeoutExceededException;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(access = lombok.AccessLevel.PACKAGE)
public class BashCommandRunner implements Callable<BashCommandRunner.CommandOutput> {

    @Value
    public static class CommandOutput {
        int statusCode;
        String stdout;
        String stderr;
    }

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    final String command;
    final Duration timeout;

    public BashCommandRunner(String command) {
        this(command, DEFAULT_TIMEOUT);
    }

    @Override
    public CommandOutput call() {
        final var isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        final var processbuilderArgs = isWindows
                ? new String[]{"cmd.exe", "/c", command}
                : new String[]{"/bin/bash", "-c", command};
        try {
            final var timeoutPolicy = Timeout.<CommandOutput>builder(timeout)
                    .withInterrupt()
                    .build();
            return Failsafe.with(timeoutPolicy).get(() -> {
                final var process = new ProcessBuilder(processbuilderArgs)
                        .redirectErrorStream(true)
                        .start();

                try (final var stdoutStream = reader(process.getInputStream())) {
                    final var stdout = streamToString(stdoutStream);
                    final var statusCode = process.waitFor();
                    if (statusCode == 0) {
                        return new CommandOutput(statusCode, stdout, "");
                    }
                    return new CommandOutput(statusCode, "", stdout);
                }
            });
        } catch (TimeoutExceededException e) {
            return new CommandOutput(-1, "", "Execution timed out after " + timeout.toMinutes() + " minutes");
        } catch (Exception e) {
            final var rootCause = AgentUtils.rootCause(e);
            if (rootCause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                return new CommandOutput(-1, "", "Execution interrupted");
            }
            return new CommandOutput(-1,
                    "",
                    "Error executing command: " + rootCause.getMessage());
        }
    }

    private String streamToString(final BufferedReader reader) {
        return reader.lines()
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private static BufferedReader reader(final InputStream stream) {
        return new BufferedReader(new InputStreamReader(stream));
    }
}
