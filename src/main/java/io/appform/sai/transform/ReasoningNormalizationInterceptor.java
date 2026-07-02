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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * An OkHttp interceptor that normalises reasoning/thinking fields in responses
 * from {@code /v1/chat/completions} so that downstream code only needs to
 * look for {@code reasoning_content}.
 *
 * <p>Several LLM providers return chain-of-thought reasoning using different
 * JSON field names:
 * <ul>
 * <li>DeepSeek, Qwen, Zhipu, Moonshot, Volcengine → {@code reasoning_content} (no-op)</li>
 * <li>Google Gemini (OpenAI-compatible) → {@code thinking}</li>
 * <li>Anthropic (OpenAI-compatible) → {@code thinking} or {@code thinking_content}</li>
 * <li>OpenAI o-series → {@code reasoning} (object with nested content)</li>
 * </ul>
 *
 * <p>The interceptor reads the response body, rewrites any of the alternate
 * fields to {@code reasoning_content}, and rebuilds the response. Both
 * streaming (SSE) and non-streaming responses are handled.
 */
@Slf4j
public class ReasoningNormalizationInterceptor implements Interceptor {

    private static final String CHAT_COMPLETIONS_ENDPOINT = "/v1/chat/completions";
    private static final String REASONING_CONTENT = "reasoning_content";
    private static final String DATA_PREFIX = "data:";
    private static final String SSE_DONE = "[DONE]";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");
    private static final MediaType SSE_MEDIA_TYPE = MediaType.parse("text/event-stream");

    private static final Set<String> ALTERNATE_REASONING_FIELDS = Set.of(
                                                                         "thinking",
                                                                         "thinking_content");

    private final ObjectMapper mapper;

    public ReasoningNormalizationInterceptor(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        var request = chain.request();
        var response = chain.proceed(request);

        if (!isChatCompletionsEndpoint(request.url().encodedPath())) {
            return response;
        }

        var contentType = response.body() != null ? response.body().contentType() : null;
        if (isStreaming(contentType)) {
            return normaliseStreamingResponse(response);
        }
        return normaliseJsonResponse(response);
    }

    String normaliseSseLine(String line) {
        if (line == null || line.isBlank() || !line.startsWith(DATA_PREFIX)) {
            return line;
        }
        var payload = line.substring(DATA_PREFIX.length()).trim();
        if (SSE_DONE.equals(payload) || payload.isEmpty()) {
            return line;
        }
        try {
            var root = (ObjectNode) mapper.readTree(payload);
            if (normaliseChoices(root, "delta")) {
                return DATA_PREFIX + mapper.writeValueAsString(root);
            }
        }
        catch (JsonProcessingException e) {
            log.debug("Could not parse SSE line for reasoning normalisation: {}", payload);
        }
        return line;
    }

    /**
     * Extracts a plain-text reasoning string from the various structures
     * used by the OpenAI o-series {@code reasoning} field.
     *
     * <p>Supported structures:
     * <ul>
     * <li>Simple string: {@code "reasoning": "..."}</li>
     * <li>Object with content array:
     * {@code "reasoning": {"content": [{"type": "summary", "summary": "..."}]}}</li>
     * <li>Object with text field: {@code "reasoning": {"text": "..."}}</li>
     * </ul>
     *
     * @param reasoning the reasoning JSON node
     * @return the extracted text, or {@code null} if no text could be extracted
     */
    private String extractReasoningText(JsonNode reasoning) {
        if (reasoning.isTextual()) {
            return reasoning.asText();
        }
        if (reasoning.isObject()) {
            // Try "text" field
            var text = reasoning.get("text");
            if (text != null && text.isTextual()) {
                return text.asText();
            }
            // Try "content" array — each item may have "summary" or "text"
            var content = reasoning.get("content");
            if (content != null && content.isArray()) {
                var sb = new StringBuilder();
                for (JsonNode item : content) {
                    if (item.isTextual()) {
                        sb.append(item.asText());
                    }
                    else if (item.isObject()) {
                        var summary = item.get("summary");
                        if (summary != null) {
                            if (summary.isTextual()) {
                                sb.append(summary.asText());
                            }
                            else if (summary.isArray()) {
                                for (JsonNode s : summary) {
                                    sb.append(s.asText());
                                }
                            }
                        }
                        else {
                            var itemText = item.get("text");
                            if (itemText != null) {
                                sb.append(itemText.asText());
                            }
                        }
                    }
                }
                var result = sb.toString();
                if (!result.isEmpty()) {
                    return result;
                }
            }
        }
        return null;
    }

    // ─── Non-streaming ────────────────────────────────────────────

    private boolean isChatCompletionsEndpoint(String path) {
        return path != null && path.endsWith(CHAT_COMPLETIONS_ENDPOINT);
    }

    // ─── Streaming (SSE) ─────────────────────────────────────────

    private boolean isStreaming(MediaType contentType) {
        return contentType != null && SSE_MEDIA_TYPE.equals(contentType);
    }

    /**
     * Iterates over {@code choices[].<wrapperField>} and normalises reasoning fields.
     *
     * @param root         the root response node
     * @param wrapperField either {@code "message"} (non-streaming) or {@code "delta"} (streaming)
     * @return {@code true} if any node was modified
     */
    private boolean normaliseChoices(ObjectNode root, String wrapperField) {
        var choices = root.get("choices");
        if (choices == null || !choices.isArray()) {
            return false;
        }
        var changed = false;
        for (JsonNode choice : choices) {
            if (!(choice instanceof ObjectNode choiceNode)) {
                continue;
            }
            var wrapper = choiceNode.get(wrapperField);
            if (!(wrapper instanceof ObjectNode wrapperNode)) {
                continue;
            }
            if (normaliseReasoningFields(wrapperNode)) {
                changed = true;
            }
        }
        return changed;
    }

    // ─── Shared JSON rewriting ────────────────────────────────────

    private Response normaliseJsonResponse(Response response) throws IOException {
        if (response.body() == null) {
            return response;
        }
        var responseBody = response.body().string();
        if (responseBody.isBlank()) {
            return rebuildResponse(response, responseBody, JSON_MEDIA_TYPE);
        }

        try {
            var root = (ObjectNode) mapper.readTree(responseBody);
            var changed = normaliseChoices(root, "message");
            if (!changed) {
                return rebuildResponse(response, responseBody, JSON_MEDIA_TYPE);
            }
            return rebuildResponse(response, mapper.writeValueAsString(root), JSON_MEDIA_TYPE);
        }
        catch (JsonProcessingException e) {
            log.warn("Failed to parse chat completion response for reasoning normalisation; returning original", e);
            return rebuildResponse(response, responseBody, JSON_MEDIA_TYPE);
        }
    }

    /**
     * Rewrites alternate reasoning fields to {@code reasoning_content}.
     *
     * <p>Handles:
     * <ul>
     * <li>String fields: {@code thinking}, {@code thinking_content} → {@code reasoning_content}</li>
     * <li>Object field: {@code reasoning} (with nested content) → flattened to string {@code reasoning_content}</li>
     * </ul>
     *
     * @param node the message or delta node to normalise
     * @return {@code true} if the node was modified
     */
    private boolean normaliseReasoningFields(ObjectNode node) {
        // Skip if reasoning_content already exists
        if (node.has(REASONING_CONTENT)) {
            return false;
        }

        // Check simple string alternate fields: thinking, thinking_content
        for (var field : ALTERNATE_REASONING_FIELDS) {
            var value = node.get(field);
            if (value != null && value.isTextual()) {
                node.set(REASONING_CONTENT, value);
                node.remove(field);
                return true;
            }
        }

        // Check "reasoning" field — OpenAI o-series returns an object with nested content
        var reasoning = node.get("reasoning");
        if (reasoning != null) {
            var text = extractReasoningText(reasoning);
            if (text != null) {
                node.put(REASONING_CONTENT, text);
                node.remove("reasoning");
                return true;
            }
        }

        return false;
    }

    private Response normaliseStreamingResponse(Response response) throws IOException {
        if (response.body() == null) {
            return response;
        }
        var source = response.body().source();
        var sb = new StringBuilder();
        while (!source.exhausted()) {
            var line = source.readUtf8Line();
            if (line == null) {
                break;
            }
            sb.append(normaliseSseLine(line)).append('\n');
        }
        var newBody = sb.toString();
        return rebuildResponse(response, newBody, SSE_MEDIA_TYPE);
    }

    // ─── Response rebuilding ─────────────────────────────────────

    private Response rebuildResponse(Response original, String bodyString, MediaType mediaType) {
        var newBody = ResponseBody.create(bodyString, mediaType);
        return original.newBuilder()
                .body(newBody)
                .build();
    }
}
