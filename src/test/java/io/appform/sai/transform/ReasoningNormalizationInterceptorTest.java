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
package io.appform.sai.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.utils.JsonUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

class ReasoningNormalizationInterceptorTest {

    private static final String DATA_PREFIX = "data:";
    private static final String SSE_DONE_LINE = "data" + ": " + "[DONE]";

    private ObjectMapper mapper;
    private ReasoningNormalizationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        mapper = JsonUtils.createMapper();
        interceptor = new ReasoningNormalizationInterceptor(mapper);
    }

    // --- Non-streaming tests ---

    @Test
    void testNonChatCompletionsEndpointPassthrough() throws Exception {
        var responseBody = """
                {"choices": [{"message": {"role": "assistant", "thinking": "Should NOT be normalised."}}]}
                """;
        var root = sendAndParseJsonResponseWithUrl(responseBody,
                                                   "application/json",
                                                   "/v1/embeddings");
        var message = root.at("/choices/0/message");
        assertTrue(message.has("thinking"));
        assertFalse(message.has("reasoning_content"));
    }

    @Test
    void testNonStreamingMultipleChoicesAllNormalised() throws Exception {
        var responseBody = """
                {
                  "choices": [
                    {
                      "message": {"role": "assistant", "thinking": "Choice 1 thinking.", "content": "A1"}
                    },
                    {
                      "message": {"role": "assistant", "thinking": "Choice 2 thinking.", "content": "A2"}
                    }
                  ]
                }
                """;
        var normalised = sendAndParseJsonResponse(responseBody, "application/json");
        assertEquals("Choice 1 thinking.",
                     normalised.at("/choices/0/message/reasoning_content").asText());
        assertEquals("Choice 2 thinking.",
                     normalised.at("/choices/1/message/reasoning_content").asText());
    }

    @Test
    void testNonStreamingNoReasoningFieldPassthrough() throws Exception {
        var responseBody = """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "content": "No reasoning here."
                    }
                  }]
                }
                """;
        var normalised = sendAndParseJsonResponse(responseBody, "application/json");
        var message = normalised.at("/choices/0/message");
        assertEquals("No reasoning here.", message.get("content").asText());
        assertFalse(message.has("reasoning_content"));
    }

    @Test
    void testNonStreamingReasoningContentAlreadyPresentNoOp() throws Exception {
        var responseBody = """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "reasoning_content": "Already present.",
                      "content": "Answer."
                    }
                  }]
                }
                """;
        var normalised = sendAndParseJsonResponse(responseBody, "application/json");
        var message = normalised.at("/choices/0/message");
        assertEquals("Already present.", message.get("reasoning_content").asText());
        assertFalse(message.has("thinking"));
    }

    @Test
    void testNonStreamingReasoningObjectFlattened() throws Exception {
        var responseBody = """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "reasoning": {
                        "content": [
                          {"type": "summary", "summary": "First part. "},
                          {"type": "summary", "summary": "Second part."}
                        ]
                      },
                      "content": "Answer."
                    }
                  }]
                }
                """;
        var normalised = sendAndParseJsonResponse(responseBody, "application/json");
        var message = normalised.at("/choices/0/message");
        assertEquals("First part. Second part.", message.get("reasoning_content").asText());
        assertFalse(message.has("reasoning"));
    }

    @Test
    void testNonStreamingReasoningObjectWithTextField() throws Exception {
        var responseBody = """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "reasoning": {"text": "Direct text reasoning."},
                      "content": "Answer."
                    }
                  }]
                }
                """;
        var normalised = sendAndParseJsonResponse(responseBody, "application/json");
        var message = normalised.at("/choices/0/message");
        assertEquals("Direct text reasoning.", message.get("reasoning_content").asText());
        assertFalse(message.has("reasoning"));
    }

    @Test
    void testNonStreamingThinkingContentFieldNormalised() throws Exception {
        var responseBody = """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "thinking_content": "Reasoning here.",
                      "content": "Answer."
                    }
                  }]
                }
                """;
        var normalised = sendAndParseJsonResponse(responseBody, "application/json");
        var message = normalised.at("/choices/0/message");
        assertEquals("Reasoning here.", message.get("reasoning_content").asText());
        assertFalse(message.has("thinking_content"));
    }

    @Test
    void testNonStreamingThinkingFieldNormalised() throws Exception {
        var responseBody = """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "thinking": "Let me analyse this step by step.",
                      "content": "The answer is 42."
                    }
                  }]
                }
                """;
        var normalised = sendAndParseJsonResponse(responseBody, "application/json");
        var message = normalised.at("/choices/0/message");
        assertEquals("Let me analyse this step by step.",
                     message.get("reasoning_content").asText());
        assertEquals("The answer is 42.", message.get("content").asText());
        assertFalse(message.has("thinking"));
    }

    // --- Streaming (SSE) tests ---

    @Test
    void testStreamingNoReasoningPassthrough() throws Exception {
        var sseResponse = DATA_PREFIX + " {\"choices\":[{\"delta\":{\"content\":\"Hello\"},\"index\":0}]}\n\n"
                + SSE_DONE_LINE + "\n\n";
        var lines = sendSseResponse(sseResponse);
        var firstChunk = parseSseDataLine(lines.get(0));
        assertEquals("Hello",
                     firstChunk.at("/choices/0/delta/content").asText());
        assertFalse(firstChunk.at("/choices/0/delta").has("reasoning_content"));
    }

    @Test
    void testStreamingReasoningContentAlreadyPresentNoOp() throws Exception {
        var sseResponse = DATA_PREFIX
                + " {\"choices\":[{\"delta\":{\"reasoning_content\":\"Already there.\",\"content\":null},\"index\":0}]}\n\n"
                + SSE_DONE_LINE + "\n\n";
        var lines = sendSseResponse(sseResponse);
        var firstChunk = parseSseDataLine(lines.get(0));
        assertEquals("Already there.",
                     firstChunk.at("/choices/0/delta/reasoning_content").asText());
    }

    @Test
    void testStreamingReasoningObjectFlattened() throws Exception {
        var sseResponse = DATA_PREFIX
                + " {\"choices\":[{\"delta\":{\"reasoning\":{\"content\":[{\"type\":\"summary\",\"summary\":\"Thinking...\"}]},\"content\":null},\"index\":0}]}\n\n"
                + SSE_DONE_LINE + "\n\n";
        var lines = sendSseResponse(sseResponse);
        var firstChunk = parseSseDataLine(lines.get(0));
        assertEquals("Thinking...",
                     firstChunk.at("/choices/0/delta/reasoning_content").asText());
        assertFalse(firstChunk.at("/choices/0/delta").has("reasoning"));
    }

    @Test
    void testStreamingThinkingFieldNormalised() throws Exception {
        var sseResponse = DATA_PREFIX
                + " {\"choices\":[{\"delta\":{\"thinking\":\"Let me think\",\"content\":null},\"index\":0}]}\n\n"
                + DATA_PREFIX + " {\"choices\":[{\"delta\":{\"thinking\":\" more\",\"content\":null},\"index\":0}]}\n\n"
                + DATA_PREFIX
                + " {\"choices\":[{\"delta\":{\"content\":\"The answer.\"},\"index\":0,\"finish_reason\":\"stop\"}]}\n\n"
                + SSE_DONE_LINE + "\n\n";
        var lines = sendSseResponse(sseResponse);
        assertFalse(lines.isEmpty());

        var firstChunk = parseSseDataLine(lines.get(0));
        assertEquals("Let me think",
                     firstChunk.at("/choices/0/delta/reasoning_content").asText());
        assertFalse(firstChunk.at("/choices/0/delta").has("thinking"));

        var secondChunk = parseSseDataLine(lines.get(1));
        assertEquals(" more",
                     secondChunk.at("/choices/0/delta/reasoning_content").asText());
        assertFalse(secondChunk.at("/choices/0/delta").has("thinking"));

        var thirdChunk = parseSseDataLine(lines.get(2));
        assertEquals("The answer.",
                     thirdChunk.at("/choices/0/delta/content").asText());
        assertFalse(thirdChunk.at("/choices/0/delta").has("reasoning_content"));

        assertEquals(SSE_DONE_LINE, lines.get(3));
    }

    // --- Unit tests for normaliseSseLine ---

    @Test
    void testUnitNormaliseSseLineBlankPassthrough() {
        var result = interceptor.normaliseSseLine("");
        assertEquals("", result);
    }

    @Test
    void testUnitNormaliseSseLineDonePassthrough() {
        var result = interceptor.normaliseSseLine(SSE_DONE_LINE);
        assertEquals(SSE_DONE_LINE, result);
    }

    @Test
    void testUnitNormaliseSseLineNonDataPassthrough() {
        var line = ": comment";
        var result = interceptor.normaliseSseLine(line);
        assertEquals(line, result);
    }

    @Test
    void testUnitNormaliseSseLineThinkingField() throws Exception {
        var line = DATA_PREFIX
                + " {\"choices\":[{\"delta\":{\"thinking\":\"Hello thinking\",\"content\":null},\"index\":0}]}";
        var result = interceptor.normaliseSseLine(line);
        var node = parseSseDataLine(result);
        assertEquals("Hello thinking",
                     node.at("/choices/0/delta/reasoning_content").asText());
        assertFalse(node.at("/choices/0/delta").has("thinking"));
    }

    // --- Helpers ---

    private JsonNode parseSseDataLine(String line) throws Exception {
        var payload = line.substring(DATA_PREFIX.length()).trim();
        return mapper.readTree(payload);
    }

    private JsonNode sendAndParseJsonResponse(String body, String contentType) throws IOException {
        return sendAndParseJsonResponseWithUrl(body, contentType, "/v1/chat/completions");
    }

    private JsonNode sendAndParseJsonResponseWithUrl(String body, String contentType, String path)
            throws IOException {
        try (var server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse()
                    .setBody(body)
                    .setHeader("Content-Type", contentType));
            var client = new OkHttpClient.Builder()
                    .addInterceptor(interceptor)
                    .build();
            var request = new Request.Builder()
                    .url(server.url(path))
                    .build();
            try (var response = client.newCall(request).execute()) {
                var responseBody = response.body().string();
                return mapper.readTree(responseBody);
            }
        }
    }

    private List<String> sendSseResponse(String sseBody) throws IOException {
        try (var server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse()
                    .setBody(sseBody)
                    .setHeader("Content-Type", "text/event-stream"));
            var client = new OkHttpClient.Builder()
                    .addInterceptor(interceptor)
                    .build();
            var request = new Request.Builder()
                    .url(server.url("/v1/chat/completions"))
                    .build();
            try (var response = client.newCall(request).execute()) {
                var rawBody = response.body().string();
                var lines = new ArrayList<String>();
                for (var line : rawBody.split("\n")) {
                    if (!line.isBlank()) {
                        lines.add(line);
                    }
                }
                return lines;
            }
        }
    }
}
