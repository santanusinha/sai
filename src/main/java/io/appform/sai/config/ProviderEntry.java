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
package io.appform.sai.config;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

import javax.annotation.Nullable;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Represents a single provider entry in {@code settings.yaml}.
 *
 * <p>Defines the provider type ({@code openai} or {@code azure}), connection details
 * (endpoint, API key, etc.), optional provider-level tuning defaults, and a map of
 * model entries.
 *
 * <p>The {@code copilot} provider is never listed here — it is always handled as a built-in
 * by {@link io.appform.sai.ConfigurableProviderFactory}.
 */
@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProviderEntry {

    /**
     * Provider type: {@code "openai"} or {@code "azure"}.
     */
    @Nullable
    String type;

    /**
     * API endpoint / base URL for the provider.
     */
    @Nullable
    String endpoint;

    /**
     * API key (typically an {@code ${ENV}} reference, not a literal).
     */
    @Nullable
    String apiKey;

    /**
     * Azure API version (only used when {@code type == "azure"}).
     */
    @Nullable
    String apiVersion;

    /**
     * OpenAI organization ID.
     */
    @Nullable
    String organizationId;

    /**
     * OpenAI project ID.
     */
    @Nullable
    String projectId;

    /**
     * Extra HTTP headers to inject into every request to this provider.
     */
    @Nullable
    Map<String, String> extraHeaders;

    /**
     * Provider-level tuning defaults — apply to all models under this provider.
     */
    @Nullable
    ModelTuning tuning;

    /**
     * Models defined under this provider, keyed by model ID.
     */
    @Nullable
    Map<String, ModelEntry> models;

    /**
     * Returns the model entry for the given model ID, or {@code null} if not found.
     *
     * @param modelId the model ID (may contain slashes, e.g. {@code "anthropic/claude-3.5-sonnet"})
     * @return the model entry, or {@code null}
     */
    @Nullable
    public ModelEntry getModel(@Nullable String modelId) {
        if (models == null || modelId == null) {
            return null;
        }
        return models.get(modelId);
    }
}
