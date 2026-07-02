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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.compaction.ExtractedSummary;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.events.CompactionCompletedEvent;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EventPrinter}, focusing on the {@link CompactionCompletedEvent}
 * visit method that now delegates to {@link CompactionSummaryFormatter}.
 */
class EventPrinterTest {

    @Test
    void compactionErrorPrintsErrorMessage() {
        final var printer = mock(Printer.class);
        final var mapper = new ObjectMapper();
        final var eventPrinter = new EventPrinter(printer, mapper);

        final var event = CompactionCompletedEvent.builder()
                .agentName("test-agent")
                .runId("run-1")
                .sessionId("session-1")
                .userId("user-1")
                .errorType(ErrorType.GENERIC_MODEL_CALL_FAILURE)
                .errorMessage("Model timed out")
                .elapsedTimeMs(1000L)
                .extractedSummary(null)
                .build();

        eventPrinter.visit(event);

        final var captor = org.mockito.ArgumentCaptor.forClass(Printer.Update.class);
        verify(printer, times(1)).print(captor.capture());
        final var data = captor.getValue().getData();
        assertTrue(data.contains("Compaction completed with errors"), "error message");
        assertTrue(data.contains("Model timed out"), "error detail");
    }

    @Test
    @SuppressWarnings("unchecked")
    void compactionStartedPrintsSystemMessage() {
        final var printer = mock(Printer.class);
        final var mapper = new ObjectMapper();
        final var eventPrinter = new EventPrinter(printer, mapper);

        final var startedEvent = com.phonepe.sentinelai.core.events.CompactionStartedEvent.builder()
                .agentName("test-agent")
                .runId("run-1")
                .sessionId("session-1")
                .userId("user-1")
                .build();

        eventPrinter.visit(startedEvent);

        // CompactionStartedEvent handler prints a systemMessage
        verify(printer, times(1)).print(any(Printer.Update.class));
    }

    @Test
    void compactionSuccessWithExtractedSummaryFallbackNoRawData() {
        final var printer = mock(Printer.class);
        final var mapper = new ObjectMapper();
        final var eventPrinter = new EventPrinter(printer, mapper);

        final var extracted = ExtractedSummary.builder()
                .title("Fallback title")
                .summary("Plain fallback summary text.")
                .rawData(null)
                .build();

        final var event = CompactionCompletedEvent.builder()
                .agentName("test-agent")
                .runId("run-1")
                .sessionId("session-1")
                .userId("user-1")
                .errorType(ErrorType.SUCCESS)
                .elapsedTimeMs(200L)
                .extractedSummary(extracted)
                .build();

        eventPrinter.visit(event);

        final var captor = org.mockito.ArgumentCaptor.forClass(Printer.Update.class);
        verify(printer, times(1)).print(captor.capture());
        final var update = captor.getValue();
        assertTrue(update.isRaw(), "update should be raw");
        final var plain = update.getData().replaceAll("\\u001B\\[[0-9;]*m", "");
        assertTrue(plain.contains("Session compacted"), "header");
        assertTrue(plain.contains("Title: Fallback title"), "title");
        assertTrue(plain.contains("Summary: Plain fallback summary text."), "fallback summary");
    }

    @Test
    void compactionSuccessWithExtractedSummaryPrettyPrintsFormattedOutput() {
        final var printer = mock(Printer.class);
        final var mapper = new ObjectMapper();
        final var eventPrinter = new EventPrinter(printer, mapper);

        final var rawNode = mapper.valueToTree(java.util.Map.of(
                                                                "title",
                                                                "Auto compaction",
                                                                "summary",
                                                                "Auto-compacted by sentinel.",
                                                                "keywords",
                                                                java.util.List.of("auto", "compaction"),
                                                                "confidence",
                                                                8.5,
                                                                "sentiment",
                                                                "positive"));
        final var extracted = ExtractedSummary.builder()
                .title("Auto compaction")
                .summary("Fallback text.")
                .rawData(rawNode)
                .build();

        final var event = CompactionCompletedEvent.builder()
                .agentName("test-agent")
                .runId("run-1")
                .sessionId("session-1")
                .userId("user-1")
                .errorType(ErrorType.SUCCESS)
                .elapsedTimeMs(500L)
                .extractedSummary(extracted)
                .build();

        eventPrinter.visit(event);

        // Verify a single print call with the formatted raw Update
        final var captor = org.mockito.ArgumentCaptor.forClass(Printer.Update.class);
        verify(printer, times(1)).print(captor.capture());
        final var update = captor.getValue();
        assertTrue(update.isRaw(), "update should be raw");
        final var plain = update.getData().replaceAll("\\u001B\\[[0-9;]*m", "");
        assertTrue(plain.contains("Session compacted"), "header");
        assertTrue(plain.contains("Title: Auto compaction"), "title");
        assertTrue(plain.contains("Summary: Auto-compacted by sentinel."), "summary");
        assertTrue(plain.contains("Keywords:"), "keywords label");
        assertTrue(plain.contains("Sentiment: positive"), "sentiment");
        assertTrue(plain.contains("8.5/10"), "confidence value");
    }

    @Test
    void compactionSuccessWithNullExtractedSummaryPrintsPlainMessage() {
        final var printer = mock(Printer.class);
        final var mapper = new ObjectMapper();
        final var eventPrinter = new EventPrinter(printer, mapper);

        final var event = CompactionCompletedEvent.builder()
                .agentName("test-agent")
                .runId("run-1")
                .sessionId("session-1")
                .userId("user-1")
                .errorType(ErrorType.SUCCESS)
                .elapsedTimeMs(500L)
                .extractedSummary(null)
                .build();

        eventPrinter.visit(event);

        final var captor = org.mockito.ArgumentCaptor.forClass(Printer.Update.class);
        verify(printer, times(1)).print(captor.capture());
        final var data = captor.getValue().getData();
        assertTrue(data.contains("Compaction completed successfully"), "plain success message");
        assertTrue(data.contains("500"), "elapsed time");
    }
}
