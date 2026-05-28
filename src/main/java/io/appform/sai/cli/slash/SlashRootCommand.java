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
package io.appform.sai.cli.slash;

import io.appform.sai.cli.slash.commands.HelpCommand;
import io.appform.sai.cli.slash.commands.ModelCommand;
import io.appform.sai.cli.slash.commands.PersonaCommand;

import lombok.Getter;
import lombok.Setter;
import picocli.CommandLine.Command;

/**
 * Root picocli command for the slash-command subsystem. An instance is created once per session and
 * wired with a {@link SlashCommandContext} by {@link SlashCommandDispatcher} before any command is
 * dispatched.
 *
 * <p>The root command itself is a no-op {@link Runnable}; all real work is done by its subcommands
 * ({@code /help}, {@code /model}, {@code /persona}).
 */
@Getter
@Command(name = "", mixinStandardHelpOptions = false, subcommands = {
        HelpCommand.class,
        ModelCommand.class,
        PersonaCommand.class
})
public class SlashRootCommand implements Runnable {

    @Setter
    private SlashCommandContext context;

    @Override
    public void run() {
        // Root command is a no-op; subcommands do the real work.
    }
}
