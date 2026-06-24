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
 * {@code /model [provider/model[/mode]]} — get or set the active model for the current session.
 *
 * <p>With no argument, prints the currently active model string. With an argument, validates that
 * it is in {@code provider/model[/mode]} format, updates the session context, and triggers an agent
 * rebuild so subsequent queries use the new model.
 */
@Command(name = "model", description = "Get or set the current model (format: provider/model[/mode])")
public class ModelCommand implements Runnable {

    @ParentCommand
    private SlashRootCommand parent;

    @Parameters(index = "0", arity = "0..1", description = "Model in 'provider/model[/mode]' format. Omit to show current model.")
    private String model;

    @Override
    public void run() {
        final var context = parent.getContext();
        final var printer = context.getPrinter();

        if (model == null || model.isBlank()) {
            printer.print(Printer.systemMessage(
                                                Printer.Colours.CYAN + "Current model: " + Printer.Colours.WHITE
                                                        + context.getCurrentModel().get() + Printer.Colours.RESET));
            return;
        }

        final var parts = model.split("/", 3);
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            printer.print(Printer.systemMessage(
                                                Printer.Colours.RED + "Invalid model format '" + model
                                                        + "'. Expected 'provider/model[/mode]' (e.g. copilot/claude-haiku-4.5)."
                                                        + Printer.Colours.RESET));
            return;
        }

        final var mode = parts.length == 3 ? parts[2] : null;
        context.getCurrentModel().set(model);
        context.getCurrentMode().set(mode);
        context.rebuildAgent();
        printer.print(Printer.systemMessage(
                                            Printer.Colours.GREEN + "Model switched to: " + Printer.Colours.WHITE
                                                    + model + Printer.Colours.RESET));
    }
}
