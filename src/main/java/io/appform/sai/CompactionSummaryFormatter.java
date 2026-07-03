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

import com.fasterxml.jackson.databind.JsonNode;
import com.phonepe.sentinelai.core.compaction.ExtractedSummary;

import lombok.extern.slf4j.Slf4j;

/**
 * Formats an {@link ExtractedSummary} into a human-readable,
 * ANSI-coloured multi-line string for display in the interactive terminal.
 *
 * <p>The raw JSON field (accessible via {@link ExtractedSummary#getRawData()} as a
 * {@link JsonNode}) is produced by the sentinel-ai compaction pipeline. Its schema
 * (defined in {@code CompactionPrompts.DEFAULT_PROMPT_SCHEMA}) includes: {@code title},
 * {@code summary}, {@code keywords}, {@code key_points}, {@code key_facts},
 * {@code action_items}, {@code goal}, {@code discoveries}, {@code accomplishments},
 * {@code relevant_files}, {@code citations}, {@code sentiment}, {@code confidence},
 * and {@code metadata}.
 *
 * <p>This class parses the raw JSON and renders each present field as a labelled section,
 * gracefully skipping {@code null} or missing fields. When the raw JSON is absent or unparseable
 * it falls back to the plain summary text.
 */
@Slf4j
public final class CompactionSummaryFormatter {

    private static final String LABEL_COLOUR = Printer.Colours.YELLOW;
    private static final String TEXT_COLOUR = Printer.Colours.WHITE;
    private static final String DIM_COLOUR = Printer.Colours.GRAY;
    private static final String RESET = Printer.Colours.RESET;
    private static final String BOLD = "\u001B[1m";

    private static void appendCitations(StringBuilder sb, JsonNode raw) {
        final var node = raw.get("citations");
        if (node == null || node.isNull() || !node.isArray() || node.isEmpty()) {
            return;
        }
        sb.append("\n").append(LABEL_COLOUR).append("Citations:").append(RESET);
        for (int i = 0; i < node.size(); i++) {
            final var citation = node.get(i);
            if (citation == null || citation.isNull()) {
                continue;
            }
            final var source = text(citation, "source");
            final var quote = text(citation, "quote");
            if (source.isEmpty() && quote.isEmpty()) {
                continue;
            }
            sb.append("\n  ").append(DIM_COLOUR).append("\u2022 ").append(RESET);
            if (!source.isEmpty()) {
                sb.append(TEXT_COLOUR).append(source).append(RESET);
            }
            if (!quote.isEmpty()) {
                sb.append(DIM_COLOUR).append(" \u2014 \"").append(quote).append("\"").append(RESET);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Field renderers
    // -------------------------------------------------------------------------

    private static void appendConfidence(StringBuilder sb, JsonNode raw) {
        final var node = raw.get("confidence");
        if (node == null || node.isNull()) {
            return;
        }
        final var confidence = node.asDouble();
        sb.append("\n").append(LABEL_COLOUR).append("Confidence: ").append(RESET)
                .append(confidenceColour(confidence))
                .append(String.format("%.1f", confidence)).append("/10").append(RESET);
    }

    private static void appendList(StringBuilder sb, String label, JsonNode raw, String field) {
        final var node = raw.get(field);
        if (node == null || node.isNull() || !node.isArray() || node.isEmpty()) {
            return;
        }
        sb.append("\n").append(LABEL_COLOUR).append(label).append(":").append(RESET);
        for (int i = 0; i < node.size(); i++) {
            final var item = node.get(i);
            if (item != null && !item.isNull() && !item.asText().isBlank()) {
                sb.append("\n  ").append(DIM_COLOUR).append("\u2022 ").append(RESET)
                        .append(TEXT_COLOUR).append(item.asText()).append(RESET);
            }
        }
    }

    private static void appendText(StringBuilder sb, String label, String value) {
        if (value.isEmpty()) {
            return;
        }
        sb.append("\n").append(LABEL_COLOUR).append(label).append(": ").append(RESET)
                .append(TEXT_COLOUR).append(value).append(RESET);
    }

    private static String confidenceColour(double confidence) {
        if (confidence >= 8) {
            return Printer.Colours.GREEN;
        }
        if (confidence >= 5) {
            return Printer.Colours.YELLOW;
        }
        return Printer.Colours.RED;
    }

    private static String text(JsonNode parent, String field) {
        if (parent == null) {
            return "";
        }
        final var node = parent.get(field);
        if (node == null || node.isNull()) {
            return "";
        }
        final var value = node.asText();
        return value == null || value.isBlank() ? "" : value;
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "" : value;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String textOr(JsonNode raw, String field, String fallback) {
        final var fromRaw = text(raw, field);
        return fromRaw.isEmpty() ? text(fallback) : fromRaw;
    }

    /**
     * Format the compaction result from a {@code CompactionCompletedEvent}'s
     * {@link ExtractedSummary} into a coloured, multi-line string.
     *
     * @param extractedSummary the summary extracted by the compaction pipeline
     * @return a human-readable ANSI-coloured string, never {@code null}
     */
    public String format(final ExtractedSummary extractedSummary) {
        final var sb = new StringBuilder();

        // Header
        sb.append(LABEL_COLOUR).append(BOLD).append("\u2705 Session compacted").append(RESET);

        // Title
        final var title = text(extractedSummary.getTitle());
        if (!title.isEmpty()) {
            sb.append("\n").append(LABEL_COLOUR).append("Title: ").append(RESET)
                    .append(TEXT_COLOUR).append(title).append(RESET);
        }

        final var raw = extractedSummary.getRawData();
        if (raw != null && !raw.isNull()) {
            renderRichFields(sb, raw, extractedSummary.getSummary());
        }
        else if (!text(extractedSummary.getSummary()).isEmpty()) {
            // Fallback: no raw JSON, just use the summary field
            sb.append("\n").append(LABEL_COLOUR).append("Summary: ").append(RESET)
                    .append(TEXT_COLOUR).append(extractedSummary.getSummary()).append(RESET);
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Shared rendering
    // -------------------------------------------------------------------------

    private void renderRichFields(final StringBuilder sb, final JsonNode raw, final String fallbackSummary) {
        // Summary (prefer the raw JSON's summary, fall back to the provided summary)
        appendText(sb, "Summary", textOr(raw, "summary", fallbackSummary));

        // Keywords
        appendList(sb, "Keywords", raw, "keywords");

        // Goal
        appendText(sb, "Goal", text(raw, "goal"));

        // Key points
        appendList(sb, "Key points", raw, "key_points");

        // Key facts
        appendList(sb, "Key facts", raw, "key_facts");

        // Discoveries
        appendList(sb, "Discoveries", raw, "discoveries");

        // Accomplishments
        appendList(sb, "Accomplishments", raw, "accomplishments");

        // Action items
        appendList(sb, "Action items", raw, "action_items");

        // Relevant files
        appendList(sb, "Relevant files", raw, "relevant_files");

        // Citations
        appendCitations(sb, raw);

        // Sentiment
        appendText(sb, "Sentiment", text(raw, "sentiment"));

        // Confidence
        appendConfidence(sb, raw);

        // Metadata
        appendText(sb, "Metadata", text(raw, "metadata"));
    }
}
