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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessResult;
import com.phonepe.sentinelai.core.hooks.PendingMessagesQueue;

import io.appform.sai.term.Printer;

import org.jline.terminal.Terminal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tests for {@link PauseGate} and the {@link MidRunInputReader} pause-path
 * integration: the gate must block a model call until released, and the reader
 * must release the gate on stop so no call is left blocked.
 */
class PauseGateTest {

    private static final List<AgentMessage> MESSAGES = List.of();

    private static Printer mockPrinter() {
        final var printer = mock(Printer.class);
        final var terminal = mock(Terminal.class);
        when(printer.getTerminal()).thenReturn(terminal);
        when(terminal.getType()).thenReturn(Terminal.TYPE_DUMB);
        return printer;
    }

    @Test
    void openIsIdempotent() {
        final var opened = new AtomicBoolean(false);
        final var gate = new PauseGate(() -> opened.set(true), 60);
        gate.open();
        gate.open();
        assertTrue(opened.get(), "onPause must run when the gate opens");
    }

    @Test
    void processBlocksUntilReleasedWhenOpen() throws Exception {
        final var paused = new AtomicBoolean(false);
        final var gate = new PauseGate(() -> paused.set(true), 60);
        gate.open();
        final var executor = Executors.newSingleThreadExecutor();
        try {
            final var started = new CountDownLatch(1);
            final var future = executor.submit(() -> {
                started.countDown();
                return gate.process(null, MESSAGES, MESSAGES);
            });
            assertTrue(started.await(2, TimeUnit.SECONDS), "process must start");
            assertTrue(paused.get(), "onPause must run when the gate opens");
            Thread.sleep(200);
            assertFalse(future.isDone(), "process must still be blocked before release");
            gate.release();
            final var result = future.get(5, TimeUnit.SECONDS);
            assertEquals(0, result.getTransformedMessages().size(), "release must let the call proceed");
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    void processResumesAfterTimeout() throws Exception {
        final var gate = new PauseGate(() -> {
        }, 1);
        gate.open();
        final long start = System.nanoTime();
        final var result = gate.process(null, MESSAGES, MESSAGES);
        final long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs >= 900, "process must honour the bounded wait");
        assertTrue(result.getTransformedMessages().isEmpty(), "timeout must resume with no queued input");
    }

    @Test
    void processResumesOnInterrupt() throws Exception {
        final var gate = new PauseGate(() -> {
        }, 60);
        gate.open();
        final var executor = Executors.newSingleThreadExecutor();
        try {
            final var future = executor.submit(() -> gate.process(null, MESSAGES, MESSAGES));
            Thread.sleep(200);
            assertFalse(future.isDone(), "process must be blocked before interrupt");
            future.cancel(true);
            // The interrupt breaks the latch wait; a follow-up task on the same
            // worker proves the paused call returned and freed the thread.
            final var done = new CountDownLatch(1);
            executor.submit(done::countDown);
            assertTrue(done.await(5, TimeUnit.SECONDS), "interrupted process must return");
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    void processReturnsImmediatelyWhenGateIsNotOpen() {
        final var gate = new PauseGate(() -> {
        });
        final var result = gate.process(null, MESSAGES, MESSAGES);
        assertTrue(result.getTransformedMessages().isEmpty(), "gate must not alter messages");
        assertTrue(result.getNewMessages().isEmpty(), "gate must not alter new messages");
    }

    @Test
    void readerStopReleasesOpenGate() throws Exception {
        final var printer = mockPrinter();
        final var gate = new PauseGate(() -> {
        }, 60);
        gate.open();
        final var reader = new MidRunInputReader(printer, new PendingMessagesQueue(), "session-1", gate);
        reader.start();
        final var executor = Executors.newSingleThreadExecutor();
        try {
            final Future<AgentMessagesPreProcessResult> future = executor.submit(
                                                                                 () -> gate.process(null,
                                                                                                    MESSAGES,
                                                                                                    MESSAGES));
            Thread.sleep(100);
            assertFalse(future.isDone(), "process must be blocked before stop");
            reader.stop();
            final var result = future.get(5, TimeUnit.SECONDS);
            assertEquals(0, result.getTransformedMessages().size(), "stop must release the gate");
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    void releasedGateIsANoOpOnLaterCalls() {
        final var gate = new PauseGate(() -> {
        }, 60);
        gate.open();
        gate.release();
        final var result = gate.process(null, MESSAGES, MESSAGES);
        assertTrue(result.getTransformedMessages().isEmpty(), "released gate must not block later calls");
    }
}
