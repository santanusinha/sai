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

import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.models.SimpleOpenAIModelOptions;
import com.phonepe.sentinelai.toolbox.mcp.config.MCPConfiguration;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpToolReaders;

import io.appform.sai.config.ModelTuning;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.NonNull;
import lombok.Value;
import lombok.With;
import lombok.extern.jackson.Jacksonized;

@Value
@With
@Builder
@Jacksonized
public class AgentConfig {

    public static final ModelSettings DEFAULT_MODEL_SETTINGS = ModelSettings.builder()
            .temperature(0.1f)
            .build();

    @NonNull
    String agentId;

    @NonNull
    String name;

    @NonNull
    String description;

    @Nullable
    String prompt;

    @Default
    ModelSettings modelSettings = DEFAULT_MODEL_SETTINGS;

    @Default
    SimpleOpenAIModelOptions modelOptions = SimpleOpenAIModelOptions.DEFAULT;

    /**
     * Unified tuning section — preferred over {@code modelSettings} / {@code modelOptions}.
     * Uses the same {@link ModelTuning} class as {@code settings.yaml}.
     * If non-null, takes precedence over the legacy fields.
     */
    @Nullable
    ModelTuning tuning;

    MCPConfiguration mcp;

    @Default
    OutputGenerationMode outputGenerationMode = OutputGenerationMode.STRUCTURED_OUTPUT;

    /**
     * Default model string in {@code provider/model[/mode]} format.
     * Used when no {@code --model} CLI flag is provided.
     */
    @Nullable
    String model;

    Map<String, HttpToolReaders.ConfiguredUpstream> httpTools;
    String singleSkill;

    @Nullable
    List<String> skillDirectories;

    @Nullable
    List<String> skillNames;

}
