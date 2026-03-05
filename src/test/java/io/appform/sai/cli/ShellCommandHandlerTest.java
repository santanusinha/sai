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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.appform.sai.Printer;
import io.appform.sai.Settings;
import io.appform.sai.cli.handlers.ShellCommandHandler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class ShellCommandHandlerTest {

    /**
     * Wraps a real headless {@link Printer} built with a dumb terminal; intercepts
     * {@link Printer#print(List)} to expose updates to tests.
     */
    private static class InterceptingPrinter extends Printer {

        private List<Printer.Update> capture = new CopyOnWriteArrayList<>();
        private final ExecutorService executorService;

        InterceptingPrinter() throws Exception {
            super(Settings.builder().headless(true).build(),
                  Executors.newSingleThreadExecutor(),
                  null,
                  null);
            this.executorService = Executors.newSingleThreadExecutor();
        }

        @Override
        public void close() throws IOException {
            super.close();
            executorService.shutdown();
            try {
                executorService.awaitTermination(1, TimeUnit.SECONDS);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void print(List<Printer.Update> updates) {
            capture.addAll(updates);
            super.print(updates);
        }

        Printer getPrinter() {
            return this;
        }

        void setCapture(List<Printer.Update> capture) {
            this.capture = capture;
        }
    }

    private ShellCommandHandler handler;

    private InterceptingPrinter interceptingPrinter;

    private static boolean updatesContain(List<Printer.Update> updates, String substring) {
        return updates.stream()
                .anyMatch(u -> u.getData() != null && u.getData().contains(substring));
    }

    // ── canHandle ──────────────────────────────────────────────────────────────

    @Test
    void canHandle_returnsFalseForExitCommand() {
        assertFalse(handler.canHandle("exit"));
    }

    @Test
    void canHandle_returnsFalseForNormalInput() {
        assertFalse(handler.canHandle("hello world"));
    }

    @Test
    void canHandle_returnsFalseForSlashCommand() {
        assertFalse(handler.canHandle("/help"));
    }

    @Test
    void canHandle_returnsTrueForBangPrefix() {
        assertTrue(handler.canHandle("!ls"));
    }

    @Test
    void canHandle_returnsTrueForBangWithSpace() {
        assertTrue(handler.canHandle("! pwd"));
    }

    @Test
    void canHandle_returnsTrueForLeadingWhitespaceBeforeBang() {
        assertTrue(handler.canHandle("  !echo hello"));
    }

    // ── handle (verifying Update objects constructed by the handler) ───────────

    @Test
    void handle_commandExceedingTimeout_reportsTimeoutError() {
        // Use a 1-second timeout so the test completes quickly
        // Use an anonymous subclass to access the protected timeout constructor
        final ShellCommandHandler shortTimeoutHandler = new ShellCommandHandler(Duration.ofSeconds(1)) {
        };
        final List<Printer.Update> captured = new CopyOnWriteArrayList<>();
        interceptingPrinter.setCapture(captured);

        // assertTimeoutPreemptively ensures the test itself does not hang
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            shortTimeoutHandler.handle("!sleep 10", interceptingPrinter.getPrinter());
            // Allow async Printer queue to drain
            Thread.sleep(300);
        }, "handler should complete well within 5 s despite a 10-second sleep command");

        assertTrue(updatesContain(captured, "Exit code"),
                   "Expected exit-code error message on timeout");
        assertTrue(updatesContain(captured, "timed out"),
                   "Expected 'timed out' in the error message");
    }

    @Test
    void handle_commandWithArguments_works() {
        final var updates = captureUpdates("!echo hello world");
        assertTrue(updatesContain(updates, "hello world"),
                   "Expected full argument string in captured output");
    }

    @Test
    void handle_emptyCommandAfterBangWithSpaces_printsUsage() {
        final var updates = captureUpdates("!   ");
        assertTrue(updatesContain(updates, "Usage"),
                   "Expected usage hint when only whitespace follows '!'");
    }

    @Test
    void handle_emptyCommandAfterBang_printsUsage() {
        final var updates = captureUpdates("!");
        assertTrue(updatesContain(updates, "Usage"),
                   "Expected usage hint when no command follows '!'");
    }

    @Test
    void handle_failingCommand_containsExitCode() {
        // 'false' always exits with code 1 on POSIX systems
        final var updates = captureUpdates("!false");
        assertTrue(updatesContain(updates, "Exit code"),
                   "Expected 'Exit code' error message for a failing command");
    }

    // ── timeout handling ──────────────────────────────────────────────────────

    @Test
    void handle_successfulEchoCommand_printsOutput() {
        final var updates = captureUpdates("!echo sai_test_output");
        assertTrue(updatesContain(updates, "sai_test_output"),
                   "Expected stdout to contain the echoed string");
    }

    // ── registry integration ───────────────────────────────────────────────────

    @Test
    void registry_routesBangToShellHandler() {
        final var registry = new CliCommandRegistry();
        assertTrue(registry.findHandler("!ls").isPresent(), "Registry should find handler for !ls");
        assertFalse(registry.findHandler("normal input").isPresent(),
                    "Registry should return empty for normal input");
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws Exception {
        handler = new ShellCommandHandler();
        interceptingPrinter = new InterceptingPrinter();
    }

    @AfterEach
    void tearDown() throws Exception {
        interceptingPrinter.close();
    }

    /**
     * Invokes the handler with the given input and collects the resulting {@link Printer.Update}
     * objects via a capturing subclass approach.
     */
    private List<Printer.Update> captureUpdates(String input) {
        final List<Printer.Update> captured = new CopyOnWriteArrayList<>();
        interceptingPrinter.setCapture(captured);
        handler.handle(input, interceptingPrinter.getPrinter());
        // Give the async queue a moment to drain
        try {
            Thread.sleep(300);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Collections.unmodifiableList(captured);
    }
}
