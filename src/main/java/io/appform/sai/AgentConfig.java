/*
 * Copyright (c) 2026 Original Author(s)
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

import com.fasterxml.jackson.databind.JsonNode;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.toolbox.mcp.config.MCPConfiguration;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpToolReaders;

import java.util.Map;

import javax.annotation.Nullable;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.NonNull;
import lombok.Value;
import lombok.With;

@Value
@With
@Builder
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

    @Nullable
    String model;

    @Nullable
    JsonNode inputSchema;

    @Nullable
    JsonNode outputSchema;

    @Default
    OutputGenerationMode outputGenerationMode = OutputGenerationMode.TOOL_BASED;

    @Default
    ModelSettings modelSettings = DEFAULT_MODEL_SETTINGS;

    MCPConfiguration mcp;

    Map<String, HttpToolReaders.ConfiguredUpstream> httpTools;
}
