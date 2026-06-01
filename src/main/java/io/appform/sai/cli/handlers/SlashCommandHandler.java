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
package io.appform.sai.cli.handlers;

import io.appform.sai.Printer;
import io.appform.sai.cli.CliCommandHandler;
import io.appform.sai.cli.slash.SlashCommandDispatcher;

import lombok.RequiredArgsConstructor;

/**
 * Handles the {@code /<command>} prefix: intercepts user input starting with {@code /} and
 * delegates it to the {@link SlashCommandDispatcher} for parsing and execution.
 *
 * <p>Examples:
 * <pre>
 * /help
 * /model copilot-proxy/claude-haiku-4.5
 * /persona my-persona
 * </pre>
 */
@RequiredArgsConstructor
public class SlashCommandHandler implements CliCommandHandler {

    private final SlashCommandDispatcher dispatcher;

    @Override
    public boolean canHandle(String input) {
        return input != null && input.trim().startsWith("/");
    }

    @Override
    public void handle(String input, Printer printer) {
        final var stripped = input.trim().substring(1);
        dispatcher.dispatch(stripped, printer);
    }
}
