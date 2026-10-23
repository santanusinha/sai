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
package io.appform.sai.repl;

import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.agent.StreamConsumer;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class AgentStreamConsumer implements StreamConsumer {
    // So we don't cock up the internal buffers if both
    // reasoning and content are being streamed at the same time
    private final BufferedOutputPrinter reasoningPrinter;
    private final BufferedOutputPrinter outputPrinter;

    /** Becomes true when the first reasoning chunk of the turn arrives. */
    private boolean reasoningStarted;

    @Override
    public void consumeReasoningAndContent(String reasoningData, String content) {
        if (!Strings.isNullOrEmpty(reasoningData)) {
            if (!reasoningStarted) {
                reasoningStarted = true;
                reasoningPrinter.printReasoningStartedMarker();
            }
            reasoningPrinter.accept(reasoningData);
        }
        if (!Strings.isNullOrEmpty(content)) {
            outputPrinter.accept(content);
        }
    }

    public void markDone() {
        if (reasoningStarted) {
            reasoningPrinter.markDone();
            reasoningPrinter.printReasoningCompletedMarker();
            reasoningStarted = false;
        }
        outputPrinter.markDone();
    }
}
