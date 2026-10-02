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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.model.Model;

import io.appform.sai.config.ProviderEntry;
import io.appform.sai.config.SettingsConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Wire tests for the {@link ConfigurableProviderFactory}: models built from settings.yaml
 * hit the wire with the right URL, headers and body. Covers transformer wiring (extra
 * headers, session id injection, jolt), the Azure api-version query parameter, and the
 * no-session-id case.
 */
@Slf4j
class ModelFactoryWireTest {

    private static final String CHAT_COMPLETION_RESPONSE = """
            {
              "id": "chatcmpl-1",
              "object": "chat.completion",
              "created": 1,
              "model": "test-model",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": null,
                    "tool_calls": [
                      {
                        "id": "call-1",
                        "type": "function",
                        "function": {
                          "name": "__output_generator__",
                          "arguments": "{\\"output\\": \\"hi\\"}"
                        }
                      }
                    ]
                  },
                  "finish_reason": "tool_calls"
                }
              ],
              "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2}
            }
            """;

    private static final class WireAgent extends Agent<String, String, WireAgent> {

        private WireAgent(final AgentSetup setup) {
            super(String.class, "answer the user", setup, List.of(), Map.of());
        }

        @Override
        public String name() {
            return "wire-agent";
        }
    }

    private final MockWebServer server = new MockWebServer();

    @Test
    @SneakyThrows
    void azureModelAppendsApiVersionQueryParameter() {
        enqueueResponse();
        final var config = SettingsConfig.builder()
                .providers(Map.of("azure",
                                  ProviderEntry.builder()
                                          .type("azure")
                                          .endpoint(server.url("").toString())
                                          .apiKey("azure-key")
                                          .apiVersion("2024-10-21")
                                          .build()))
                .build();
        final var factory = new ConfigurableProviderFactory("azure", new OkHttpClient(), config);
        final var model = factory.model("my-deployment", "my-deployment");
        runModel(model, "session-1");

        final RecordedRequest request = server.takeRequest();
        assertTrue(request.getPath().replace("//", "/")
                .startsWith("/openai/deployments/my-deployment/chat/completions"),
                   "unexpected path: " + request.getPath());
        assertTrue(request.getPath().contains("api-version=2024-10-21"),
                   "api-version missing: " + request.getPath());
        assertEquals("Bearer azure-key", request.getHeader("Authorization"));
    }

    @Test
    @SneakyThrows
    void joltTransformerFromTuningHitsWire() {
        enqueueResponse();
        final var config = openAiConfig(null);
        final var jolt = com.phonepe.sentinelai.models.provider.JoltRequestTransformer.ofTransforms(
                                                                                                    List.of(JOLT_MARKER_TRANSFORM()));
        final var factory = new ConfigurableProviderFactory("openai",
                                                            new OkHttpClient(),
                                                            config,
                                                            List.of(jolt));
        runModel(factory.model("test-model", "test-model"), "session-1");

        final RecordedRequest request = server.takeRequest();
        final var body = new ObjectMapper().readTree(request.getBody().readUtf8());
        assertEquals("jolt-applied", body.get("jolt_marker").asText());
    }

    @Test
    @SneakyThrows
    void noSessionIdMeansNoHeaderNoBodyFieldNoFailure() {
        enqueueResponse();
        final var config = SettingsConfig.builder()
                .providers(Map.of("openai",
                                  ProviderEntry.builder()
                                          .type("openai")
                                          .endpoint(server.url("").toString())
                                          .apiKey("test-key")
                                          .build()))
                .build();
        final var factory = new ConfigurableProviderFactory("openai",
                                                            new OkHttpClient(),
                                                            config,
                                                            List.of(com.phonepe.sentinelai.models.provider.SessionIdInjectionTransformer
                                                                    .builder()
                                                                    .header("x-session-id")
                                                                    .bodyPath("/session_id")
                                                                    .build()));
        runModel(factory.model("test-model", "test-model"), null);

        final RecordedRequest request = server.takeRequest();
        assertTrue(request.getHeader("x-session-id") == null);
        final var body = new ObjectMapper().readTree(request.getBody().readUtf8());
        assertTrue(body.get("session_id") == null || body.get("session_id").isNull());
    }

    @Test
    @SneakyThrows
    void openAiModelHitsWireWithBearerOrgProjectAndExtraHeaders() {
        enqueueResponse();
        final var config = SettingsConfig.builder()
                .providers(Map.of("openai",
                                  ProviderEntry.builder()
                                          .type("openai")
                                          .endpoint(server.url("").toString())
                                          .apiKey("test-key")
                                          .organizationId("org-1")
                                          .projectId("proj-1")
                                          .extraHeaders(Map.of("X-Custom", "custom-val"))
                                          .build()))
                .build();
        final var factory = new ConfigurableProviderFactory("openai", new OkHttpClient(), config);
        final var model = factory.model("test-model", "test-model");
        final var output = runModel(model, "session-1");
        assertNotNull(output);
        assertNotNull(output.getData());

        final RecordedRequest request = server.takeRequest();
        assertEquals("/v1/chat/completions", request.getPath().replace("//", "/"));
        assertEquals("Bearer test-key", request.getHeader("Authorization"));
        assertEquals("org-1", request.getHeader("OpenAI-Organization"));
        assertEquals("proj-1", request.getHeader("OpenAI-Project"));
        assertEquals("custom-val", request.getHeader("X-Custom"));
        final var body = new ObjectMapper().readTree(request.getBody().readUtf8());
        assertEquals("test-model", body.get("model").asText());
    }

    @Test
    @SneakyThrows
    void sessionAffinityTransformerInjectsHeaderAndBodyField() {
        enqueueResponse();
        final var config = SettingsConfig.builder()
                .providers(Map.of("openai",
                                  ProviderEntry.builder()
                                          .type("openai")
                                          .endpoint(server.url("").toString())
                                          .apiKey("test-key")
                                          .build()))
                .build();
        final var factory = new ConfigurableProviderFactory("openai",
                                                            new OkHttpClient(),
                                                            config,
                                                            List.of(com.phonepe.sentinelai.models.provider.SessionIdInjectionTransformer
                                                                    .builder()
                                                                    .header("x-session-id")
                                                                    .bodyPath("/session_id")
                                                                    .build()));
        runModel(factory.model("test-model", "test-model"), "sess-42");

        final RecordedRequest request = server.takeRequest();
        assertEquals("sess-42", request.getHeader("x-session-id"));
        final var body = new ObjectMapper().readTree(request.getBody().readUtf8());
        assertEquals("sess-42", body.get("session_id").asText());
    }

    @BeforeEach
    void start() throws Exception {
        server.start();
    }

    @AfterEach
    void stop() throws Exception {
        server.shutdown();
    }

    private com.phonepe.sentinelai.models.provider.JoltTransform JOLT_MARKER_TRANSFORM() {
        return com.phonepe.sentinelai.models.provider.JoltTransform.builder()
                .operation("default")
                .spec(Map.of("jolt_marker", "jolt-applied"))
                .build();
    }

    private void enqueueResponse() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(CHAT_COMPLETION_RESPONSE));
    }

    private SettingsConfig openAiConfig(Map<String, String> extraHeaders) {
        final var entry = ProviderEntry.builder()
                .type("openai")
                .endpoint(server.url("").toString())
                .apiKey("test-key");
        if (extraHeaders != null && !extraHeaders.isEmpty()) {
            entry.extraHeaders(extraHeaders);
        }
        return SettingsConfig.builder()
                .providers(Map.of("openai", entry.build()))
                .build();
    }

    private AgentOutput<String> runModel(Model model, String sessionId) {
        final var inputBuilder = AgentInput.<String>builder().request("Hi?");
        if (sessionId != null) {
            inputBuilder.requestMetadata(AgentRequestMetadata.builder()
                    .sessionId(sessionId)
                    .userId("test-user")
                    .build());
        }
        final var agent = new WireAgent(AgentSetup.builder()
                .mapper(new ObjectMapper())
                .model(model)
                .build());
        return agent.execute(inputBuilder.build());
    }
}
