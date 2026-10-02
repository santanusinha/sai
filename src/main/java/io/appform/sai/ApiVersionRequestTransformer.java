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

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.phonepe.sentinelai.models.provider.RequestTransformer;
import com.phonepe.sentinelai.models.provider.RequestTransformerContext;

import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.Request;

/**
 * Request transformer that appends the {@code api-version} query parameter to every
 * request URL. Azure OpenAI requires this parameter on all calls.
 */
@Slf4j
public class ApiVersionRequestTransformer implements RequestTransformer {

    private final String apiVersion;

    /**
     * Creates a transformer that appends the given api-version to every request.
     *
     * @param apiVersion the Azure API version string (e.g. {@code 2024-10-21})
     */
    public ApiVersionRequestTransformer(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    @Override
    public void transform(Request.Builder requestBuilder, ObjectNode body, RequestTransformerContext ctx) {
        final HttpUrl currentUrl = requestBuilder.build().url();
        if (apiVersion.equals(currentUrl.queryParameter("api-version"))) {
            return;
        }
        final HttpUrl newUrl = currentUrl.newBuilder()
                .addQueryParameter("api-version", apiVersion)
                .build();
        log.debug("Appended api-version={}", apiVersion);
        requestBuilder.url(newUrl);
    }
}
