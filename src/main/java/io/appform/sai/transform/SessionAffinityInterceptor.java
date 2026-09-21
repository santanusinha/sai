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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.appform.sai.config.SessionAffinityConfig;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;

/**
 * An OkHttp interceptor that sends the current session id to a provider as a
 * cache-affinity signal.
 *
 * <p>Providers use different mechanisms for cache-affinity routing, so both
 * the header name and the body field name come from
 * {@link SessionAffinityConfig}. When a header is configured, it is added to
 * every request. When a body field is configured, it is injected as a
 * top-level field of {@code /v1/chat/completions} request bodies.
 */
@Slf4j
public class SessionAffinityInterceptor implements Interceptor {

    private static final String CHAT_COMPLETIONS_ENDPOINT = "/v1/chat/completions";

    private final ObjectMapper mapper;
    private final SessionAffinityConfig config;
    private final String sessionId;

    public SessionAffinityInterceptor(ObjectMapper mapper, SessionAffinityConfig config, String sessionId) {
        this.mapper = mapper;
        this.config = config;
        this.sessionId = sessionId;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        var request = chain.request();
        if (config.getHeader() != null && !config.getHeader().isBlank()) {
            request = request.newBuilder()
                    .header(config.getHeader(), sessionId)
                    .build();
            log.debug("Added session affinity header: {}", config.getHeader());
        }
        final var bodyField = config.getBodyField();
        if (bodyField != null && !bodyField.isBlank() && request.body() != null
                && request.url().toString().endsWith(CHAT_COMPLETIONS_ENDPOINT)) {
            request = injectBodyField(request, bodyField);
        }
        return chain.proceed(request);
    }

    /**
     * Injects the session id as a top-level field of the JSON request body.
     *
     * @param request   the outgoing request
     * @param bodyField the top-level field name to set
     * @return the request with the session id injected into its body
     * @throws IOException when the request body cannot be read or rewritten
     */
    private okhttp3.Request injectBodyField(okhttp3.Request request, String bodyField) throws IOException {
        var buffer = new Buffer();
        request.body().writeTo(buffer);
        var bodyString = buffer.readUtf8();
        if (bodyString.isBlank()) {
            return request;
        }
        var payload = (ObjectNode) mapper.readTree(bodyString);
        payload.putPOJO(bodyField, sessionId);
        var contentType = request.body().contentType();
        var newBody = RequestBody.create(mapper.writeValueAsString(payload), contentType);
        log.debug("Injected session affinity body field: {}", bodyField);
        return request.newBuilder().method(request.method(), newBody).build();
    }
}
