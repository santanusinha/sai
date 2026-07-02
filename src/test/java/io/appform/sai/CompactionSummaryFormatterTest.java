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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.session.SessionSummary;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CompactionSummaryFormatter}.
 */
class CompactionSummaryFormatterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CompactionSummaryFormatter formatter = new CompactionSummaryFormatter(mapper);

    @Test
    void blankRawJsonFallsBackToSummaryText() {
        final var summary = SessionSummary.builder()
                .sessionId("test-blank-raw")
                .title("Blank raw")
                .summary("Fallback for blank raw.")
                .raw("")
                .updatedAt(System.currentTimeMillis())
                .build();

        final var result = formatter.format(summary);
        final var plain = result.replaceAll("\u001B\\[[0-9;]*m", "");

        assertTrue(plain.contains("Summary: Fallback for blank raw."), "fallback summary");
    }

    @Test
    void confidenceColouringAppearsForHighValue() {
        final var raw = """
                {
                  "title": "High confidence",
                  "summary": "Very confident.",
                  "confidence": 9.5
                }
                """;

        final var summary = SessionSummary.builder()
                .sessionId("test-confidence")
                .title("High confidence")
                .summary("Very confident.")
                .raw(raw)
                .updatedAt(System.currentTimeMillis())
                .build();

        final var result = formatter.format(summary);

        // Green ANSI code for >= 8
        assertTrue(result.contains(Printer.Colours.GREEN), "high confidence should be green");
        assertTrue(result.contains("9.5/10"), "confidence value");
    }

    @Test
    void emptyListFieldsAreSkipped() {
        final var raw = """
                {
                  "title": "Empty lists",
                  "summary": "Testing empty arrays.",
                  "keywords": [],
                  "key_points": [],
                  "action_items": [],
                  "citations": []
                }
                """;

        final var summary = SessionSummary.builder()
                .sessionId("test-empty-lists")
                .title("Empty lists")
                .summary("Testing empty arrays.")
                .raw(raw)
                .updatedAt(System.currentTimeMillis())
                .build();

        final var result = formatter.format(summary);
        final var plain = result.replaceAll("\u001B\\[[0-9;]*m", "");

        assertTrue(plain.contains("Title: Empty lists"), "title");
        assertTrue(plain.contains("Summary: Testing empty arrays."), "summary");
        assertFalse(plain.contains("Keywords:"), "empty keywords should be absent");
        assertFalse(plain.contains("Key points:"), "empty key points should be absent");
        assertFalse(plain.contains("Action items:"), "empty action items should be absent");
        assertFalse(plain.contains("Citations:"), "empty citations should be absent");
    }

    @Test
    void fullSchemaRendersAllFields() {
        final var raw = """
                {
                  "title": "Implement /compact command",
                  "summary": "Added a /compact slash command with pretty-printing.",
                  "keywords": ["compact", "ui-revamp", "slash"],
                  "key_points": ["Created CompactCommand", "Added CompactionSummaryFormatter"],
                  "key_facts": ["363 tests pass", "JLine 3.30.6"],
                  "action_items": ["Add tests", "Verify output"],
                  "goal": "Pretty-print compaction summary",
                  "discoveries": ["SessionSummary.raw is JSON"],
                  "accomplishments": ["Created formatter class"],
                  "relevant_files": ["CompactCommand.java", "CompactionSummaryFormatter.java"],
                  "citations": [
                    {"source": "CompactionPrompts.java", "quote": "DEFAULT_PROMPT_SCHEMA"},
                    {"source": "AgentSessionExtension.java", "quote": "forceCompaction"}
                  ],
                  "sentiment": "neutral",
                  "confidence": 9,
                  "metadata": "Session ID: abc-123"
                }
                """;

        final var summary = SessionSummary.builder()
                .sessionId("test-full")
                .title("Implement /compact command")
                .summary("Added a /compact slash command with pretty-printing.")
                .raw(raw)
                .updatedAt(System.currentTimeMillis())
                .build();

        final var result = formatter.format(summary);

        // Strip ANSI codes for assertions
        final var plain = result.replaceAll("\u001B\\[[0-9;]*m", "");

        assertTrue(plain.contains("Session compacted"), "header");
        assertTrue(plain.contains("Title: Implement /compact command"), "title");
        assertTrue(plain.contains("Summary: Added a /compact slash command"), "summary");
        assertTrue(plain.contains("Keywords:"), "keywords label");
        assertTrue(plain.contains("compact"), "keyword item");
        assertTrue(plain.contains("ui-revamp"), "keyword item");
        assertTrue(plain.contains("Key points:"), "key points label");
        assertTrue(plain.contains("Created CompactCommand"), "key point item");
        assertTrue(plain.contains("Key facts:"), "key facts label");
        assertTrue(plain.contains("363 tests pass"), "key fact item");
        assertTrue(plain.contains("Action items:"), "action items label");
        assertTrue(plain.contains("Add tests"), "action item");
        assertTrue(plain.contains("Goal: Pretty-print compaction summary"), "goal");
        assertTrue(plain.contains("Discoveries:"), "discoveries label");
        assertTrue(plain.contains("SessionSummary.raw is JSON"), "discovery item");
        assertTrue(plain.contains("Accomplishments:"), "accomplishments label");
        assertTrue(plain.contains("Created formatter class"), "accomplishment item");
        assertTrue(plain.contains("Relevant files:"), "relevant files label");
        assertTrue(plain.contains("CompactCommand.java"), "relevant file");
        assertTrue(plain.contains("Citations:"), "citations label");
        assertTrue(plain.contains("CompactionPrompts.java"), "citation source");
        assertTrue(plain.contains("DEFAULT_PROMPT_SCHEMA"), "citation quote");
        assertTrue(plain.contains("Sentiment: neutral"), "sentiment");
        assertTrue(plain.contains("Confidence:"), "confidence label");
        assertTrue(plain.contains("9.0/10"), "confidence value");
        assertTrue(plain.contains("Metadata: Session ID: abc-123"), "metadata");
    }

    @Test
    void malformedRawJsonFallsBackToSummaryText() {
        final var summary = SessionSummary.builder()
                .sessionId("test-malformed")
                .title("Malformed raw")
                .summary("Fallback for malformed raw.")
                .raw("{not valid json")
                .updatedAt(System.currentTimeMillis())
                .build();

        final var result = formatter.format(summary);
        final var plain = result.replaceAll("\u001B\\[[0-9;]*m", "");

        assertTrue(plain.contains("Summary: Fallback for malformed raw."), "fallback summary");
    }

    @Test
    void nullAndMissingFieldsAreSkipped() {
        // Only title and summary present; everything else null/missing
        final var raw = """
                {
                  "title": "Minimal compaction",
                  "summary": "Just the basics."
                }
                """;

        final var summary = SessionSummary.builder()
                .sessionId("test-minimal")
                .title("Minimal compaction")
                .summary("Just the basics.")
                .raw(raw)
                .updatedAt(System.currentTimeMillis())
                .build();

        final var result = formatter.format(summary);
        final var plain = result.replaceAll("\u001B\\[[0-9;]*m", "");

        assertTrue(plain.contains("Session compacted"), "header");
        assertTrue(plain.contains("Title: Minimal compaction"), "title");
        assertTrue(plain.contains("Summary: Just the basics."), "summary");

        // Ensure absent fields don't appear
        assertFalse(plain.contains("Keywords:"), "keywords should be absent");
        assertFalse(plain.contains("Key points:"), "key points should be absent");
        assertFalse(plain.contains("Key facts:"), "key facts should be absent");
        assertFalse(plain.contains("Action items:"), "action items should be absent");
        assertFalse(plain.contains("Goal:"), "goal should be absent");
        assertFalse(plain.contains("Discoveries:"), "discoveries should be absent");
        assertFalse(plain.contains("Accomplishments:"), "accomplishments should be absent");
        assertFalse(plain.contains("Relevant files:"), "relevant files should be absent");
        assertFalse(plain.contains("Citations:"), "citations should be absent");
        assertFalse(plain.contains("Sentiment:"), "sentiment should be absent");
        assertFalse(plain.contains("Confidence:"), "confidence should be absent");
        assertFalse(plain.contains("Metadata:"), "metadata should be absent");
    }

    @Test
    void nullRawJsonFallsBackToSummaryText() {
        final var summary = SessionSummary.builder()
                .sessionId("test-no-raw")
                .title("No raw JSON")
                .summary("Fallback summary text.")
                .raw(null)
                .updatedAt(System.currentTimeMillis())
                .build();

        final var result = formatter.format(summary);
        final var plain = result.replaceAll("\u001B\\[[0-9;]*m", "");

        assertTrue(plain.contains("Session compacted"), "header");
        assertTrue(plain.contains("Title: No raw JSON"), "title");
        assertTrue(plain.contains("Summary: Fallback summary text."), "fallback summary");

        // Rich fields absent
        assertFalse(plain.contains("Keywords:"), "keywords should be absent");
        assertFalse(plain.contains("Key points:"), "key points should be absent");
    }

    @Test
    void nullTitleIsSkipped() {
        final var raw = """
                {
                  "summary": "Summary without title."
                }
                """;

        final var summary = SessionSummary.builder()
                .sessionId("test-no-title")
                .title(null)
                .summary("Summary without title.")
                .raw(raw)
                .updatedAt(System.currentTimeMillis())
                .build();

        final var result = formatter.format(summary);
        final var plain = result.replaceAll("\u001B\\[[0-9;]*m", "");

        assertTrue(plain.contains("Session compacted"), "header");
        assertFalse(plain.contains("Title:"), "title should be absent");
        assertTrue(plain.contains("Summary: Summary without title."), "summary");
    }

    @Test
    void nullValuesInCitationsAreSkipped() {
        final var raw = """
                {
                  "title": "Null citations",
                  "summary": "Testing null citation entries.",
                  "citations": [
                    null,
                    {"source": "valid-source", "quote": "valid-quote"},
                    {"source": "", "quote": ""},
                    null
                  ]
                }
                """;

        final var summary = SessionSummary.builder()
                .sessionId("test-null-citations")
                .title("Null citations")
                .summary("Testing null citation entries.")
                .raw(raw)
                .updatedAt(System.currentTimeMillis())
                .build();

        final var result = formatter.format(summary);
        final var plain = result.replaceAll("\u001B\\[[0-9;]*m", "");

        assertTrue(plain.contains("Citations:"), "citations label");
        assertTrue(plain.contains("valid-source"), "valid citation source");
        assertTrue(plain.contains("valid-quote"), "valid citation quote");
        // Should only have one citation entry despite 4 array elements
        final var bulletCount = plain.lines().filter(l -> l.contains("•")).count();
        assertTrue(bulletCount == 1, "only one valid citation should render, got " + bulletCount);
    }
}
