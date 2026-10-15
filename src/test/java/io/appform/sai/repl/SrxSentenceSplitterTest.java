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
package io.appform.sai.repl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

/**
 * Unit tests for {@link SrxSentenceSplitter}.
 *
 * <p>These tests load the bundled {@code segment.srx} resource. A failure in
 * the SRX parser setup shows up here as an initialization error.
 */
class SrxSentenceSplitterTest {

    private final SrxSentenceSplitter splitter = new SrxSentenceSplitter(Locale.ENGLISH);

    @Test
    void abbreviationIsNotABoundary() {
        final var sentences = splitter.split("Dr. Smith went home. He slept.");
        assertEquals(2, sentences.size(), "abbreviation must not split: " + sentences);
        assertTrue(sentences.get(0).contains("Dr. Smith went home."));
    }

    @Test
    void bundledRulesAreLoaded() {
        // A load failure (for example a missing resource or an unusable SRX
        // parser) surfaces as an exception here.
        final List<String> sentences = splitter.split("One. Two.");
        assertEquals(2, sentences.size(), "expected two sentences, got: " + sentences);
    }

    @Test
    void decimalAndAbbreviationAreNotBoundaries() {
        final var sentences = splitter.split("It costs 3.5 dollars, e.g. at No. 5. Then he paid.");
        assertEquals(2, sentences.size(), "no false boundary: " + sentences);
    }

    @Test
    void emittablePrefixKeepsBoundaryInsideTheMarginHeld() {
        // The only boundary is inside the last 80 characters, so nothing is
        // safe yet: more text may extend it.
        final var text = "x".repeat(70) + ". Tail";
        assertEquals("", splitter.emittablePrefix(text, 80));
    }

    @Test
    void emittablePrefixReturnsEmptyForEmptyText() {
        assertEquals("", splitter.emittablePrefix("", 80));
    }

    @Test
    void emittablePrefixStopsBeforeTheMargin() {
        final var text = "First sentence. " + "x".repeat(85);
        final var prefix = splitter.emittablePrefix(text, 80);
        assertEquals("First sentence. ",
                     prefix,
                     "only the boundary before the margin is safe");
    }

    @Test
    void orderedListMarkerIsNotABoundary() {
        final var sentences = splitter.split("1. First item. Second sentence.");
        assertEquals(2, sentences.size(), "list marker must not split: " + sentences);
        assertTrue(sentences.get(0).contains("1. First item."));
    }

    @Test
    void shortTextHasNoEmittablePrefix() {
        // The text is shorter than the margin, so nothing is safe to emit.
        assertEquals("", splitter.emittablePrefix("Short. Text.", 80));
    }

    @Test
    void urlIsNotABoundary() {
        final var sentences = splitter.split("See https://example.com/docs for details. Done.");
        assertEquals(2, sentences.size(), "URL dots must not split: " + sentences);
        assertTrue(sentences.get(0).contains("https://example.com/docs"));
    }
}
