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
package io.appform.sai.cli;

import io.appform.sai.Printer;

/**
 * Interface for client-side CLI command handlers. Implementations intercept user input before it
 * is forwarded to the AI agent.
 */
public interface CliCommandHandler {

    /**
     * Returns {@code true} if this handler can process the given user input.
     *
     * @param input raw user input (never null)
     * @return {@code true} when this handler owns the input
     */
    boolean canHandle(String input);

    /**
     * Process the user input. Called only when {@link #canHandle(String)} returns {@code true}.
     *
     * @param input   raw user input
     * @param printer the active {@link Printer} for writing output
     */
    void handle(String input, Printer printer);
}
