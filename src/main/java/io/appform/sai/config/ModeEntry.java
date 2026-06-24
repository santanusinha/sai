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

import javax.annotation.Nullable;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Represents a single mode entry nested inside a {@link ModelEntry} in {@code settings.yaml}.
 *
 * <p>Carries sparse tuning overrides that are merged on top of the model-level settings
 * when the mode is active.
 */
@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModeEntry {

    /**
     * Mode-level tuning overrides — merged on top of model-level settings.
     */
    @Nullable
    ModelTuning tuning;
}
