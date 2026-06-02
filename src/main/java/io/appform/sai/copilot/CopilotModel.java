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
package io.appform.sai.copilot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * A single model entry from the GitHub Copilot {@code /models} endpoint.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CopilotModel {

    /** Nested capabilities block. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Capabilities {

        /** Token-limit details for this model. */
        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Limits {

            @JsonProperty("max_context_window_tokens")
            private int maxContextWindowTokens;

            @JsonProperty("max_output_tokens")
            private int maxOutputTokens;

            @JsonProperty("max_prompt_tokens")
            private int maxPromptTokens;
        }

        /** Feature-support flags for this model. */
        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Supports {

            @JsonProperty("tool_calls")
            private boolean toolCalls;

            @JsonProperty("parallel_tool_calls")
            private boolean parallelToolCalls;
        }

        @JsonProperty("type")
        private String type;

        @JsonProperty("family")
        private String family;

        @JsonProperty("limits")
        private Limits limits;

        @JsonProperty("supports")
        private Supports supports;
    }

    /** Stable model identifier used in API requests (e.g. {@code gpt-4o}). */
    @JsonProperty("id")
    private String id;

    /** Human-readable display name. */
    @JsonProperty("name")
    private String name;

    /** Model vendor (e.g. {@code OpenAI}, {@code Anthropic}, {@code Google}). */
    @JsonProperty("vendor")
    private String vendor;

    /** Whether this is a preview / experimental model. */
    @JsonProperty("preview")
    private boolean preview;

    /** Whether the model appears in the Copilot model picker UI. */
    @JsonProperty("model_picker_enabled")
    private boolean modelPickerEnabled;

    @JsonProperty("capabilities")
    private Capabilities capabilities;
}
