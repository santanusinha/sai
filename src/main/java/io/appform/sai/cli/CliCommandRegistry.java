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
import io.appform.sai.cli.handlers.ShellCommandHandler;

import java.util.List;
import java.util.Optional;

/**
 * Registry that holds all known {@link CliCommandHandler} instances and dispatches user input to
 * the first matching handler.
 *
 * <p>To register additional handlers in the future, add them to the {@code HANDLERS} list.
 */
public class CliCommandRegistry {

    /**
     * All registered handlers. Handlers in this list <em>must</em> be stateless and
     * thread-safe — a single shared instance is reused across all calls.
     */
    private static final List<CliCommandHandler> HANDLERS = List.of(new ShellCommandHandler());

    // Future handlers: new HelpCommandHandler(), new ClearCommandHandler(), …

    /**
     * Returns the first handler that claims ownership of {@code input}, or empty if none match.
     *
     * @param input raw user input
     * @return matching handler, or {@link Optional#empty()}
     */
    public Optional<CliCommandHandler> findHandler(String input) {
        return HANDLERS.stream()
                .filter(h -> h.canHandle(input))
                .findFirst();
    }

    /**
     * Convenience method: route {@code input} to a matching handler if one exists.
     *
     * @param input   raw user input
     * @param printer the active {@link Printer}
     * @return {@code true} if a handler was found and executed, {@code false} otherwise
     */
    public boolean tryHandle(String input, Printer printer) {
        final var handler = findHandler(input);
        handler.ifPresent(h -> h.handle(input, printer));
        return handler.isPresent();
    }
}
