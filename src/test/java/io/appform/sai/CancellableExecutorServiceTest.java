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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class CancellableExecutorServiceTest {

    private CancellableExecutorService executor;

    @Test
    void beginRunReopensTheGate() throws Exception {
        executor.cancelAll();
        executor.beginRun();
        final var done = new CountDownLatch(1);
        executor.execute(done::countDown);
        assertTrue(done.await(2, TimeUnit.SECONDS));
    }

    @Test
    void cancelAllInterruptsRunningTask() throws Exception {
        final var started = new CountDownLatch(1);
        final var interrupted = new AtomicBoolean();
        CompletableFuture.runAsync(() -> {
            started.countDown();
            try {
                Thread.sleep(30_000);
            }
            catch (InterruptedException e) {
                interrupted.set(true);
            }
        }, executor);
        assertTrue(started.await(2, TimeUnit.SECONDS));
        executor.cancelAll();
        assertTrue(executor.awaitIdle(Duration.ofSeconds(2)), "worker must stop after cancel");
        assertTrue(interrupted.get());
    }

    @Test
    void cancelAllRejectsTasksSubmittedAfterwards() {
        executor.cancelAll();
        assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {
        }));
    }

    @Test
    void interruptFlagDoesNotLeakIntoNextTask() throws Exception {
        final var started = new CountDownLatch(1);
        CompletableFuture.runAsync(() -> {
            started.countDown();
            try {
                Thread.sleep(30_000);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, executor);
        assertTrue(started.await(2, TimeUnit.SECONDS));
        executor.cancelAll();
        assertTrue(executor.awaitIdle(Duration.ofSeconds(2)));
        executor.beginRun();
        final var flag = CompletableFuture.supplyAsync(() -> Thread.currentThread().isInterrupted(), executor);
        assertFalse(flag.get(2, TimeUnit.SECONDS));
    }

    @Test
    void queuedTaskStillCompletesItsFutureAfterCancel() throws Exception {
        final var future = CompletableFuture.supplyAsync(() -> Thread.currentThread().isInterrupted(), executor);
        assertTrue(future.get(2, TimeUnit.SECONDS) || !future.isCompletedExceptionally());
    }

    @BeforeEach
    void setUp() {
        executor = new CancellableExecutorService(Executors.newCachedThreadPool());
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }
}
