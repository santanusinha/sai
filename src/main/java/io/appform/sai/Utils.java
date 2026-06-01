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

import com.google.common.base.Stopwatch;
import com.phonepe.sentinelai.core.model.ModelUsageStats;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Utils {

    private static final String C = Printer.Colours.CYAN;
    private static final String W = Printer.Colours.WHITE;
    private static final String G = Printer.Colours.GRAY;
    private static final String Y = Printer.Colours.YELLOW;
    private static final String GR = Printer.Colours.GREEN;
    private static final String R = Printer.Colours.RESET;

    public static float elapsedTimeInSeconds(final Stopwatch stopwatch) {
        return toSeconds(stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    public static float toSeconds(long millis) {
        return millis / 1000.0f;
    }

    public static String tokenSummary(final ModelUsageStats stats) {
        final var parts = new ArrayList<String>();
        final int input = stats.getRequestTokens();
        final int output = stats.getResponseTokens();
        final int cached = stats.getRequestTokenDetails().getCachedTokens();
        final int reasoning = stats.getResponseTokenDetails().getReasoningTokens();
        final int toolCalls = stats.getToolCallsForRun();
        final int requests = stats.getRequestsForRun();

        parts.add(C + "📥 In" + G + ":" + W + input);
        parts.add(C + "📤 Out" + G + ":" + W + output);
        if (cached > 0) {
            final float hitRate = input > 0 ? (cached * 100.0f / input) : 0.0f;
            final String rateColour = hitRate >= 50.0f ? GR : Y;
            parts.add(C + "💾 Cached" + G + ":" + W + cached
                    + G + " (" + rateColour + "%.1f%%".formatted(hitRate) + G + ")");
        }
        if (reasoning > 0) {
            parts.add(C + "🧠 Reasoning" + G + ":" + W + reasoning);
        }
        if (toolCalls > 0) {
            parts.add(C + "🔧 Tools" + G + ":" + W + toolCalls);
        }
        if (requests > 0) {
            parts.add(C + "🔄 Requests" + G + ":" + W + requests);
        }

        return G + "┤ " + String.join(G + "  ", parts) + G + " ├" + R;
    }

}
