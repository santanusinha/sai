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
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.model.ModelAttributes;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.models.ChatCompletionServiceFactory;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;
import com.phonepe.sentinelai.models.SimpleOpenAIModelOptions;
import com.phonepe.sentinelai.models.TokenCountingConfig;
import com.phonepe.sentinelai.toolbox.mcp.ComposingMCPToolBox;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolBox;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpToolReaders.ConfiguredHttpTool;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.InMemoryHttpToolSource;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.TemplatizedHttpTool;

import io.appform.sai.AgentConfig;
import io.appform.sai.SaiAgent;
import io.appform.sai.Settings;
import io.appform.sai.skills.AgentSkillsExtension;
import io.appform.sai.skills.SkillRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

@Slf4j
@AllArgsConstructor
public class AgentFactory {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are a helpful assistant that provides information and answers questions based on the user's input.
            You can use the tools at your disposal to gather information and provide accurate responses.
            Always strive to be clear, concise, and helpful in your answers.
            Do not make up information. If you don't know the answer, say you don't know.
            """;
    private final Settings settings;
    private final AgentExtension<String, String, SaiAgent> sessionExtension;
    private final ExecutorService executorService;
    private final ChatCompletionServiceFactory modelProviderFactory;
    private final ObjectMapper mapper;
    private final EventBus eventBus;
    private final OkHttpClient httpClient;

    public SaiAgent createAgent(String modelName, AgentConfig config) {
        final var modelSettings = Objects.requireNonNullElseGet(config.getModelSettings(),
                                                                this::defaultModelSettings)
                .withParallelToolCalls(false); //To keep context usage and console output sane
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
        final var cwdName = Paths.get("").toAbsolutePath().getFileName();
        final var cwd = "\nCurrent working directory: " + (cwdName != null ? cwdName.toString() : "/tmp") + "\n";
        final var saiAgent = new SaiAgent(config.getName(),
                                          config,
                                          settings,
                                          agentSetup,
                                          systemPrompt + cwd,
                                          List.of(sessionExtension),
                                          Map.of());
        registerMCPTools(saiAgent, config);
        registerHttpTools(saiAgent, config);
        return saiAgent;
    }

    /**
     * Register skills extension in multi-skill mode (from AgentConfig)
     */
    public void registerSkillsExtension(SaiAgent agent, AgentConfig config, Settings settings)
            throws IOException {
        final var skillDirs = config.getSkillDirectories();
        if (skillDirs == null || skillDirs.isEmpty()) {
            return;
        }

        registerSkillsExtension(agent, skillDirs, config.getSkillNames(), settings);
    }

    /**
     * Register skills extension with specific directories and optional name filter
     */
    public void registerSkillsExtension(
                                        SaiAgent agent,
                                        List<String> skillDirectories,
                                        List<String> skillNames,
                                        Settings settings) throws IOException {

        final var registry = new SkillRegistry(mapper);

        // Discover skills from all configured directories
        for (String dirPath : skillDirectories) {
            Path skillsDir = Paths.get(dirPath);
            if (!skillsDir.isAbsolute()) {
                // Resolve relative paths from config directory
                skillsDir = Paths.get(settings.getConfigDir(), dirPath);
            }

            if (Files.isDirectory(skillsDir)) {
                registry.discoverSkills(skillsDir);
            }
            else {
                log.warn("Skills directory does not exist: {}", skillsDir);
            }
        }

        // If specific skill names are configured, pre-load them
        if (skillNames != null && !skillNames.isEmpty()) {
            for (String skillName : skillNames) {
                registry.loadSkill(skillName)
                        .ifPresent(skill -> log.info("Pre-loaded skill: {}", skillName));
            }
        }

        final var extension = AgentSkillsExtension.builder()
                .registry(registry)
                .mapper(mapper)
                .singleSkillMode(false)
                .build();

        agent.registerToolbox(extension);
        log.info("Registered skills extension with {} skills", registry.getSkillNames().size());
    }

    /**
     * Register skills extension in single-skill mode (for --skill CLI option)
     */
    public void registerSkillsExtension(SaiAgent agent, Path skillPath, boolean singleSkillMode)
            throws IOException {
        final var registry = new SkillRegistry(mapper);
        registry.loadSkillFromPath(skillPath);

        final var extension = AgentSkillsExtension.builder()
                .registry(registry)
                .mapper(mapper)
                .singleSkillMode(true)
                .build();

        agent.registerToolbox(extension);
        log.info("Registered single skill from: {}", skillPath);
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
        httpTools.forEach((upstream, configuredUpstream) -> httpToolSource.register(upstream,
                                                                                    configuredUpstream.tools()
                                                                                            .stream()
                                                                                            .map(this::createTool)
                                                                                            .toList()));
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
