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

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessContext;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessResult;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessor;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

/**
 * A pre-processor that pauses an agent run at a model-call boundary until the
 * user submits or cancels the mid-run input prompt.
 *
 * <p>The gate is opened by the mid-run input reader when the user presses
 * Ctrl-Q or Esc. The next model call then blocks in {@link #process} on a
 * latch until one of these happens:
 *
 * <ul>
 * <li>The user submits or cancels the prompt ({@link #release()}) — the run
 * proceeds, and a submitted line is injected by the
 * {@code PendingMessageInjector} registered after this gate.</li>
 * <li>The user cancels the run with Ctrl-C — the run thread is interrupted
 * and the latch wait ends.</li>
 * <li>The bounded wait times out — the run proceeds with no queued input, so
 * a forgotten pause cannot hang the run forever.</li>
 * </ul>
 *
 * <p>Register this gate BEFORE the pending-message injector on the agent so the
 * queued line is drained by the very same model call that was paused.
 */
@Slf4j
public class PauseGate implements AgentMessagesPreProcessor {

    private static final long DEFAULT_TIMEOUT_MINUTES = 10;

    private final CountDownLatch latch = new CountDownLatch(1);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final long timeoutSeconds;
    private final Runnable onPause;

    public PauseGate(final Runnable onPause) {
        this(onPause, DEFAULT_TIMEOUT_MINUTES * 60);
    }

    public PauseGate(final Runnable onPause, final long timeoutSeconds) {
        this.onPause = onPause;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Opens the gate so the next model call pauses. Safe to call when the gate
     * is already open.
     */
    public void open() {
        if (paused.compareAndSet(false, true)) {
            onPause.run();
        }
        // No-op when already open: one gate opening serves one prompt.
    }

    /**
     * {@inheritDoc}
     *
     * <p>Blocks only when the gate is open; a gate that was never opened is a
     * no-op so a fresh gate can never stall a run.
     */
    @Override
    public AgentMessagesPreProcessResult process(final AgentMessagesPreProcessContext ctx,
                                                 final List<AgentMessage> allMessages,
                                                 final List<AgentMessage> newMessages) {
        if (!paused.get()) {
            return new AgentMessagesPreProcessResult(allMessages, newMessages);
        }
        log.info("Run paused for mid-run input (timeout {} s)", timeoutSeconds);
        try {
            final var released = latch.await(timeoutSeconds, TimeUnit.SECONDS);
            if (!released) {
                log.warn("Pause timed out after {} s; resuming with no queued input", timeoutSeconds);
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Pause interrupted; resuming");
        }
        return new AgentMessagesPreProcessResult(allMessages, newMessages);
    }

    /**
     * Releases the gate so a paused model call proceeds. Safe to call when the
     * gate is not open.
     */
    public void release() {
        paused.set(false);
        latch.countDown();
    }
}
