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
package io.appform.sai.cli.slash;

import io.appform.sai.Printer;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

/**
 * Dispatches slash-commands to their respective picocli sub-commands. A single instance is created
 * per interactive session and reused for every {@code /}-prefixed input line.
 *
 * <p>The dispatcher:
 * <ol>
 * <li>Builds a {@link CommandLine} tree rooted at {@link SlashRootCommand}.</li>
 * <li>Injects a {@link SlashCommandContext} into the root command.</li>
 * <li>Bridges picocli stdout/stderr to the SAI {@link Printer} via {@link PrinterWriter}.</li>
 * <li>Tokenizes the stripped input (shell-style, honouring quoted strings).</li>
 * <li>Calls {@link CommandLine#execute(String...)} with the tokenized arguments.</li>
 * </ol>
 */
@Slf4j
public class SlashCommandDispatcher {

    private final CommandLine commandLine;
    private final SlashCommandContext context;

    public SlashCommandDispatcher(SlashCommandContext context) {
        this.context = context;
        final var rootCommand = new SlashRootCommand();
        rootCommand.setContext(context);
        this.commandLine = new CommandLine(rootCommand);

        final var printerWriter = new PrinterWriter(context.getPrinter());
        this.commandLine.setOut(new PrintWriter(printerWriter, true));
        this.commandLine.setErr(new PrintWriter(printerWriter, true));
    }

    /**
     * Split an input string into an argument array using shell-style tokenization. Tokens are
     * separated by unquoted whitespace. Single-quoted and double-quoted substrings are treated as
     * single tokens (quotes are stripped from the result).
     *
     * @param input the raw input string (after the leading {@code /} has been removed)
     * @return array of tokens, or an empty array for blank input
     */
    static String[] tokenize(String input) {
        if (input == null || input.isBlank()) {
            return new String[0];
        }

        final List<String> tokens = new ArrayList<>();
        final var sb = new StringBuilder();
        var inSingleQuote = false;
        var inDoubleQuote = false;

        for (int i = 0; i < input.length(); i++) {
            final var ch = input.charAt(i);

            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            }
            else if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            }
            else if (Character.isWhitespace(ch) && !inSingleQuote && !inDoubleQuote) {
                if (!sb.isEmpty()) {
                    tokens.add(sb.toString());
                    sb.setLength(0);
                }
            }
            else {
                sb.append(ch);
            }
        }

        if (!sb.isEmpty()) {
            tokens.add(sb.toString());
        }

        return tokens.toArray(new String[0]);
    }

    /**
     * Dispatch a slash-command line to the appropriate picocli sub-command.
     *
     * @param rawInput the user input with the leading {@code /} already stripped
     * @param printer  the active printer (used for usage messages on bare input)
     */
    public void dispatch(String rawInput, Printer printer) {
        final var args = tokenize(rawInput);
        if (args.length == 0) {
            printer.print(Printer.systemMessage(
                                                Printer.Colours.YELLOW + "Usage: /<command> [args]  (try /help)"
                                                        + Printer.Colours.RESET));
            return;
        }
        commandLine.execute(args);
    }

    /**
     * Returns the picocli {@link CommandLine} root used by this dispatcher. Exposed so that
     * completion infrastructure (e.g. {@link io.appform.sai.SlashCommandCompleter}) can inspect
     * the registered sub-commands without duplicating the command list.
     *
     * @return the root {@link CommandLine} instance
     */
    public CommandLine getCommandLine() {
        return commandLine;
    }
}
