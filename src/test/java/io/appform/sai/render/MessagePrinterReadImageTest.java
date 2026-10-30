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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;

import io.appform.sai.term.Printer;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the compact read_image rendering in {@link MessagePrinter}.
 * The tool call prints one summary line and the response prints the file path
 * and size. Bulk data is never printed.
 */
class MessagePrinterReadImageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String renderToolCall(String arguments) {
        final var printer = new MessagePrinter(null, MAPPER, false);
        final var toolCall = ToolCall.builder()
                .sessionId("session-1")
                .runId("run-1")
                .toolCallId("call-1")
                .toolName("core_tool_box_read_image")
                .arguments(arguments)
                .build();
        return printer.visit(toolCall)
                .stream()
                .map(Printer.Update::getData)
                .filter(java.util.Objects::nonNull)
                .reduce("", (a, b) -> a + b);
    }

    private static String renderToolResponse(String jsonResponse) {
        final var printer = new MessagePrinter(null, MAPPER, false);
        final var toolResponse = com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse.builder()
                .sessionId("session-1")
                .runId("run-1")
                .toolCallId("call-1")
                .toolName("core_tool_box_read_image")
                .errorType(ErrorType.SUCCESS)
                .response(jsonResponse)
                .build();
        return printer.visit(toolResponse)
                .stream()
                .map(Printer.Update::getData)
                .filter(java.util.Objects::nonNull)
                .reduce("", (a, b) -> a + b);
    }

    @Test
    void toolCallPrintsOneLineSummaryWithFilePathAndDetail() {
        final var rendered = renderToolCall("{\"filePath\":\"/tmp/a.png\",\"imageDetail\":\"HIGH\"}");
        assertTrue(rendered.contains("/tmp/a.png"), "summary should contain the file path");
        assertTrue(rendered.contains("HIGH"), "summary should contain the detail level");
        assertTrue(rendered.contains("Read image"), "summary should name the action");
    }

    @Test
    void toolResponseErrorIsSurfaced() {
        final var rendered = renderToolResponse("{\"attached\":false,\"filePath\":\"/tmp/a.png\","
                + "\"format\":\"png\",\"imageDetail\":\"AUTO\",\"bytes\":0,\"error\":\"file too large\"}");
        assertTrue(rendered.contains("Error reading image"), "error should be surfaced");
        assertTrue(rendered.contains("file too large"), "error text should be surfaced");
    }

    @Test
    void toolResponsePrintsFilePathAndSizeNotPayload() {
        final var rendered = renderToolResponse("{\"attached\":true,\"filePath\":\"/tmp/a.png\","
                + "\"format\":\"png\",\"imageDetail\":\"AUTO\",\"bytes\":2048,\"error\":null}");
        assertTrue(rendered.contains("/tmp/a.png"), "summary should contain the file path");
        assertTrue(rendered.contains("2.0 KB"), "summary should contain the size");
        assertFalse(rendered.contains("2048,"), "raw response JSON should not be printed");
    }
}
