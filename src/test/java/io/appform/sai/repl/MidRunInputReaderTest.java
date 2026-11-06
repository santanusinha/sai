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
package io.appform.sai.repl;


import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.phonepe.sentinelai.core.hooks.PendingMessagesQueue;

import io.appform.sai.term.Printer;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlocking;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lifecycle tests for {@link MidRunInputReader}. On a dumb terminal the read
 * loop exits immediately, which makes the join-based stop observable and
 * deterministic without a real tty.
 */
class MidRunInputReaderTest {

    /**
     * Polls the condition until it holds or a bounded wait of 5 seconds ends.
     */
    private static void awaitCondition(final java.util.function.BooleanSupplier condition) {
        final var deadline = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("condition not met within 5 seconds");
            }
            try {
                Thread.sleep(20);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    private static Printer printerWithDumbTerminal() {
        final var printer = mock(Printer.class);
        final var terminal = mock(Terminal.class);
        when(printer.getTerminal()).thenReturn(terminal);
        when(terminal.getType()).thenReturn(Terminal.TYPE_DUMB);
        return printer;
    }

    /**
     * Returns a printer whose terminal serves the given input and records the
     * raw-mode attributes set on the terminal.
     */
    private static RawAwarePrinter printerWithInput(final String input) {
        final var printer = mock(Printer.class);
        final var terminal = mock(Terminal.class);
        when(printer.getTerminal()).thenReturn(terminal);
        when(terminal.getType()).thenReturn("xterm-256color");
        final var setAttributes = new AtomicReference<Attributes>();
        final var savedAttributes = new Attributes();
        when(terminal.enterRawMode()).thenReturn(savedAttributes);
        try {
            org.mockito.Mockito.doAnswer(invocation -> {
                setAttributes.set(invocation.getArgument(0));
                return null;
            }).when(terminal).setAttributes(any(Attributes.class));
            when(terminal.reader()).thenReturn(NonBlocking.nonBlocking("test", new StringReader(input)));
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return new RawAwarePrinter(printer, terminal, setAttributes);
    }

    private static Thread readerThreadOf(final MidRunInputReader reader) {
        try {
            final var field = MidRunInputReader.class.getDeclaredField("readerThread");
            field.setAccessible(true);
            return (Thread) field.get(reader);
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * EOF on the terminal must not throw: the loop exits quietly and resets
     * the running flag.
     */
    @Test
    void eofExitsQuietly() {
        final var setup = printerWithInput("z");
        final var reader = new MidRunInputReader(setup.printer(),
                                                 new PendingMessagesQueue(),
                                                 "session-1",
                                                 new PauseGate(() -> {}));
        reader.start();
        reader.stop();
        final var thread = readerThreadOf(reader);
        assertTrue(thread == null || !thread.isAlive(), "EOF must exit the reader quietly");
    }

    @Test
    void restartAfterStopStartsANewThread() {
        final var reader = new MidRunInputReader(printerWithDumbTerminal(),
                                                 new PendingMessagesQueue(),
                                                 "session-1",
                                                 new PauseGate(() -> {}));
        reader.start();
        final var first = readerThreadOf(reader);
        reader.stop();
        reader.start();
        final var second = readerThreadOf(reader);
        assertTrue(first == null || second != first, "start() after stop() must create a fresh reader thread");
        reader.stop();
    }

    /**
     * The reader must enter raw mode for the run and restore the earlier
     * attributes before the thread exits, so the JLine prompt between runs
     * works as before.
     */
    @Test
    void runHoldsRawModeAndRestoresAttributesOnExit() {
        final var setup = printerWithInput("");
        final var reader = new MidRunInputReader(setup.printer(),
                                                 new PendingMessagesQueue(),
                                                 "session-1",
                                                 new PauseGate(() -> {}));
        reader.start();
        awaitCondition(() -> readerThreadOf(reader) != null);
        reader.stop();
        final var thread = readerThreadOf(reader);
        awaitCondition(() -> thread == null || !thread.isAlive());
        verify(setup.terminal()).enterRawMode();
        verify(setup.terminal()).setAttributes(any(Attributes.class));
    }

    @Test
    void secondStopIsSafe() {
        final var reader = new MidRunInputReader(printerWithDumbTerminal(),
                                                 new PendingMessagesQueue(),
                                                 "session-1",
                                                 new PauseGate(() -> {}));
        reader.start();
        reader.stop();
        reader.stop();
    }

    @Test
    void stopJoinsReaderThreadBeforeReturning() {
        final var reader = new MidRunInputReader(printerWithDumbTerminal(),
                                                 new PendingMessagesQueue(),
                                                 "session-1",
                                                 new PauseGate(() -> {}));
        reader.start();
        reader.stop();
        final var thread = readerThreadOf(reader);
        assertTrue(thread == null || !thread.isAlive(), "stop() must return only after the reader thread exits");
    }

    @Test
    void stopWhenNeverStartedIsSafe() {
        final var reader = new MidRunInputReader(printerWithDumbTerminal(),
                                                 new PendingMessagesQueue(),
                                                 "session-1",
                                                 new PauseGate(() -> {}));
        reader.stop();
    }

    /**
     * When the reader thread dies, the running flag must reset. Otherwise
     * start() on the next run is a no-op and the reader stays dead forever.
     */
    @Test
    void threadDeathResetsRunningFlagSoNextStartWorks() throws Exception {
        final var setup = printerWithInput("abc");
        final var reader = new MidRunInputReader(setup.printer(),
                                                 new PendingMessagesQueue(),
                                                 "session-1",
                                                 new PauseGate(() -> {}));
        reader.start();
        final var thread = readerThreadOf(reader);
        assertTrue(thread != null, "start() must create the reader thread");
        // The loop reads the input to EOF, which ends the thread naturally.
        awaitCondition(() -> !thread.isAlive());
        final var running = MidRunInputReader.class.getDeclaredField("running");
        running.setAccessible(true);
        final var flag = (java.util.concurrent.atomic.AtomicBoolean) running.get(reader);
        awaitCondition(() -> !flag.get());
        assertFalse(flag.get(), "thread death must reset running so the next start() works");
        // start() must now create a fresh thread.
        reader.start();
        final var second = readerThreadOf(reader);
        assertTrue(second != null && second != thread, "start() after thread death must create a fresh thread");
        reader.stop();
    }

    private record RawAwarePrinter(
            Printer printer,
            Terminal terminal,
            AtomicReference<Attributes> setAttributes
    ) {
    }
}
