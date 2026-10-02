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
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelAttributes;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.tools.loopdetection.ToolLoopProtectionSetup;
import com.phonepe.sentinelai.models.ConfiguredModel;
import com.phonepe.sentinelai.models.ModelOptions;
import com.phonepe.sentinelai.models.provider.ExtraHeadersRequestTransformer;
import com.phonepe.sentinelai.models.provider.JoltRequestTransformer;
import com.phonepe.sentinelai.models.provider.JoltTransform;
import com.phonepe.sentinelai.models.provider.RequestTransformer;
import com.phonepe.sentinelai.models.provider.SessionIdInjectionTransformer;
import com.phonepe.sentinelai.models.wire.WireLoggingMode;
import com.phonepe.sentinelai.toolbox.mcp.ComposingMCPToolBox;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolBox;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpToolReaders.ConfiguredHttpTool;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.InMemoryHttpToolSource;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.TemplatizedHttpTool;

import io.appform.sai.AgentConfig;
import io.appform.sai.ConfigurableProviderFactory;
import io.appform.sai.ModelFactory;
import io.appform.sai.SaiAgent;
import io.appform.sai.Settings;
import io.appform.sai.config.ModelTuning;
import io.appform.sai.config.ProviderEntry;
import io.appform.sai.config.SessionAffinityConfig;
import io.appform.sai.config.SettingsConfig;
import io.appform.sai.config.SettingsResolver;
import io.appform.sai.transform.MdcSessionInterceptor;

import java.util.ArrayList;
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
 * <p>Each call to {@link #createAgent(String, String, String, AgentConfig)} creates a fresh agent
 * bound to the provided model name and {@link AgentConfig}, wires all session and skill extensions,
 * and registers any configured MCP or HTTP toolboxes. {@link SaiAgent} augments the system prompt
 * itself (terminal hints, working directory, scratch directory).
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
    private final ModelFactory modelProviderFactory;
    private final ObjectMapper mapper;
    private final EventBus eventBus;
    private final OkHttpClient httpClient;
    private final SettingsConfig settingsConfig;

    /**
     * Creates a new {@link SaiAgent} configured for the given provider/model/mode and agent config.
     *
     * <p>The system prompt is taken from {@link AgentConfig#getPrompt()} (falling back to a
     * built-in default). {@link SaiAgent} augments it with terminal hints and the current
     * working directory.
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
                                                                                       ModelOptions.DEFAULT));

        final var effectiveProviderFactory = resolveProviderFactory(config, resolved.getTuning());
        final var protocolOverride = resolveProtocolOverride(resolved.getTuning());

        // If the ModelEntry defines an effectiveModelId (e.g. "moonshotai/kimi-k3" for an
        // OpenRouter model keyed as "kimi-k3"), use it as the API model ID; otherwise fall back
        // to the bare model name from the CLI string.
        final var apiModelId = Objects.requireNonNullElse(resolved.getEffectiveModelId(), modelName);
        if (!apiModelId.equals(modelName)) {
            log.info("Overriding API model ID: '{}' -> '{}'", modelName, apiModelId);
        }
        final var agentSetup = AgentSetup.builder()
                .executorService(executorService)
                .mapper(mapper)
                .eventBus(eventBus)
                .modelSettings(modelSettings)
                .outputGenerationMode(config.getOutputGenerationMode())
                .model(buildModel(apiModelId,
                                  effectiveProviderFactory,
                                  modelOptions,
                                  protocolOverride,
                                  settings.isDebug() ? WireLoggingMode.FRAMES : null))
                .autoCompactionSetup(AutoCompactionSetup.builder()
                        .compactionTriggerThresholdPercentage(resolveCompactionTriggerThreshold(resolved.getTuning()))
                        .skipToolMessages(false) //Keep tool i/o in summaries for now; flip when validated
                        .build())
                .toolLoopProtectionSetup(ToolLoopProtectionSetup.builder()
                        .maxToolCalls(config.getMaxToolCalls())
                        .maxToolRounds(config.getMaxToolRounds())
                        .build())
                .build();
        final var systemPrompt = Objects.requireNonNullElse(config.getPrompt(), DEFAULT_SYSTEM_PROMPT);
        final var saiAgent = new SaiAgent(config.getName(),
                                          config,
                                          settings,
                                          agentSetup,
                                          systemPrompt,
                                          sessionExtensions,
                                          Map.of());
        registerMCPTools(saiAgent, config);
        registerHttpTools(saiAgent, config);
        return saiAgent;
    }

    /**
     * Builds the agent's model from the provider factory and model options.
     *
     * <p>When the resolved tuning carries request transforms, session affinity or extra
     * headers, the provider factory applies them as model-level request transformers.
     * The resolved protocol override from the tuning is passed through so the provider
     * can apply it when it builds the model.
     *
     * @param apiModelId       the model id to send on the wire
     * @param providerFactory  the provider factory for this agent
     * @param modelOptions     the resolved model options
     * @param protocolOverride the protocol override name from the tuning (may be null)
     * @param wireLogging      the wire payload logging mode from debug mode, or null for the default
     * @return the built model
     */
    private Model buildModel(String apiModelId,
                             ModelFactory providerFactory,
                             ModelOptions modelOptions,
                             @Nullable String protocolOverride,
                             @Nullable WireLoggingMode wireLogging) {
        final var model = (ConfiguredModel) providerFactory.model(apiModelId,
                                                                  apiModelId,
                                                                  protocolOverride,
                                                                  wireLogging);
        if (model.getModelOptions() == modelOptions) {
            return model;
        }
        return ConfiguredModel.builder()
                .modelName(model.getModelName())
                .modelId(model.getModelId())
                .provider(model.getProvider())
                .protocol(model.getProtocol())
                .httpClient(model.getHttpClient())
                .modelOptions(modelOptions)
                .tokenCounter(model.getTokenCounter())
                .requestTransformers(model.getRequestTransformers())
                .requestRetryPolicy(model.getRequestRetryPolicy())
                .wireLogging(wireLogging)
                .build();
    }

    private TemplatizedHttpTool createTool(ConfiguredHttpTool tool) {
        return new TemplatizedHttpTool(tool.metadata(),
                                       tool.definition(),
                                       tool.transformer());
    }

    private ModelSettings defaultModelSettings() {
        return ModelSettings.builder()
                .parallelToolCalls(false)
                .modelAttributes(ModelAttributes.builder()
                        .contextWindowSize(ModelAttributes.DEFAULT_WINDOW_SIZE)
                        .encodingType(EncodingType.O200K_BASE)
                        .build())
                .build();
    }

    /**
     * Resolves the extra headers for a provider from settings.yaml.
     *
     * @param provider the provider name
     * @return the extra headers, or an empty map when none are configured
     */
    private Map<String, String> extraHeaders(String provider) {
        return settingsConfig.getProvider(provider)
                .map(ProviderEntry::getExtraHeaders)
                .map(headers -> headers == null ? Map.<String, String>of() : headers)
                .orElseGet(() -> Map.of());
    }

    /**
     * Converts the tuning's request transforms to {@link JoltTransform} instances.
     *
     * @param tuning the resolved tuning (may be null)
     * @return the jolt transforms, or an empty list when none are configured
     */
    private List<JoltTransform> joltTransforms(@Nullable ModelTuning tuning) {
        if (tuning == null || tuning.getRequestTransforms() == null) {
            return List.of();
        }
        return tuning.getRequestTransforms();
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
     * Resolves the auto-compaction trigger threshold for an agent.
     *
     * <p>Uses the {@code compactionTriggerThresholdPercentage} from the resolved
     * {@link ModelTuning} when set. Falls back to {@code 50} otherwise. A value of {@code 0}
     * means compact on every run.
     */
    private int resolveCompactionTriggerThreshold(@Nullable ModelTuning tuning) {
        if (tuning != null && tuning.getCompactionTriggerThresholdPercentage() != null) {
            return tuning.getCompactionTriggerThresholdPercentage();
        }
        return 50;
    }

    /**
     * Resolves the wire protocol override name from the tuning.
     *
     * @param tuning the resolved tuning (may be null)
     * @return the protocol name, or null when the provider default applies
     */
    @Nullable
    private String resolveProtocolOverride(@Nullable ModelTuning tuning) {
        if (tuning == null || tuning.getProtocol() == null || tuning.getProtocol().isBlank()) {
            return null;
        }
        log.info("Protocol override from tuning: {}", tuning.getProtocol());
        return tuning.getProtocol();
    }

    /**
     * Resolves the {@link ModelFactory} to use for this agent.
     *
     * <p>When the agent needs request transforms, session affinity or extra headers, they are
     * applied as model-level request transformers on the built {@link ConfiguredModel}. A
     * fresh {@link ConfigurableProviderFactory} is created so the base factory stays reusable.
     */
    private ModelFactory resolveProviderFactory(AgentConfig config, ModelTuning tuning) {
        if (!(modelProviderFactory instanceof ConfigurableProviderFactory factory)) {
            log.warn("modelProviderFactory is not a ConfigurableProviderFactory; "
                    + "request transforms and session affinity may not be applied");
            return modelProviderFactory;
        }
        final var joltTransforms = joltTransforms(tuning);
        final var sessionAffinity = sessionAffinityConfig(factory.getProvider());
        final var extraHeaders = extraHeaders(factory.getProvider());
        if (joltTransforms.isEmpty() && !sessionAffinity.enabled() && extraHeaders.isEmpty()) {
            return modelProviderFactory;
        }
        // Stamp the session id onto OkHttp threads first so that all
        // interceptor log lines route to the per-session log file.
        final var mdcClient = httpClient.newBuilder()
                .addInterceptor(new MdcSessionInterceptor(settings.getSessionId()))
                .build();
        final var requestTransformers = new ArrayList<RequestTransformer>();
        if (!joltTransforms.isEmpty()) {
            log.info("Applying {} request transform(s) for agent {}",
                     joltTransforms.size(),
                     config.getAgentId());
            requestTransformers.add(JoltRequestTransformer.ofTransforms(joltTransforms));
        }
        if (sessionAffinity.enabled()) {
            log.info("Enabling session affinity for provider {} (header: {}, bodyField: {})",
                     factory.getProvider(),
                     sessionAffinity.getHeader(),
                     sessionAffinity.getBodyField());
            requestTransformers.add(SessionIdInjectionTransformer.builder()
                    .header(sessionAffinity.getHeader())
                    .bodyPath(sessionAffinity.getBodyField() == null
                            ? null
                            : "/" + sessionAffinity.getBodyField())
                    .build());
        }
        if (!extraHeaders.isEmpty()) {
            requestTransformers.add(ExtraHeadersRequestTransformer.builder()
                    .headers(extraHeaders)
                    .build());
        }
        return new ConfigurableProviderFactory(factory.getProvider(),
                                               mdcClient,
                                               settingsConfig,
                                               requestTransformers);
    }

    /**
     * Resolves the {@link ModelFactory} to use for this agent.
     *
     * <p>If the agent config contains {@code requestTransforms}, a
     * {@link RequestTransformInterceptor} is added, and a fresh
     * {@link ConfigurableProviderFactory} is created.
     */
    /**
     * Resolves the opt-in session cache affinity config for a provider.
     *
     * @param provider the provider name
     * @return the session affinity config, or a disabled config when not set
     */
    private SessionAffinityConfig sessionAffinityConfig(String provider) {
        return settingsConfig.getProvider(provider)
                .map(ProviderEntry::getSessionAffinity)
                .filter(SessionAffinityConfig::enabled)
                .orElseGet(() -> SessionAffinityConfig.builder().build());
    }
}
