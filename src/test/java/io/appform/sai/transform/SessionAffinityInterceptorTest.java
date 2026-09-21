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
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.appform.sai.config.SessionAffinityConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

/**
 * Tests for {@link SessionAffinityInterceptor}.
 */
@Slf4j
class SessionAffinityInterceptorTest {

    private static final String SESSION_ID = "session-123";

    private MockWebServer server;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        server.enqueue(new MockResponse().setBody("{}"));
        mapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void testBodyFieldIsInjectedIntoChatCompletions() throws Exception {
        post("/v1/chat/completions", "{\"model\":\"m\"}", config(null, "session_id"));
        final var recorded = server.takeRequest();
        assertNull(recorded.getHeader("x-session-id"));
        final var body = mapper.readTree(recorded.getBody().readUtf8());
        assertEquals(SESSION_ID, body.get("session_id").asText());
        assertEquals("m", body.get("model").asText());
    }

    @Test
    void testBodyFieldIsNotInjectedIntoOtherEndpoints() throws Exception {
        post("/v1/embeddings", "{\"model\":\"m\"}", config("x-session-id", "session_id"));
        final var recorded = server.takeRequest();
        assertEquals(SESSION_ID, recorded.getHeader("x-session-id"));
        assertEquals("{\"model\":\"m\"}", recorded.getBody().readUtf8());
    }

    @Test
    void testBothHeaderAndBodyFieldAreApplied() throws Exception {
        post("/v1/chat/completions",
             "{\"model\":\"m\"}",
             config("x-session-id", "prompt_cache_key"));
        final var recorded = server.takeRequest();
        assertEquals(SESSION_ID, recorded.getHeader("x-session-id"));
        final var body = mapper.readTree(recorded.getBody().readUtf8());
        assertEquals(SESSION_ID, body.get("prompt_cache_key").asText());
    }

    @Test
    void testExistingBodyFieldValueIsOverwritten() throws Exception {
        post("/v1/chat/completions",
             "{\"model\":\"m\",\"session_id\":\"old\"}",
             config("x-session-id", "session_id"));
        final var recorded = server.takeRequest();
        final var body = mapper.readTree(recorded.getBody().readUtf8());
        assertEquals(SESSION_ID, body.get("session_id").asText());
    }

    @Test
    void testHeaderOnlyIsAddedToEveryRequest() throws Exception {
        post("/v1/chat/completions", "{\"model\":\"m\"}", config("x-session-id", null));
        final var recorded = server.takeRequest();
        assertEquals(SESSION_ID, recorded.getHeader("x-session-id"));
        assertEquals("{\"model\":\"m\"}", recorded.getBody().readUtf8());
    }

    private OkHttpClient client(SessionAffinityConfig config) {
        return new OkHttpClient.Builder()
                .addInterceptor(new SessionAffinityInterceptor(mapper, config, SESSION_ID))
                .build();
    }

    private SessionAffinityConfig config(String header, String bodyField) {
        return SessionAffinityConfig.builder()
                .header(header)
                .bodyField(bodyField)
                .build();
    }

    private void post(String path, String json, SessionAffinityConfig config) throws Exception {
        client(config).newCall(new Request.Builder()
                .url(server.url(path))
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build())
                .execute()
                .close();
    }
}
