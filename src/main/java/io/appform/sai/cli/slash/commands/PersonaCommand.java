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
package io.appform.sai.cli.slash.commands;

import io.appform.sai.Printer;
import io.appform.sai.cli.slash.SlashRootCommand;
import io.appform.sai.config.AgentConfigLoader;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * {@code /persona [path]} — get or set the active persona for the current session.
 *
 * <p>With no argument, prints the name of the currently active persona. With an argument, resolves
 * and loads the persona file, updates the session context (config and model), and triggers an agent
 * rebuild so subsequent queries use the new persona.
 */
@Slf4j
@Command(name = "persona", description = "Load a persona file (.yaml/.yml/.json)")
public class PersonaCommand implements Runnable {

    @ParentCommand
    private SlashRootCommand parent;

    @Parameters(index = "0", arity = "0..1", description = "Path to persona file. Omit to show active persona name.")
    private String personaPath;

    @Override
    public void run() {
        final var context = parent.getContext();
        final var printer = context.getPrinter();

        if (personaPath == null || personaPath.isBlank()) {
            final var currentConfig = context.getCurrentAgentConfig().get();
            printer.print(Printer.systemMessage(
                                                Printer.Colours.CYAN + "Current persona: " + Printer.Colours.WHITE
                                                        + currentConfig.getName() + Printer.Colours.RESET));
            return;
        }

        try {
            final var resolvedPath = AgentConfigLoader.resolvePersonaPath(
                                                                          personaPath,
                                                                          context.getSettings().getConfigDir());
            final var newConfig = AgentConfigLoader.load(resolvedPath, context.getMapper());

            context.getCurrentAgentConfig().set(newConfig);

            if (newConfig.getModel() != null && !newConfig.getModel().isBlank()) {
                context.getCurrentModel().set(newConfig.getModel());
            }

            context.rebuildAgent();

            printer.print(Printer.systemMessage(
                                                Printer.Colours.GREEN + "Persona loaded: " + Printer.Colours.WHITE
                                                        + newConfig.getName()
                                                        + Printer.Colours.GRAY + " (model: " + context.getCurrentModel()
                                                                .get() + ")"
                                                        + Printer.Colours.RESET));
        }
        catch (IllegalArgumentException e) {
            log.warn("Failed to load persona '{}': {}", personaPath, e.getMessage());
            printer.print(Printer.systemMessage(
                                                Printer.Colours.RED + "Failed to load persona '" + personaPath + "': "
                                                        + e.getMessage() + Printer.Colours.RESET));
        }
    }
}
