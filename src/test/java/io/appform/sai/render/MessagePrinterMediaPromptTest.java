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
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.AudioFormat;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.ImageDetail;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for media user-prompt rendering in {@link MessagePrinter}. A media
 * prompt prints a short summary line with the payload size; the base64 payload
 * itself is never printed.
 */
class MessagePrinterMediaPromptTest {

    private static final String BASE64_100_BYTES = "A".repeat(136);

    private static String joined(UserPrompt userPrompt) {
        final var printer = new MessagePrinter(null, new ObjectMapper(), false);
        return printer.visit(userPrompt)
                .stream()
                .map(update -> update.getData() == null ? "" : update.getData())
                .reduce("", (a, b) -> a + b);
    }

    @Test
    void audioPromptPrintsSizeSummaryNotBase64() {
        final var userPrompt = UserPrompt.audio("session-1",
                                                "run-1",
                                                BASE64_100_BYTES,
                                                AudioFormat.WAV,
                                                null);
        final var output = joined(userPrompt);
        assertTrue(output.contains("audio data"), "summary should name the media kind");
        assertFalse(output.contains(BASE64_100_BYTES), "base64 payload must not be printed");
    }

    @Test
    void imagePromptPrintsSizeSummaryNotBase64() {
        final var userPrompt = UserPrompt.imageData("session-1",
                                                    "run-1",
                                                    "data:image/png;base64," + BASE64_100_BYTES,
                                                    ImageDetail.AUTO,
                                                    null);
        final var output = joined(userPrompt);
        assertTrue(output.contains("image data"), "summary should name the media kind");
        assertTrue(output.contains("102 B"), "summary should contain the decoded size");
        assertFalse(output.contains(BASE64_100_BYTES), "base64 payload must not be printed");
    }

    @Test
    void imageUrlPromptPrintsTheUrl() throws Exception {
        final var userPrompt = UserPrompt.imageURL("session-1",
                                                   "run-1",
                                                   java.net.URI.create("https://example.com/a.png").toURL(),
                                                   ImageDetail.AUTO,
                                                   null);
        final var output = joined(userPrompt);
        assertTrue(output.contains("https://example.com/a.png"), "url prompt should print the url");
    }

    @Test
    void textPromptStillPrintsContent() {
        final var content = "<?xml version='1.1' encoding='UTF-8'?><user_input><data>hello world</data></user_input>";
        final var userPrompt = UserPrompt.text("session-1", "run-1", content, null);
        final var output = joined(userPrompt);
        assertTrue(output.contains("hello world"), "text prompt should print its content");
    }
}
