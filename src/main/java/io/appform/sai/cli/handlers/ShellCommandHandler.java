/*
 * Copyright (c) 2026 Original Author(s)
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
package io.appform.sai.cli.handlers;

import io.appform.sai.Printer;
import io.appform.sai.cli.CliCommandHandler;
import io.appform.sai.models.Severity;
import io.appform.sai.tools.BashCommandRunner;

import java.time.Duration;
import java.util.ArrayList;

import com.google.common.annotations.VisibleForTesting;

/**
 * Handles the {@code !<command>} prefix: executes the remainder of the input as a shell command
 * and prints stdout (on success) or stderr with exit-code (on failure) back to the user.
 *
 * <p>Examples:
 * <pre>
 * !ls -la
 * ! pwd
 * !echo "hello world"
 * </pre>
 */
public class ShellCommandHandler implements CliCommandHandler {

    private static final Duration SHELL_TIMEOUT = Duration.ofHours(1);

    private final Duration timeout;

    public ShellCommandHandler() {
        this(SHELL_TIMEOUT);
    }

    @VisibleForTesting
    ShellCommandHandler(Duration timeout) {
        this.timeout = timeout;
    }

    @Override
    public boolean canHandle(String input) {
        return input.trim().startsWith("!");
    }

    @Override
    public void handle(String input, Printer printer) {
        final var command = input.trim().substring(1).trim();
        if (command.isEmpty()) {
            printer.print(Printer.systemMessage(
                                                Printer.Colours.YELLOW + "Usage: !<command>  (e.g. !ls -la)"
                                                        + Printer.Colours.RESET));
            return;
        }

        final var output = new BashCommandRunner(command,
                                                 timeout,
                                                 line -> {
                                                     printer.print(Printer.raw(line));
                                                     return line;
                                                 }).call();

        final var messages = new ArrayList<Printer.Update>();
        if (output.getStatusCode() != 0) {
            final var errorText = "Exit code " + output.getStatusCode() + ": "
                    + (output.getStderr().isEmpty() ? "(no output)" : output.getStderr());
            messages.add(Printer.systemMessage(errorText).withSeverity(Severity.ERROR));
        }

        messages.add(Printer.markIdleStatus());
        messages.add(Printer.empty());
        printer.print(messages);
    }
}
