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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.appform.sai.Printer;
import io.appform.sai.Settings;
import io.appform.sai.cli.slash.SlashCommandDispatcher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import lombok.SneakyThrows;

class SlashCommandHandlerTest {

    private static class CapturingPrinter extends Printer {

        final List<Printer.Update> captured = new CopyOnWriteArrayList<>();

        @SneakyThrows
        CapturingPrinter() {
            super(Settings.builder().headless(true).build(),
                  Executors.newSingleThreadExecutor(),
                  null,
                  null);
        }

        @Override
        public void print(List<Printer.Update> updates) {
            captured.addAll(updates);
            super.print(updates);
        }
    }

    private SlashCommandDispatcher dispatcher;
    private SlashCommandHandler handler;
    private CapturingPrinter printer;

    // ── canHandle() ──────────────────────────────────────────────────────────

    @Test
    void canHandleSlashOnly() {
        assertTrue(handler.canHandle("/"));
    }

    @Test
    void canHandleSlashPrefix() {
        assertTrue(handler.canHandle("/help"));
    }

    @Test
    void canHandleSlashPrefixWithLeadingSpace() {
        assertTrue(handler.canHandle("  /model openai/gpt-4o"));
    }

    @Test
    void cannotHandleBlank() {
        assertFalse(handler.canHandle("   "));
    }

    @Test
    void cannotHandleEmpty() {
        assertFalse(handler.canHandle(""));
    }

    @Test
    void cannotHandleExclamationPrefix() {
        assertFalse(handler.canHandle("!ls"));
    }

    @Test
    void cannotHandleNull() {
        assertFalse(handler.canHandle(null));
    }

    @Test
    void cannotHandlePlainText() {
        assertFalse(handler.canHandle("tell me a joke"));
    }

    // ── handle() — delegates to dispatcher ───────────────────────────────────

    @Test
    void handleSlashOnlyPassesEmptyStringToDispatcher() {
        handler.handle("/", printer);
        verify(dispatcher).dispatch("", printer);
    }

    @Test
    void handleStripsLeadingSlashAndDelegates() {
        handler.handle("/help", printer);
        verify(dispatcher).dispatch("help", printer);
    }

    @Test
    void handleStripsLeadingSlashFromModelCommand() {
        handler.handle("/model copilot-proxy/gpt-4o", printer);
        verify(dispatcher).dispatch("model copilot-proxy/gpt-4o", printer);
    }

    @Test
    void handleTrimsLeadingWhitespaceBeforeStrippingSlash() {
        handler.handle("  /persona my-persona", printer);
        verify(dispatcher).dispatch("persona my-persona", printer);
    }

    @BeforeEach
    void setUp() {
        dispatcher = mock(SlashCommandDispatcher.class);
        handler = new SlashCommandHandler(dispatcher);
        printer = new CapturingPrinter();
    }
}
