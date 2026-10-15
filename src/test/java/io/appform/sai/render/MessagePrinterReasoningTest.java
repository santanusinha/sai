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
package io.appform.sai.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.agentmessages.ReasoningContent;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.model.ModelUsageStats;

import io.appform.sai.term.Printer;

import org.junit.jupiter.api.Test;

import java.util.Objects;

/**
 * Unit tests for reasoning rendering in {@link MessagePrinter} on session restore
 * (historical mode). The upstream framework now persists reasoning content on
 * agent responses; the printer must render it dimmed, after the content message.
 */
class MessagePrinterReasoningTest {

    private static ReasoningContent reasoning(String text) {
        return ReasoningContent.builder()
                .content(text)
                .build();
    }

    @Test
    void liveModeNeverRendersReasoning() {
        final var printer = new MessagePrinter(null, new ObjectMapper(), false);

        final var text = Text.builder()
                .sessionId("session-1")
                .runId("run-1")
                .content("The answer is 42.")
                .stats(new ModelUsageStats())
                .elapsedTimeMs(1000L)
                .reasoning(reasoning("I need to compute the answer."))
                .build();

        final var updates = printer.visit(text);
        assertEquals(0, updates.size(), "live mode renders nothing for responses");
    }

    @Test
    void textResponseWithBlankReasoningRendersNoReasoningBlock() {
        final var printer = new MessagePrinter(null, new ObjectMapper(), true);

        final var text = Text.builder()
                .sessionId("session-1")
                .runId("run-1")
                .content("The answer is 42.")
                .stats(new ModelUsageStats())
                .elapsedTimeMs(1000L)
                .reasoning(reasoning(""))
                .build();

        final var updates = printer.visit(text);
        final var joined = updates.stream()
                .map(Printer.Update::getData)
                .filter(Objects::nonNull)
                .reduce("", (a, b) -> a + "\n" + b);

        assertFalse(joined.contains("Reasoning"), "blank reasoning renders nothing");
    }

    @Test
    void textResponseWithReasoningRendersReasoningAfterContent() {
        final var printer = new MessagePrinter(null, new ObjectMapper(), true);

        final var text = Text.builder()
                .sessionId("session-1")
                .runId("run-1")
                .content("The answer is 42.")
                .stats(new ModelUsageStats())
                .elapsedTimeMs(1000L)
                .reasoning(reasoning("I need to compute the answer."))
                .build();

        final var updates = printer.visit(text);
        final var joined = updates.stream()
                .map(Printer.Update::getData)
                .filter(Objects::nonNull)
                .reduce("", (a, b) -> a + "\n" + b);

        assertTrue(joined.contains("The answer is 42."), "content is rendered");
        assertTrue(joined.contains("I need to compute the answer."), "reasoning is rendered");
        assertTrue(joined.contains("Reasoning"), "reasoning header is rendered");

        final var contentIndex = joined.indexOf("The answer is 42.");
        final var reasoningIndex = joined.indexOf("I need to compute the answer.");
        assertTrue(contentIndex < reasoningIndex, "reasoning comes after the content");
    }

    @Test
    void textResponseWithoutReasoningRendersNoReasoningBlock() {
        final var printer = new MessagePrinter(null, new ObjectMapper(), true);

        final var text = Text.builder()
                .sessionId("session-1")
                .runId("run-1")
                .content("The answer is 42.")
                .stats(new ModelUsageStats())
                .elapsedTimeMs(1000L)
                .build();

        final var updates = printer.visit(text);
        final var joined = updates.stream()
                .map(Printer.Update::getData)
                .filter(Objects::nonNull)
                .reduce("", (a, b) -> a + "\n" + b);

        assertFalse(joined.contains("Reasoning"), "no reasoning header without reasoning");
        assertTrue(joined.contains("The answer is 42."), "content is still rendered");
    }

    @Test
    void toolCallWithReasoningRendersReasoningBeforeToolSummary() {
        final var printer = new MessagePrinter(null, new ObjectMapper(), true);

        final var toolCall = ToolCall.builder()
                .sessionId("session-1")
                .runId("run-1")
                .toolCallId("call-1")
                .toolName("core_tool_box_read_file")
                .arguments("{\"requestReason\":\"test\",\"filePath\":\"/tmp/x\",\"knownChecksum\":\"\"}")
                .reasoning(reasoning("I must read the file first."))
                .build();

        final var updates = printer.visit(toolCall);
        final var joined = updates.stream()
                .map(Printer.Update::getData)
                .filter(Objects::nonNull)
                .reduce("", (a, b) -> a + "\n" + b);

        assertTrue(joined.contains("I must read the file first."), "tool-call reasoning is rendered");
        assertTrue(joined.contains("Read: "), "tool summary is rendered");
        assertTrue(joined.contains("/tmp/x"), "tool target file is rendered");
    }
}
