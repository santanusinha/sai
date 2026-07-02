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
package io.appform.sai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knuddels.jtokkit.api.EncodingType;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.AutoCompactionSetup;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.model.ModelAttributes;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.models.ChatCompletionServiceFactory;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;
import com.phonepe.sentinelai.models.SimpleOpenAIModelOptions;
import com.phonepe.sentinelai.toolbox.mcp.ComposingMCPToolBox;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolBox;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpToolReaders.ConfiguredHttpTool;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.InMemoryHttpToolSource;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.TemplatizedHttpTool;

import io.appform.sai.AgentConfig;
import io.appform.sai.ConfigurableProviderFactory;
import io.appform.sai.SaiAgent;
import io.appform.sai.Settings;
import io.appform.sai.config.ModelTuning;
import io.appform.sai.config.SettingsConfig;
import io.appform.sai.config.SettingsResolver;
import io.appform.sai.transform.ReasoningNormalizationInterceptor;
import io.appform.sai.transform.RequestTransformInterceptor;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import javax.annotation.Nullable;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

@Slf4j
@AllArgsConstructor
/**
 * Factory responsible for constructing and configuring {@link SaiAgent} instances.
 *
 * <p>Each call to {@link #createAgent(String, AgentConfig)} creates a fresh agent bound to the
 * provided model name and {@link AgentConfig}, wires all session and skill extensions, registers
 * any configured MCP or HTTP toolboxes, and injects a sanitised current-working-directory hint
 * into the system prompt.
 */
public class AgentFactory {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are a helpful assistant that provides information and answers questions based on the user's input.
            You can use the tools at your disposal to gather information and provide accurate responses.
            Always strive to be clear, concise, and helpful in your answers.
            Do not make up information. If you don't know the answer, say you don't know.
            """;
    private final Settings settings;
    private final List<AgentExtension<String, String, SaiAgent>> sessionExtensions;
    private final ExecutorService executorService;
    private final ChatCompletionServiceFactory modelProviderFactory;
    private final ObjectMapper mapper;
    private final EventBus eventBus;
    private final OkHttpClient httpClient;
    private final SettingsConfig settingsConfig;

    /**
     * Creates a new {@link SaiAgent} configured for the given provider/model/mode and agent config.
     *
     * <p>The system prompt is taken from {@link AgentConfig#getPrompt()} (falling back to a
     * built-in default) and is augmented with the sanitised name of the current working directory
     * so the agent has implicit path context. Control characters and path separators in the
     * directory name are replaced with {@code _} to prevent prompt-injection via malicious paths.
     *
     * <p>Effective model settings are resolved through {@link SettingsResolver} using the
     * provider → model → mode hierarchy from {@code settings.yaml}, merged with the persona's
     * inline tuning. If the resolver returns no settings, the agent config's
     * {@code modelSettings}/{@code modelOptions} (or framework defaults) are used.
     *
     * @param provider  the provider name (e.g. {@code "copilot"}, {@code "openai"})
     * @param modelName the bare model name (without provider prefix, e.g. {@code "claude-haiku-4.5"})
     * @param mode      the mode name (may be {@code null})
     * @param config    the agent configuration containing prompt, skills, tools, and model settings
     * @return a fully initialised {@link SaiAgent} ready to receive tool registrations and queries
     */
    public SaiAgent createAgent(String provider, String modelName, @Nullable String mode, AgentConfig config) {
        final var resolved = SettingsResolver.resolve(provider,
                                                      modelName,
                                                      mode,
                                                      settingsConfig,
                                                      config.getTuning());

        final var modelSettings = Objects.requireNonNullElseGet(resolved.getModelSettings(),
                                                                () -> Objects.requireNonNullElseGet(
                                                                                                    config.getModelSettings(),
                                                                                                    this::defaultModelSettings))
                .withParallelToolCalls(false); //To keep context usage and console output sane
        final var modelOptions = Objects.requireNonNullElse(resolved.getModelOptions(),
                                                            Objects.requireNonNullElse(config.getModelOptions(),
                                                                                       SimpleOpenAIModelOptions.DEFAULT));

        final var effectiveProviderFactory = resolveProviderFactory(config, resolved.getTuning());

        final var agentSetup = AgentSetup.builder()
                .executorService(executorService)
                .mapper(mapper)
                .eventBus(eventBus)
                .modelSettings(modelSettings)
                .model(new SimpleOpenAIModel<>(modelName,
                                               effectiveProviderFactory,
                                               mapper,
                                               modelOptions))
                .outputGenerationMode(config.getOutputGenerationMode())
                .autoCompactionSetup(AutoCompactionSetup.builder()
                        .compactionTriggerThresholdPercentage(50)
                        .build())
                .build();
        final var systemPrompt = Objects.requireNonNullElse(config.getPrompt(), DEFAULT_SYSTEM_PROMPT);
        final var rawCwdName = Paths.get("").toAbsolutePath().getFileName();
        final var safeCwdName = rawCwdName != null
                ? rawCwdName.toString().replaceAll("[\\p{Cntrl}/\\\\]", "_")
                : "_tmp";
        final var cwd = "\nCurrent working directory: " + safeCwdName + "\n";
        final var saiAgent = new SaiAgent(config.getName(),
                                          config,
                                          settings,
                                          agentSetup,
                                          systemPrompt + cwd,
                                          sessionExtensions,
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

    /**
     * Resolves the {@link ChatCompletionServiceFactory} to use for this agent.
     *
     * <p>Always wraps the shared {@link OkHttpClient} with a
     * {@link ReasoningNormalizationInterceptor} so that reasoning/thinking fields
     * from different providers are normalised to {@code reasoning_content}.
     *
     * <p>If the agent config additionally contains {@code requestTransforms}, a
     * {@link RequestTransformInterceptor} is also added, and a fresh
     * {@link ConfigurableProviderFactory} is created.
     */
    private ChatCompletionServiceFactory resolveProviderFactory(AgentConfig config,
                                                                ModelTuning tuning) {
        final var requestTransforms = tuning == null ? null : tuning.getRequestTransforms();
        final var hasRequestTransforms = requestTransforms != null && !requestTransforms.isEmpty();

        if (hasRequestTransforms) {
            log.info("Applying {} request transform(s) for agent {}",
                     requestTransforms.size(),
                     config.getAgentId());
        }

        var clientBuilder = httpClient.newBuilder()
                .addInterceptor(new ReasoningNormalizationInterceptor(mapper));

        if (hasRequestTransforms) {
            clientBuilder.addInterceptor(new RequestTransformInterceptor(mapper, requestTransforms));
        }

        var effectiveClient = clientBuilder.build();

        if (modelProviderFactory instanceof ConfigurableProviderFactory factory) {
            return new ConfigurableProviderFactory(factory.getProvider(),
                                                   mapper,
                                                   effectiveClient,
                                                   settingsConfig);
        }

        if (hasRequestTransforms) {
            log.warn("modelProviderFactory is not a ConfigurableProviderFactory; request transforms may not be applied");
        }
        return modelProviderFactory;
    }
}
