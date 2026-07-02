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
package io.appform.sai;

import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.agent.StreamConsumer;

import lombok.AllArgsConstructor;

@AllArgsConstructor
class AgentStreamConsumer implements StreamConsumer {
    // So we don't cock up the internal buffers if both
    // reasoning and content are being streamed at the same time
    private final BufferedOutputPrinter reasoningPrinter;
    private final BufferedOutputPrinter outputPrinter;

    @Override
    public void consumeReasoningAndContent(String reasoningData, String content) {
        if (!Strings.isNullOrEmpty(reasoningData)) {
            reasoningPrinter.accept(reasoningData);
        }
        if (!Strings.isNullOrEmpty(content)) {
            outputPrinter.accept(content);
        }
    }

    public void markDone() {
        reasoningPrinter.markDone();
        outputPrinter.markDone();
    }
}
