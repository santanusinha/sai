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

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * {@code /skills} — list all agent skills that are available in the current session.
 *
 * <p>Delegates to {@link com.phonepe.sentinelai.filesystem.skills.AgentSkillsExtension#listSkills()}
 * to retrieve the skills catalog and prints it to the terminal. If no skills extension is
 * configured (e.g. in headless or test mode), a short informational message is shown instead.
 */
@Command(name = "skills", description = "List available agent skills")
public class SkillsCommand implements Runnable {

    @ParentCommand
    private SlashRootCommand parent;

    @Override
    public void run() {
        final var context = parent.getContext();
        final var printer = context.getPrinter();
        final var extension = context.getAgentSkillsExtension();

        if (extension == null) {
            printer.print(Printer.systemMessage(
                                                Printer.Colours.YELLOW
                                                        + "No skills extension is configured for this session."
                                                        + Printer.Colours.RESET));
            return;
        }

        final var catalog = extension.listSkills();
        printer.print(Printer.raw(Printer.Colours.YELLOW + "Available skills:\n" + Printer.Colours.RESET + catalog));
    }
}
