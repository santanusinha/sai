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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.appform.sai.term.Printer;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Utils#contextUsageSummary(int, int)}: the post-run
 * context-usage segment appended to the token summary line. Verifies the usage
 * percent and compaction threshold are shown, the recommendation hint appears
 * only at or above the threshold, and unusable inputs yield {@code null}.
 */
class UtilsContextUsageSummaryTest {

    private static final String GREEN = Printer.Colours.GREEN;
    private static final String YELLOW = Printer.Colours.YELLOW;
    private static final String RED = Printer.Colours.RED;

    private static int countOccurrences(final String haystack, final String needle) {
        var count = 0;
        var index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }

    @Test
    void capsAtHundred() {
        final var summary = Utils.contextUsageSummary(100, 60);
        assertNotNull(summary);
        assertEquals(1, countOccurrences(summary, "100%"));
    }

    @Test
    void rendersHintAndYellowAtThreshold() {
        final var summary = Utils.contextUsageSummary(60, 60);
        assertNotNull(summary);
        assertTrue(summary.contains(YELLOW), "usage at threshold must be yellow");
        assertTrue(summary.contains("/compact recommended"), "hint must appear at threshold");
    }

    @Test
    void rendersRedAtNinetyAndAbove() {
        final var summary = Utils.contextUsageSummary(95, 60);
        assertNotNull(summary);
        assertTrue(summary.contains(RED), "usage at 90%+ must be red");
        assertTrue(summary.contains("/compact recommended"));
    }

    @Test
    void rendersRedWhenThresholdAboveNinety() {
        // Threshold 95: 92% is above 90 so still red even though below threshold.
        final var summary = Utils.contextUsageSummary(92, 95);
        assertNotNull(summary);
        assertTrue(summary.contains(RED));
        assertTrue(!summary.contains("/compact recommended"), "below threshold must not hint");
    }

    @Test
    void rendersUsageAndThresholdBelowThreshold() {
        final var summary = Utils.contextUsageSummary(42, 60);
        assertNotNull(summary);
        assertTrue(summary.contains("42%"), "usage percent must be rendered");
        assertTrue(summary.contains("60%"), "threshold must be rendered");
        assertTrue(summary.contains("compacts at"), "threshold label must be rendered");
        assertTrue(summary.contains(GREEN), "usage below threshold must be green");
        assertTrue(!summary.contains("/compact recommended"), "no hint below threshold");
    }

    @Test
    void rendersYellowAboveThresholdButBelowNinety() {
        final var summary = Utils.contextUsageSummary(75, 60);
        assertNotNull(summary);
        assertTrue(summary.contains(YELLOW));
        assertTrue(!summary.contains(RED), "below 90% must not be red");
        assertTrue(summary.contains("/compact recommended"));
    }

    @Test
    void returnsNullForNegativeThreshold() {
        assertNull(Utils.contextUsageSummary(42, -1));
    }

    @Test
    void returnsNullForNegativeUsage() {
        assertNull(Utils.contextUsageSummary(-1, 60));
    }
}
