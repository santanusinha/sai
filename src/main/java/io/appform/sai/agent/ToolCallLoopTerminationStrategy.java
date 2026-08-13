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
package io.appform.sai.agent;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategy;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategyResponse;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelSettings;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link EarlyTerminationStrategy} that stops a model run when the model repeats the same
 * tool call in a row.
 *
 * <p>Some models repeat the same tool call (same tool name and same arguments) without
 * making progress. The framework loop in the model layer has no iteration cap, so the run
 * continues until the user stops it. This strategy terminates the run with
 * {@link ErrorType#MODEL_RUN_TERMINATED} when the same tool call appears at least
 * {@code maxIdenticalToolCalls} times in a row in the current run.
 *
 * <p>Detection counts consecutive {@link ToolCall} messages in
 * {@link ModelOutput#getNewMessages()} and keeps per-run state in an internal map. The
 * count resets when a different tool call appears. Entries are removed when a new run id
 * is seen, so the map does not grow without bounds.
 *
 * <p>Thread safety: the map is a {@link ConcurrentHashMap}, so parallel runs do not
 * corrupt the state. The eviction of stale entries is not atomic. Two runs that
 * interleave on one strategy instance can evict each other's state. The count then
 * resets, and the guard fires later than configured. This is safe for sai, which runs
 * one agent loop at a time per session.
 */
@Slf4j
public class ToolCallLoopTerminationStrategy implements EarlyTerminationStrategy {

    /**
     * Default maximum number of times the same tool call may repeat in a row in one run.
     */
    public static final int DEFAULT_MAX_IDENTICAL_TOOL_CALLS = 3;

    /**
     * Mutable per-run state: the last tool call seen and its consecutive repeat count.
     */
    private static final class ConsecutiveCallState {
        private String lastKey;
        private int count;
    }

    private final int maxIdenticalToolCalls;

    /**
     * Per-run state of the last tool call and its consecutive repeat count, keyed by run id.
     */
    private final Map<String, ConsecutiveCallState> stateByRun = new ConcurrentHashMap<>();

    public ToolCallLoopTerminationStrategy() {
        this(DEFAULT_MAX_IDENTICAL_TOOL_CALLS);
    }

    public ToolCallLoopTerminationStrategy(final int maxIdenticalToolCalls) {
        if (maxIdenticalToolCalls < 1) {
            throw new IllegalArgumentException("maxIdenticalToolCalls must be a positive integer");
        }
        this.maxIdenticalToolCalls = maxIdenticalToolCalls;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ToolCallLoopTerminationStrategy other)) {
            return false;
        }
        return maxIdenticalToolCalls == other.maxIdenticalToolCalls;
    }

    @Override
    public EarlyTerminationStrategyResponse evaluate(final ModelSettings modelSettings,
                                                     final ModelRunContext modelRunContext,
                                                     final ModelOutput output) {
        return checkIdenticalToolCalls(modelRunContext.getRunId(),
                                       output == null ? List.of() : output.getNewMessages());
    }

    private EarlyTerminationStrategyResponse checkIdenticalToolCalls(final String runId,
                                                                     final List<AgentMessage> newMessages) {
        final var state = stateForRun(runId);
        for (final var message : newMessages) {
            if (message instanceof ToolCall toolCall) {
                final var key = toolCall.getToolName() + "\n" + toolCall.getArguments();
                final var count = key.equals(state.lastKey) ? state.count + 1 : 1;
                state.lastKey = key;
                state.count = count;
                if (count >= maxIdenticalToolCalls) {
                    return terminate("Tool '%s' was called %d times in a row with identical arguments in run %s"
                            .formatted(toolCall.getToolName(), count, runId));
                }
            }
        }
        return EarlyTerminationStrategyResponse.doNotTerminate();
    }

    private ConsecutiveCallState stateForRun(final String runId) {
        if (!stateByRun.containsKey(runId)) {
            // New run id: drop stale entries so the map stays bounded
            stateByRun.keySet().retainAll(List.of(runId));
        }
        return stateByRun.computeIfAbsent(runId, key -> new ConsecutiveCallState());
    }

    private EarlyTerminationStrategyResponse terminate(final String reason) {
        log.warn("Terminating model run early: {}", reason);
        return EarlyTerminationStrategyResponse.terminate(ErrorType.MODEL_RUN_TERMINATED,
                                                          reason);
    }
}
