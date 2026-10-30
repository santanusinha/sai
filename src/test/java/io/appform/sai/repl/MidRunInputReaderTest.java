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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.phonepe.sentinelai.core.hooks.PendingMessagesQueue;

import io.appform.sai.term.Printer;

import org.jline.terminal.Terminal;
import org.junit.jupiter.api.Test;

/**
 * Lifecycle tests for {@link MidRunInputReader}. On a dumb terminal the read
 * loop exits immediately, which makes the join-based stop observable and
 * deterministic without a real tty.
 */
class MidRunInputReaderTest {

    private static Printer printerWithDumbTerminal() {
        final var printer = mock(Printer.class);
        final var terminal = mock(Terminal.class);
        when(printer.getTerminal()).thenReturn(terminal);
        when(terminal.getType()).thenReturn(Terminal.TYPE_DUMB);
        return printer;
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
}
