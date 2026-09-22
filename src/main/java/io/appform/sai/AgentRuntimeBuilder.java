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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.filesystem.skills.AgentSkillsExtension;
import com.phonepe.sentinelai.models.ChatCompletionServiceFactory;

import io.appform.sai.config.AgentConfigLoader;
import io.appform.sai.config.ModelEntry;
import io.appform.sai.config.ProviderEntry;
import io.appform.sai.config.SettingsConfig;
import io.appform.sai.config.SettingsConfigLoader;
import io.appform.sai.repl.CommandProcessor;
import io.appform.sai.term.Printer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

/**
 * Builds the pieces of an agent runtime: settings, persona config, model factory,
 * skills extension, and the command processor.
 *
 * <p>Each method is stateless. CLI flag values are passed in as parameters, so one
 * builder instance serves any number of builds.
 */
@Slf4j
public final class AgentRuntimeBuilder {

    /**
     * User agent that identifies SAI to providers. Some providers ask clients
     * to identify with their own agent name instead of a generic
     * HTTP-library name.
     */
    private static final String USER_AGENT = "sai/"
            + Objects.requireNonNullElse(
                                         AgentRuntimeBuilder.class.getPackage().getImplementationVersion(),
                                         "dev");

    private AgentRuntimeBuilder() {
        // Utility class
    }

    /**
     * Builds the skills extension from the {@code --skill} flag or the persona's skill
     * directories. Single-skill mode disables discovery.
     *
     * @param settings    current settings (config dir supplies the default skills path)
     * @param agentConfig persona config (may carry skill directories and names)
     * @param skill       value of the {@code --skill} flag (may be {@code null})
     * @return the configured skills extension
     * @throws IOException if the default skills directory cannot be created
     */
    public static AgentSkillsExtension<String, String, SaiAgent> buildAgentSkillsExtension(
                                                                                           final Settings settings,
                                                                                           final AgentConfig agentConfig,
                                                                                           final String skill)
            throws IOException {
        if (!Strings.isNullOrEmpty(skill)) {
            // Single skill specified: load only this one and disable discovery.
            return AgentSkillsExtension.<String, String, SaiAgent>withSingleSkill()
                    .baseDir(Paths.get(settings.getConfigDir(), "skills").toString())
                    .singleSkill(skill)
                    .build();
        }
        else {
            var skillDirs = agentConfig.getSkillDirectories();
            if (skillDirs == null || skillDirs.isEmpty()) {
                final var path = Paths.get(settings.getConfigDir(), "skills");
                Files.createDirectories(path);
                skillDirs = List.of(path.toString());
            }
            var skillNames = Objects.requireNonNullElseGet(agentConfig.getSkillNames(), List::<String>of);
            return AgentSkillsExtension.<String, String, SaiAgent>withMultipleSkills()
                    .baseDir(Paths.get(settings.getConfigDir(), "skills").toString())
                    .skillsDirectories(skillDirs)
                    .skillsToLoad(skillNames)
                    .build();
        }
    }

    /**
     * Builds the command processor that runs one input at a time against the agent.
     *
     * @param agent    the agent to run inputs against
     * @param settings current settings (session ID is read from here)
     * @param printer  the active printer
     * @return a new command processor
     */
    public static CommandProcessor buildCommandProcessor(final SaiAgent agent,
                                                         final Settings settings,
                                                         final Printer printer) {
        return CommandProcessor.builder()
                .sessionId(settings.getSessionId())
                .agent(agent)
                .printer(printer)
                .build();
    }

    /**
     * Constructs the {@link Settings} object for this invocation, applying CLI overrides and
     * routing to a temporary data directory when a one-shot {@code effectiveInput} is provided.
     *
     * @param cliDataDir         value of the {@code --data-dir} flag (may be {@code null})
     * @param cliConfigDir       value of the {@code --config-dir} flag (may be {@code null})
     * @param cliDebug           value of the {@code --debug} flag
     * @param cliHeadless        value of the {@code --headless} flag
     * @param effectiveSessionId the resolved session ID to embed in settings
     * @param effectiveInput     the resolved input string (may be {@code null} for interactive mode)
     * @return a fully-built {@code Settings} instance
     * @throws IOException if a temporary data directory cannot be created
     */
    public static Settings buildSettings(final String cliDataDir,
                                         final String cliConfigDir,
                                         final boolean cliDebug,
                                         final boolean cliHeadless,
                                         final String effectiveSessionId,
                                         final String effectiveInput) throws IOException {
        final var settingsBuilder = Settings.builder()
                .sessionId(effectiveSessionId)
                .debug(cliDebug)
                .headless(cliHeadless || !Strings.isNullOrEmpty(effectiveInput))
                .noSession(!Strings.isNullOrEmpty(effectiveInput));
        if (!Strings.isNullOrEmpty(cliConfigDir)) {
            settingsBuilder.configDir(cliConfigDir);
        }
        if (Strings.isNullOrEmpty(effectiveInput)) {
            if (!Strings.isNullOrEmpty(cliDataDir)) {
                settingsBuilder.dataDir(cliDataDir);
            }
        }
        else {
            // If input is provided (via --input or piped stdin), we don't care about session persistence,
            // so we can skip setting up data dir. However we do care about compaction etc so we provide
            // the session extension a temporary directory
            final var tempDataDir = Files.createTempDirectory("sai-data-")
                    .toAbsolutePath()
                    .normalize()
                    .toString();
            settingsBuilder.dataDir(tempDataDir);
        }
        return settingsBuilder.build();
    }

    /**
     * Loads {@code settings.yaml} and logs a summary.
     *
     * @param configDir the config directory
     * @param mapper    the JSON mapper (used for debug pretty-printing)
     * @return the loaded settings config
     */
    public static SettingsConfig loadSettings(final String configDir, final ObjectMapper mapper) {
        final var settingsConfig = SettingsConfigLoader.load(configDir);
        if (log.isDebugEnabled()) {
            try {
                log.debug("Loaded settings config: {}",
                          mapper.writerWithDefaultPrettyPrinter()
                                  .writeValueAsString(settingsConfig));
            }
            catch (Exception e) {
                log.warn("Failed to pretty-print settings config: {}. Settings: {}", e.getMessage(), settingsConfig);
            }
        }
        else {
            log.info("Loaded settings config with {} providers", settingsConfig.getProviders().size());
        }
        return settingsConfig;
    }

    /**
     * Resolves the persona config. An empty {@code persona} value returns the built-in
     * default agent config.
     *
     * @param persona   value of the {@code --persona} flag (may be {@code null})
     * @param configDir the config directory
     * @param mapper    the JSON mapper
     * @return the resolved agent config
     */
    public static AgentConfig resolveAgentConfig(final String persona,
                                                 final String configDir,
                                                 final ObjectMapper mapper) {
        if (Strings.isNullOrEmpty(persona)) {
            return AgentConfig.builder()
                    .agentId("sai-agent")
                    .name("Sai Agent")
                    .description("An AI agent that can execute tasks and answer questions.")
                    .model("copilot/claude-haiku-4.5")
                    .build();
        }
        final var resolvedPath = AgentConfigLoader.resolvePersonaPath(persona, configDir);
        return AgentConfigLoader.load(resolvedPath, mapper);
    }

    /**
     * Resolves the model pointer into a provider name, model name, mode, HTTP client,
     * and completion-service factory.
     *
     * @param modelPointer   model string in {@code provider/model[/mode]} form
     * @param mapper         the JSON mapper
     * @param settingsConfig loaded settings config
     * @return the resolved model details
     */
    public static ResolvedModelDetails resolveModelFactory(final String modelPointer,
                                                           final ObjectMapper mapper,
                                                           final SettingsConfig settingsConfig) {
        final var parts = modelPointer.split("/", 3);
        Preconditions.checkArgument(parts.length >= 2,
                                    "Model name must be in the format 'provider/model[/mode]'. Provided: "
                                            + modelPointer);
        final var provider = parts[0].toLowerCase();
        final var modelName = parts[1];
        final var mode = parts.length == 3 ? parts[2] : null;
        final var providers = Objects.requireNonNullElseGet(settingsConfig.getProviders(),
                                                            Map::<String, ProviderEntry>of);
        if (log.isDebugEnabled()) {
            log.debug("Available model providers: {}", providers);
            providers
                    .forEach((key, config) -> Objects.requireNonNullElseGet(config.getModels(),
                                                                            Map::<String, ModelEntry>of)
                            .keySet()
                            .forEach(m -> log.info("Loaded config for: {}, Model: {}", key, m)));
        }
        log.debug("Loaded settings config: {}", settingsConfig);
        log.info("Using model provider: {}, model name: {}, mode: {}", provider, modelName, mode);
        final var okHttpClient = buildOkHttpClient();
        return new ResolvedModelDetails(provider,
                                        modelName,
                                        mode,
                                        okHttpClient,
                                        new ConfigurableProviderFactory(provider,
                                                                        mapper,
                                                                        okHttpClient,
                                                                        settingsConfig));
    }

    /**
     * Builds the shared {@link OkHttpClient} with project-standard timeouts and
     * a {@code User-Agent} that identifies SAI to providers.
     *
     * <p>Some providers (for example OpenCode Go) ask clients to identify with
     * their own user agent instead of a generic HTTP-library name.
     *
     * @return a configured {@code OkHttpClient}
     */
    private static OkHttpClient buildOkHttpClient() {
        return new OkHttpClient.Builder()
                .readTimeout(Duration.ofSeconds(300))
                .callTimeout(Duration.ofSeconds(300))
                .connectTimeout(Duration.ofSeconds(10))
                .addInterceptor(chain -> chain.proceed(
                                                       chain.request().newBuilder()
                                                               .header("User-Agent", USER_AGENT)
                                                               .build()))
                .build();
    }

    /**
     * Result of model resolution: provider, model name, mode, HTTP client, and the
     * completion-service factory that maps them to a model implementation.
     */
    public record ResolvedModelDetails(
            String provider,
            String modelName,
            String mode,
            OkHttpClient httpClient,
            ChatCompletionServiceFactory factory
    ) {
    }
}
