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

import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * An {@link ExecutorService} that copies the SLF4J MDC context from the
 * submitting thread to each task before execution.
 *
 * <p>This ensures that the {@code sessionId} MDC key set on the main thread is
 * visible inside every worker thread spawned for that session, so logback's
 * {@code SiftingAppender} routes all session logs to the correct per-session
 * file.
 */
@RequiredArgsConstructor
public class MdcPropagatingExecutorService implements ExecutorService {

    private final ExecutorService delegate;

    private static void restore(final Map<String, String> previous) {
        if (previous != null) {
            MDC.setContextMap(previous);
        }
        else {
            MDC.clear();
        }
    }

    @Override
    public boolean awaitTermination(final long timeout, @NonNull final TimeUnit unit)
            throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public void execute(@NonNull final Runnable command) {
        delegate.execute(wrap(command));
    }

    @Override
    public <T> List<Future<T>> invokeAll(@NonNull final Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        return delegate.invokeAll(wrapAll(tasks));
    }

    @Override
    public <T> List<Future<T>> invokeAll(@NonNull final Collection<? extends Callable<T>> tasks,
                                         final long timeout,
                                         @NonNull final TimeUnit unit)
            throws InterruptedException {
        return delegate.invokeAll(wrapAll(tasks), timeout, unit);
    }

    @Override
    public <T> T invokeAny(@NonNull final Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return delegate.invokeAny(wrapAll(tasks));
    }

    @Override
    public <T> T invokeAny(@NonNull final Collection<? extends Callable<T>> tasks,
                           final long timeout,
                           @NonNull final TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(wrapAll(tasks), timeout, unit);
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

    @Override
    public <T> Future<T> submit(@NonNull final Callable<T> task) {
        return delegate.submit(wrap(task));
    }

    @Override
    public Future<?> submit(@NonNull final Runnable task) {
        return delegate.submit(wrap(task));
    }

    @Override
    public <T> Future<T> submit(@NonNull final Runnable task, final T result) {
        return delegate.submit(wrap(task), result);
    }

    private <T> Callable<T> wrap(final Callable<T> task) {
        final var context = MDC.getCopyOfContextMap();
        return () -> {
            final var previous = MDC.getCopyOfContextMap();
            if (context != null) {
                MDC.setContextMap(context);
            }
            try {
                return task.call();
            }
            finally {
                restore(previous);
            }
        };
    }

    private Runnable wrap(final Runnable task) {
        final var context = MDC.getCopyOfContextMap();
        return () -> {
            final var previous = MDC.getCopyOfContextMap();
            if (context != null) {
                MDC.setContextMap(context);
            }
            try {
                task.run();
            }
            finally {
                restore(previous);
            }
        };
    }

    private <T> Collection<Callable<T>> wrapAll(final Collection<? extends Callable<T>> tasks) {
        final var wrapped = new ArrayList<Callable<T>>(tasks.size());
        for (final var task : tasks) {
            wrapped.add(wrap(task));
        }
        return wrapped;
    }
}
