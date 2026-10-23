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

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * An {@link ExecutorService} that can abort every task it has started.
 *
 * <p>{@link java.util.concurrent.CompletableFuture#cancel(boolean)} never interrupts the thread that runs
 * the work. Agent runs use this executor so that one call to {@link #cancelAll()} does three things:
 * <ol>
 * <li>closes the gate, so retries and follow-up tasks that the framework submits are rejected;</li>
 * <li>interrupts every thread that is running a task;</li>
 * <li>starts tasks that were queued before the cancel with the interrupt flag set, so they stop at once.</li>
 * </ol>
 * Call {@link #beginRun()} before the next run to open the gate again.
 *
 * <p>Only agent work must use this executor. Long-lived tasks such as the printer loop must stay on the
 * shared executor, because {@link #cancelAll()} would interrupt them.
 */
@Slf4j
@RequiredArgsConstructor
public class CancellableExecutorService extends AbstractExecutorService {

    private final ExecutorService delegate;
    private final Object lock = new Object();
    private final Set<Thread> running = new HashSet<>();
    private boolean cancelled;

    /**
     * Waits until no task is running.
     *
     * @param maxWait the longest time to wait
     * @return {@code true} when all tasks have ended
     */
    public boolean awaitIdle(final Duration maxWait) throws InterruptedException {
        final var deadline = System.nanoTime() + maxWait.toNanos();
        synchronized (lock) {
            while (!running.isEmpty()) {
                final var remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    log.warn("{} task(s) still running after cancel", running.size());
                    return false;
                }
                TimeUnit.NANOSECONDS.timedWait(lock, remaining);
            }
            return true;
        }
    }

    @Override
    public boolean awaitTermination(final long timeout, @NonNull final TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    /**
     * Opens the gate for a new run.
     */
    public void beginRun() {
        synchronized (lock) {
            cancelled = false;
        }
    }

    /**
     * Rejects new tasks and interrupts all running tasks.
     */
    public void cancelAll() {
        synchronized (lock) {
            cancelled = true;
            log.info("Cancelling {} running task(s)", running.size());
            running.forEach(Thread::interrupt);
        }
    }

    @Override
    public void execute(@NonNull final Runnable command) {
        synchronized (lock) {
            if (cancelled) {
                throw new RejectedExecutionException("Execution was cancelled");
            }
        }
        delegate.execute(() -> {
            final var thread = Thread.currentThread();
            synchronized (lock) {
                running.add(thread);
                if (cancelled) {
                    // Never drop the task: its future would not complete and a caller could wait forever.
                    // Run it with the interrupt flag set so that it stops at its first blocking call.
                    thread.interrupt();
                }
            }
            try {
                command.run();
            }
            finally {
                synchronized (lock) {
                    running.remove(thread);
                    lock.notifyAll();
                }
                // Clear a late interrupt so it does not leak into the next task on this pooled thread.
                Thread.interrupted();
            }
        });
    }

    /**
     * @return {@code true} between {@link #cancelAll()} and the next {@link #beginRun()}
     */
    public boolean isCancelled() {
        synchronized (lock) {
            return cancelled;
        }
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }
}
