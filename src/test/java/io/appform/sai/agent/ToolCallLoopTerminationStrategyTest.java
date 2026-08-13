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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategyResponse;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelUsageStats;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Unit tests for {@link ToolCallLoopTerminationStrategy}.
 */
class ToolCallLoopTerminationStrategyTest {

    private static final String RUN_ID = "run-1";

    private final ToolCallLoopTerminationStrategy strategy = new ToolCallLoopTerminationStrategy();

    private static ModelRunContext context(final ModelUsageStats stats) {
        return context(stats, RUN_ID);
    }

    private static ModelRunContext context(final ModelUsageStats stats, final String runId) {
        return new ModelRunContext("test-agent",
                                   runId,
                                   "session-1",
                                   "user-1",
                                   null,
                                   stats,
                                   null);
    }

    private static ToolCall toolCall(final String toolName, final String arguments) {
        return new ToolCall("session-1", RUN_ID, "call-1", toolName, arguments);
    }

    @Test
    void continuesBelowIdenticalCallThreshold() {
        final var stats = new ModelUsageStats();

        assertEquals(EarlyTerminationStrategyResponse.ResponseType.CONTINUE,
                     evaluate(List.of(toolCall("bash", "{}")), stats).getResponseType());
        assertEquals(EarlyTerminationStrategyResponse.ResponseType.CONTINUE,
                     evaluate(List.of(toolCall("bash", "{}")), stats).getResponseType());
    }

    @Test
    void continuesWhenNoToolCallsPresent() {
        final var response = evaluate(List.of(), new ModelUsageStats());

        assertEquals(EarlyTerminationStrategyResponse.ResponseType.CONTINUE, response.getResponseType());
    }

    @Test
    void countsAreIsolatedPerRun() {
        final var stats = new ModelUsageStats();
        evaluate(List.of(toolCall("bash", "{}")), stats, "run-a");
        evaluate(List.of(toolCall("bash", "{}")), stats, "run-a");

        // Same call in a different run must start from zero
        final var response = evaluate(List.of(toolCall("bash", "{}")), stats, "run-b");

        assertEquals(EarlyTerminationStrategyResponse.ResponseType.CONTINUE, response.getResponseType());
    }

    @Test
    void differentArgumentsResetTheConsecutiveCount() {
        final var stats = new ModelUsageStats();

        assertEquals(EarlyTerminationStrategyResponse.ResponseType.CONTINUE,
                     evaluate(List.of(toolCall("bash", "{\"command\":\"grep a\"}")), stats).getResponseType());
        assertEquals(EarlyTerminationStrategyResponse.ResponseType.CONTINUE,
                     evaluate(List.of(toolCall("bash", "{\"command\":\"grep b\"}")), stats).getResponseType());
        assertEquals(EarlyTerminationStrategyResponse.ResponseType.CONTINUE,
                     evaluate(List.of(toolCall("bash", "{\"command\":\"grep c\"}")), stats).getResponseType());
    }

    @Test
    void differentToolNamesResetTheConsecutiveCount() {
        final var stats = new ModelUsageStats();

        assertEquals(EarlyTerminationStrategyResponse.ResponseType.CONTINUE,
                     evaluate(List.of(toolCall("bash", "{}")), stats).getResponseType());
        assertEquals(EarlyTerminationStrategyResponse.ResponseType.CONTINUE,
                     evaluate(List.of(toolCall("read_file", "{}")), stats).getResponseType());
    }

    @Test
    void interleavedIdenticalCallsDoNotTerminate() {
        final var stats = new ModelUsageStats();

        // Two identical calls, a different call, then the same call again: never 3 in a row
        assertEquals(EarlyTerminationStrategyResponse.ResponseType.CONTINUE,
                     evaluate(List.of(toolCall("bash", "{}")), stats).getResponseType());
        assertEquals(EarlyTerminationStrategyResponse.ResponseType.CONTINUE,
                     evaluate(List.of(toolCall("bash", "{}")), stats).getResponseType());
        assertEquals(EarlyTerminationStrategyResponse.ResponseType.CONTINUE,
                     evaluate(List.of(toolCall("bash", "{\"command\":\"other\"}")), stats).getResponseType());
        assertEquals(EarlyTerminationStrategyResponse.ResponseType.CONTINUE,
                     evaluate(List.of(toolCall("bash", "{}")), stats).getResponseType());
        assertEquals(EarlyTerminationStrategyResponse.ResponseType.CONTINUE,
                     evaluate(List.of(toolCall("bash", "{}")), stats).getResponseType());
    }

    @Test
    void nullOutputIsHandled() {
        final var response = strategy.evaluate(null, context(new ModelUsageStats()), null);

        assertEquals(EarlyTerminationStrategyResponse.ResponseType.CONTINUE, response.getResponseType());
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class, () -> new ToolCallLoopTerminationStrategy(0));
        assertThrows(IllegalArgumentException.class, () -> new ToolCallLoopTerminationStrategy(-1));
    }

    @Test
    void terminatesOnThirdConsecutiveIdenticalToolCall() {
        final var stats = new ModelUsageStats();
        evaluate(List.of(toolCall("bash", "{\"command\":\"grep foo\"}")), stats);
        evaluate(List.of(toolCall("bash", "{\"command\":\"grep foo\"}")), stats);

        final var response = evaluate(List.of(toolCall("bash", "{\"command\":\"grep foo\"}")), stats);

        assertEquals(EarlyTerminationStrategyResponse.ResponseType.TERMINATE, response.getResponseType());
        assertEquals(ErrorType.MODEL_RUN_TERMINATED, response.getErrorType());
    }

    private EarlyTerminationStrategyResponse evaluate(final List<AgentMessage> newMessages,
                                                      final ModelUsageStats stats) {
        return evaluate(newMessages, stats, RUN_ID);
    }

    private EarlyTerminationStrategyResponse evaluate(final List<AgentMessage> newMessages,
                                                      final ModelUsageStats stats,
                                                      final String runId) {
        final var output = new ModelOutput(null,
                                           newMessages,
                                           newMessages,
                                           stats,
                                           null);
        return strategy.evaluate(null, context(stats, runId), output);
    }
}
