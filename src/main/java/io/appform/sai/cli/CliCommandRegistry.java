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
 * <p>The no-arg constructor initialises the registry with the built-in {@link ShellCommandHandler}
 * ({@code !} prefix). To register additional handlers (e.g., the slash-command handler), use
 * {@link #CliCommandRegistry(List)}.
 */
public class CliCommandRegistry {

    /**
     * All registered handlers. Evaluated in order; the first handler whose
     * {@link CliCommandHandler#canHandle(String)} returns {@code true} is used.
     */
    private final List<CliCommandHandler> handlers;

    /**
     * Constructs a registry with the default built-in handlers:
     * <ul>
     * <li>{@link ShellCommandHandler} — {@code !} prefix for shell pass-through</li>
     * </ul>
     */
    public CliCommandRegistry() {
        this(List.of(new ShellCommandHandler()));
    }

    /**
     * Constructs a registry with a caller-supplied handler list. Use this constructor when
     * additional stateful handlers (e.g., a slash-command handler with session context) are
     * required.
     *
     * @param handlers ordered list of handlers; must not be {@code null}
     */
    public CliCommandRegistry(List<CliCommandHandler> handlers) {
        this.handlers = List.copyOf(handlers);
    }

    /**
     * Returns the first handler that claims ownership of {@code input}, or empty if none match.
     *
     * @param input raw user input
     * @return matching handler, or {@link Optional#empty()}
     */
    public Optional<CliCommandHandler> findHandler(String input) {
        return handlers.stream()
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
