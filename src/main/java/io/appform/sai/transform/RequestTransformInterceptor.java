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

import java.io.IOException;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;

/**
 * An OkHttp interceptor that applies {@link RequestTransform} operations
 * to the JSON body of outgoing {@code /v1/chat/completions} requests.
 */
@Slf4j
public class RequestTransformInterceptor implements Interceptor {

    private static final String CHAT_COMPLETIONS_ENDPOINT = "/v1/chat/completions";

    private final ObjectMapper mapper;
    private final List<RequestTransform> transforms;
    private final JoltRequestTransformEngine engine;

    public RequestTransformInterceptor(ObjectMapper mapper, List<RequestTransform> transforms) {
        this.mapper = mapper;
        this.transforms = transforms;
        this.engine = new JoltRequestTransformEngine(mapper);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        var request = chain.request();
        var body = request.body();
        var url = request.url().toString();

        if (body != null && url.endsWith(CHAT_COMPLETIONS_ENDPOINT) && !transforms.isEmpty()) {
            var buffer = new Buffer();
            body.writeTo(buffer);
            var bodyString = buffer.readUtf8();

            if (!bodyString.isBlank()) {
                var payload = (ObjectNode) mapper.readTree(bodyString);

                if (log.isDebugEnabled()) {
                    log.debug("Original request payload for URL {}: {}", url, bodyString);
                }

                var modifiedPayload = engine.apply(payload, transforms);

                if (log.isDebugEnabled()) {
                    var printer = mapper.writerWithDefaultPrettyPrinter();
                    log.debug("Modified request payload for URL {}: {}",
                              url,
                              printer.writeValueAsString(modifiedPayload));
                }

                var contentType = body.contentType();
                var newBody = RequestBody.create(mapper.writeValueAsString(modifiedPayload), contentType);
                request = request.newBuilder().method(request.method(), newBody).build();
                log.info("Applied request transforms for URL {}", url);
            }
        }

        log.debug("HTTP Request: {} {}", request.method(), request.url());
        var response = chain.proceed(request);
        log.debug("HTTP Response: {} {} - {}",
                  response.code(),
                  response.request().url(),
                  response.message());
        return response;
    }
}
