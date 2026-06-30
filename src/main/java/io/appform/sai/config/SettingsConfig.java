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
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

import javax.annotation.Nullable;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Root configuration class for {@code settings.yaml}.
 *
 * <p>Contains a map of provider entries, each defining a provider with its type, connection
 * details, and nested model/mode hierarchy.
 */
@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SettingsConfig {

    public static final SettingsConfig DEFAULT = new SettingsConfig(Map.of());

    @JsonProperty("providers")
    @Nullable
    Map<String, ProviderEntry> providers;

    /**
     * Returns the provider entry for the given name, or {@code null} if not found.
     *
     * @param providerName the provider name (e.g. {@code "openai"}, {@code "azure"})
     * @return the provider entry, or {@code null}
     */
    @Nullable
    public ProviderEntry getProvider(@Nullable String providerName) {
        if (providers == null || providerName == null) {
            return null;
        }
        return providers.get(providerName);
    }

    /**
     * Checks whether this config has any providers defined.
     *
     * @return {@code true} if the providers map is null or empty
     */
    public boolean isEmpty() {
        return providers == null || providers.isEmpty();
    }
}
