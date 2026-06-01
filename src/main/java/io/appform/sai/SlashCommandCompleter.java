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
package io.appform.sai;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;

import picocli.CommandLine;

/**
 * A JLine {@link Completer} that provides TAB-completion for slash-commands.
 *
 * <p>It activates only when the current word starts with {@code /}. It then matches the partial
 * command name (the text after the {@code /}) against the names of all sub-commands registered
 * under the given picocli {@link CommandLine} root, and adds a {@link Candidate} for each match.
 *
 * <p>Examples:
 * <pre>
 * /&lt;TAB&gt; → /help /model /persona
 * /mo&lt;TAB&gt; → /model
 * /per&lt;TAB&gt; → /persona
 * </pre>
 */
public class SlashCommandCompleter implements Completer {

    private final CommandLine commandLine;

    /**
     * Create a completer backed by the given picocli root command. The sub-command names are
     * resolved lazily from {@link CommandLine#getSubcommands()} at completion time, so any
     * commands added after construction are also visible.
     *
     * @param commandLine the root {@link CommandLine} whose sub-commands are the completable names
     */
    public SlashCommandCompleter(CommandLine commandLine) {
        this.commandLine = commandLine;
    }

    private static String descriptionOf(CommandLine sub) {
        final var desc = sub.getCommandSpec().usageMessage().description();
        return (desc != null && desc.length > 0) ? desc[0] : null;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        final var word = line.word();
        if (!word.startsWith("/")) {
            return;
        }

        final var prefix = word.substring(1);
        commandLine.getSubcommands().forEach((name, sub) -> {
            if (name.startsWith(prefix)) {
                final var description = descriptionOf(sub);
                candidates.add(new Candidate(
                                             "/" + name,
                                             "/" + name,
                                             null,
                                             description,
                                             null,
                                             null,
                                             true
                ));
            }
        });
    }
}
