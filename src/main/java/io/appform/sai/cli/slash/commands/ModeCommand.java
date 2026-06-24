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
package io.appform.sai.cli.slash.commands;

import io.appform.sai.Printer;
import io.appform.sai.cli.slash.SlashRootCommand;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * {@code /mode [name]} — get or set the active mode for the current session.
 *
 * <p>With no argument, prints the currently active mode (or {@code (none)}). With an argument,
 * sets the active mode and triggers an agent rebuild so subsequent queries use the mode-level
 * tuning overrides. Does not change the model or provider — only the mode tier.
 */
@Command(name = "mode", description = "Get or set the current mode")
public class ModeCommand implements Runnable {

    @ParentCommand
    private SlashRootCommand parent;

    @Parameters(index = "0", arity = "0..1", description = "Mode name. Omit to show current mode.")
    private String mode;

    @Override
    public void run() {
        final var context = parent.getContext();
        final var printer = context.getPrinter();

        if (mode == null || mode.isBlank()) {
            final var currentMode = context.getCurrentMode().get();
            printer.print(Printer.systemMessage(
                                                Printer.Colours.CYAN + "Current mode: " + Printer.Colours.WHITE
                                                        + (currentMode != null ? currentMode : "(none)")
                                                        + Printer.Colours.RESET));
            return;
        }

        context.getCurrentMode().set(mode);
        context.rebuildAgent();
        printer.print(Printer.systemMessage(
                                            Printer.Colours.GREEN + "Mode switched to: " + Printer.Colours.WHITE
                                                    + mode + Printer.Colours.RESET));
    }
}
