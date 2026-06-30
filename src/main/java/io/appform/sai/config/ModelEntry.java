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
 * Represents a single model entry nested inside a {@link ProviderEntry} in {@code settings.yaml}.
 *
 * <p>Carries model-level tuning (which overrides provider-level defaults) and a map of
 * mode entries for sparse per-mode overrides.
 */
@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelEntry {

    /**
     * Model-level tuning — overrides provider-level defaults.
     */
    @Nullable
    ModelTuning tuning;

    /**
     * Designates the default mode for this model. If a model defines exactly one mode,
     * that mode is automatically the default even without this field.
     */
    @Nullable
    String defaultMode;

    /**
     * Modes defined under this model, keyed by mode name (e.g. {@code "coding"},
     * {@code "planning"}).
     */
    @Nullable
    Map<String, ModeEntry> modes;

    /**
     * Returns the mode entry for the given mode name, or {@code null} if not found.
     *
     * @param modeName the mode name
     * @return the mode entry, or {@code null}
     */
    @Nullable
    public ModeEntry getMode(@Nullable String modeName) {
        if (modes == null || modeName == null) {
            return null;
        }
        return modes.get(modeName);
    }

    /**
     * Resolves the effective mode name, applying the auto-default rule:
     * if exactly one mode is defined and no explicit mode is given, that mode is the default.
     *
     * @param requestedMode the mode requested by the user (may be {@code null})
     * @return the resolved mode name, or {@code null} if no mode applies
     */
    @Nullable
    public String resolveModeName(@Nullable String requestedMode) {
        if (requestedMode != null) {
            return requestedMode;
        }
        if (defaultMode != null) {
            return defaultMode;
        }
        //        if (modes != null && modes.size() == 1) {
        //            return modes.keySet().iterator().next();
        //        }
        return null;
    }
}
