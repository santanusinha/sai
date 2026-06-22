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
package io.appform.sai;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import lombok.SneakyThrows;

class PrinterPaneLifetimeTest {

    private static class CapturingPrinter extends Printer {

        private volatile List<Printer.Update> captured = new CopyOnWriteArrayList<>();

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

        List<Printer.Update> getCapture() {
            return captured;
        }

        void resetCapture() {
            captured = new CopyOnWriteArrayList<>();
        }
    }

    private CapturingPrinter printer;

    private static void awaitQueueDrain() throws InterruptedException {
        Thread.sleep(200);
    }

    @Test
    void closeAfterStart() {
        printer.start();
        assertDoesNotThrow(() -> printer.close());
    }

    @Test
    void closeIsIdempotent() {
        printer.start();
        assertDoesNotThrow(() -> {
            printer.close();
            printer.close();
        });
    }

    @Test
    void closeWithoutStart() {
        assertDoesNotThrow(() -> printer.close());
    }

    @Test
    void headlessQueuesStatusUpdates() throws InterruptedException {
        printer.resetCapture();
        printer.print(Printer.statusUpdate("Processing…"));
        awaitQueueDrain();

        assertTrue(printer.getCapture().stream().anyMatch(Printer.Update::isStatusUpdate));
    }

    @Test
    void markIdleProducesStatusUpdate() throws InterruptedException {
        printer.resetCapture();
        printer.print(Printer.markIdleStatus());
        awaitQueueDrain();

        assertTrue(printer.getCapture().stream().anyMatch(Printer.Update::isStatusUpdate));
    }

    @BeforeEach
    @SneakyThrows
    void setUp() {
        printer = new CapturingPrinter();
    }

    @Test
    void startEnqueuesStatus() throws InterruptedException {
        printer.start();
        awaitQueueDrain();

        final var updates = printer.getCapture();
        assertTrue(updates.stream().anyMatch(Printer.Update::isStatusUpdate));
    }

    @Test
    void startStatusContainsIdle() throws InterruptedException {
        printer.start();
        awaitQueueDrain();

        final var hasIdleText = printer.getCapture().stream()
                .filter(Printer.Update::isStatusUpdate)
                .anyMatch(u -> u.getData() != null && u.getData().contains("Idle"));
        assertTrue(hasIdleText);
    }

    @Test
    void statusUpdateIsFlagged() throws InterruptedException {
        printer.resetCapture();
        printer.print(Printer.statusUpdate("Running…"));
        awaitQueueDrain();

        final var statusUpdates = printer.getCapture().stream()
                .filter(Printer.Update::isStatusUpdate)
                .toList();
        assertFalse(statusUpdates.isEmpty());
    }

    @Test
    void systemMessageNotFlaggedAsStatus() throws InterruptedException {
        printer.resetCapture();
        printer.print(Printer.systemMessage("Hello"));
        awaitQueueDrain();

        final var allNonStatus = printer.getCapture().stream()
                .noneMatch(Printer.Update::isStatusUpdate);
        assertTrue(allNonStatus);
    }

    @AfterEach
    void tearDown() throws IOException {
        printer.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateContextInfoSetsPersonaAndModelInContextInfo()
            throws InterruptedException, NoSuchFieldException, IllegalAccessException {
        printer.updateContextInfo("myPersona", "claude-opus-4");
        printer.print(Printer.markIdleStatus());
        awaitQueueDrain();

        final Field contextInfoField = Printer.class.getDeclaredField("contextInfo");
        contextInfoField.setAccessible(true);
        final var ref = (AtomicReference<String>) contextInfoField.get(printer);
        final var stored = ref.get();
        assertTrue(stored.contains("myPersona"), "contextInfo should contain persona name");
        assertTrue(stored.contains("claude-opus-4"), "contextInfo should contain model name");

        assertTrue(printer.getCapture().stream().anyMatch(Printer.Update::isStatusUpdate),
                   "markIdleStatus should enqueue a status update");
    }
}
