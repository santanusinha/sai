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
package io.appform.sai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knuddels.jtokkit.api.EncodingType;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.model.ModelAttributes;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.utils.EnvLoader;
import com.phonepe.sentinelai.models.ChatCompletionServiceFactory;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;
import com.phonepe.sentinelai.models.SimpleOpenAIModelOptions;
import com.phonepe.sentinelai.models.TokenCountingConfig;
import com.phonepe.sentinelai.session.AgentSessionExtension;
import com.phonepe.sentinelai.toolbox.mcp.ComposingMCPToolBox;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolBox;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpToolReaders.ConfiguredHttpTool;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.InMemoryHttpToolSource;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.TemplatizedHttpTool;

import io.appform.sai.AgentConfig;
import io.appform.sai.SaiAgent;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import lombok.AllArgsConstructor;
import okhttp3.OkHttpClient;

@AllArgsConstructor
public class AgentFactory {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are a helpful assistant that provides information and answers questions based on the user's input.You can use the tools at your disposal to gather information and provide accurate responses.Always strive to be clear,concise, and helpful in your answers.
            You can use /tmp/sai/< session id>/scrath/ directory to store any temporary files you need during the conversation.No need to clean up the files, they will be automatically deleted after the session ends.
            If working on a coding project, first look for and read any AGENTS.md file in the project directory for any specific instructions or guidelines related to the project. If such a file exists, make sure to follow the instructions provided in it while working on the project.
            """;

    AgentSessionExtension<String, String, SaiAgent> sessionExtension;
    ExecutorService executorService;
    ChatCompletionServiceFactory modelProviderFactory;
    ObjectMapper mapper;
    EventBus eventBus;
    OkHttpClient httpClient;

    public SaiAgent createAgent(AgentConfig config) {
        final var modelName = Objects.requireNonNullElseGet(config.getModel(),
                                                            () -> EnvLoader.readEnv("MODEL", "gemini-3-pro-preview"));
        final var modelSettings = Objects.requireNonNullElseGet(config.getModelSettings(),
                                                                this::defaultModelSettings);
        final var agentSetup = AgentSetup.builder()
                .executorService(executorService)
                .mapper(mapper)
                .eventBus(eventBus)
                .modelSettings(modelSettings)
                .model(new SimpleOpenAIModel<>(modelName,
                                               modelProviderFactory,
                                               mapper,
                                               SimpleOpenAIModelOptions
                                                       .builder()
                                                       .tokenCountingConfig(TokenCountingConfig.DEFAULT)
                                                       .build()))
                .outputGenerationMode(config.getOutputGenerationMode())
                .build();
        final var systemPrompt = Objects.requireNonNullElse(config.getPrompt(), DEFAULT_SYSTEM_PROMPT);
        final var cwd = "\nCurrent working directory: " + System.getProperty("user.dir") + "\n";
        final var saiAgent = new SaiAgent(
                                          agentSetup,
                                          systemPrompt + cwd,
                                          List.of(sessionExtension),
                                          Map.of());
        registerMCPTools(saiAgent, config);
        registerHttpTools(saiAgent, config);
        return saiAgent;
    }

    private TemplatizedHttpTool createTool(ConfiguredHttpTool tool) {
        return new TemplatizedHttpTool(
                                       tool.metadata(),
                                       tool.definition(),
                                       tool.transformer());
    }

    private ModelSettings defaultModelSettings() {
        return ModelSettings.builder()
                .parallelToolCalls(false)
                .modelAttributes(ModelAttributes.builder()
                        .contextWindowSize(128_000)
                        .encodingType(EncodingType.O200K_BASE)
                        .build())
                .build();
    }

    private void registerHttpTools(SaiAgent saiAgent, AgentConfig config) {
        final var httpTools = config.getHttpTools();
        if (httpTools == null || httpTools.isEmpty()) {
            return;
        }
        final var httpToolSource = InMemoryHttpToolSource.builder()
                .mapper(mapper)
                .build();
        httpTools.forEach((upstream, configuredUpstream) -> {

            httpToolSource.register(upstream,
                                    configuredUpstream.tools()
                                            .stream()
                                            .map(this::createTool)
                                            .toList());
        });
        saiAgent.registerToolbox(HttpToolBox.builder()
                .httpToolSource(httpToolSource)
                .httpClient(httpClient)
                .mapper(mapper)
                .build());
    }

    private void registerMCPTools(SaiAgent agent, AgentConfig config) {
        if (config.getMcp() == null) {
            return;
        }
        agent.registerToolbox(new ComposingMCPToolBox(mapper, config.getMcp(), "mcp"));
    }
}
