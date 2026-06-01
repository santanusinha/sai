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
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * {@code /help} — list all available slash commands with their descriptions.
 */
@Command(name = "help", description = "Show available slash commands")
public class HelpCommand implements Runnable {

    @ParentCommand
    private SlashRootCommand parent;

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        final var printer = parent.getContext().getPrinter();
        final var sb = new StringBuilder();
        sb.append(Printer.Colours.YELLOW).append("Available slash commands:").append(Printer.Colours.RESET).append(
                                                                                                                   '\n');

        spec.parent().subcommands().forEach((name, commandLine) -> {
            final var desc = commandLine.getCommandSpec().usageMessage().description();
            final var description = (desc != null && desc.length > 0) ? desc[0] : "";
            sb.append(Printer.Colours.CYAN)
                    .append("  /").append(name)
                    .append(Printer.Colours.RESET)
                    .append("  ").append(description)
                    .append('\n');
        });

        printer.print(Printer.raw(sb.toString()));
    }
}
