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

import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.utils.EnvLoader;
import com.phonepe.sentinelai.models.ConfiguredModel;
import com.phonepe.sentinelai.models.openai.ChatCompletionsProtocol;
import com.phonepe.sentinelai.models.provider.ExtraHeadersRequestTransformer;
import com.phonepe.sentinelai.models.provider.HeaderAuth;
import com.phonepe.sentinelai.models.provider.Provider;
import com.phonepe.sentinelai.models.provider.RequestTransformer;
import com.phonepe.sentinelai.models.wire.WireLoggingMode;

import io.appform.sai.config.ProviderEntry;
import io.appform.sai.config.SettingsConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

@Slf4j
public class ConfigurableProviderFactory implements ModelFactory {

    private static final Map<String, String> COPILOT_PROXY_HEADERS = Map.of(
                                                                            "Accept",
                                                                            "application/vnd.github+json",
                                                                            "X-GitHub-Api-Version",
                                                                            "2022-11-28");

    @UtilityClass
    public static class Providers {
        public static final String AZURE = "azure";
        public static final String OPENAI = "openai";
        public static final String COPILOT_PROXY = "copilot-proxy";
        public static final String COPILOT = "copilot";
    }

    @Getter
    private final String provider;
    private final OkHttpClient okHttpClient;
    private final SettingsConfig settingsConfig;
    private final List<RequestTransformer> extraRequestTransformers;

    private CopilotDirectProvider copilotDirectProvider = null;

    /**
     * Constructs a factory with a loaded {@link SettingsConfig} for config-driven providers.
     *
     * @param provider       the provider name (e.g. "openai", "azure", "copilot")
     * @param okHttpClient   the shared {@link OkHttpClient}
     * @param settingsConfig the loaded settings.yaml configuration (may be empty for env-var fallback)
     */
    public ConfigurableProviderFactory(String provider,
                                       OkHttpClient okHttpClient,
                                       SettingsConfig settingsConfig) {
        this(provider, okHttpClient, settingsConfig, List.of());
    }

    /**
     * Constructs a factory that attaches extra model-level request transformers to every
     * model it builds. The agent layer uses this for jolt transforms, session affinity
     * and extra headers.
     *
     * @param provider                 the provider name (e.g. "openai", "azure", "copilot")
     * @param okHttpClient             the shared {@link OkHttpClient}
     * @param settingsConfig           the loaded settings.yaml configuration (may be empty for env-var fallback)
     * @param extraRequestTransformers model-level request transformers applied to every built model
     */
    public ConfigurableProviderFactory(String provider,
                                       OkHttpClient okHttpClient,
                                       SettingsConfig settingsConfig,
                                       List<RequestTransformer> extraRequestTransformers) {
        this.provider = provider;
        this.okHttpClient = okHttpClient;
        this.settingsConfig = settingsConfig != null ? settingsConfig : SettingsConfig.builder().build();
        this.extraRequestTransformers = List.copyOf(extraRequestTransformers);
    }

    @Override
    public Model model(String modelName, String apiModelId) {
        return model(modelName, apiModelId, null, null);
    }

    @Override
    public Model model(String modelName, String apiModelId, String protocolOverride) {
        return model(modelName, apiModelId, protocolOverride, null);
    }

    @Override
    public Model model(String modelName, String apiModelId, String protocolOverride, WireLoggingMode wireLogging) {
        // Check settings.yaml first. If a config entry exists, build from it. If not,
        // fall back to env-var behavior for backward compatibility.
        final var providerEntry = settingsConfig.getProvider(provider).orElse(null);
        if (providerEntry != null) {
            return buildFromConfig(providerEntry, modelName, apiModelId, protocolOverride, wireLogging);
        }
        // No config entry — fall back to env-var behavior for known built-in provider types.
        return switch (provider) {
            case Providers.COPILOT -> copilotDirectModel(modelName, apiModelId, protocolOverride, wireLogging);
            case Providers.AZURE, Providers.OPENAI, Providers.COPILOT_PROXY -> buildEnvVarModel(apiModelId,
                                                                                                wireLogging);
            default -> throw new IllegalArgumentException("Unsupported provider: " + provider
                    + ". Add it to settings.yaml or use a built-in provider (openai, azure, copilot, copilot-proxy).");
        };
    }

    private Model azureModel(String modelName, String apiModelId) {
        return azureModel(modelName, apiModelId, null);
    }

    /**
     * Builds Azure {@link Model} from resolved connection values.
     *
     * <p>Azure serves the plain OpenAI chat-completions path with an
     * {@code api-version} query parameter that a request transformer appends.
     *
     * @param endpoint    the Azure endpoint / base URL
     * @param apiKey      the Azure API key
     * @param apiVersion  the Azure API version
     * @param apiModelId  the model id sent on the wire
     * @param wireLogging the wire payload logging mode, or null for the default
     * @return the built model
     */
    private Model azureModel(String endpoint,
                             String apiKey,
                             String apiVersion,
                             String apiModelId,
                             WireLoggingMode wireLogging) {
        final var transformers = new ArrayList<RequestTransformer>(extraRequestTransformers);
        transformers.add(new ApiVersionRequestTransformer(apiVersion));
        return baseModel(apiModelId,
                         Provider.builder()
                                 .baseUrl(endpoint)
                                 .protocol(new ChatCompletionsProtocol())
                                 .endpointPrefix("/openai/deployments/" + apiModelId)
                                 .auth(HeaderAuth.bearer(apiKey))
                                 .build(),
                         transformers,
                         wireLogging);
    }

    private Model azureModel(String modelName, String apiModelId, WireLoggingMode wireLogging) {
        log.debug("Creating Azure model for model: {}", modelName);
        final var endpoint = readEnv("AZURE_ENDPOINT",
                                     "AZURE_ENDPOINT environment variable must be set");
        final var apiKey = readEnv("AZURE_API_KEY",
                                   "AZURE_API_KEY environment variable must be set");
        final var apiVersion = EnvLoader.readEnv("AZURE_API_VERSION", "2024-10-21");
        return azureModel(endpoint, apiKey, apiVersion, apiModelId, wireLogging);
    }

    private Model azureModelFromConfig(ProviderEntry entry, String modelName, String apiModelId) {
        return azureModelFromConfig(entry, modelName, apiModelId, null);
    }

    private Model azureModelFromConfig(ProviderEntry entry,
                                       String modelName,
                                       String apiModelId,
                                       WireLoggingMode wireLogging) {
        log.debug("Creating Azure model from config for provider: {}, model: {}", provider, modelName);
        final var endpoint = resolveValue(entry.getEndpoint(),
                                          "AZURE_ENDPOINT",
                                          "Azure endpoint must be set in settings.yaml or AZURE_ENDPOINT env var");
        final var apiKey = resolveValue(entry.getApiKey(),
                                        "AZURE_API_KEY",
                                        "Azure API key must be set in settings.yaml or AZURE_API_KEY env var");
        final var apiVersion = resolveValueWithDefault(entry.getApiVersion(), "AZURE_API_VERSION", "2024-10-21");
        return azureModel(endpoint, apiKey, apiVersion, apiModelId, wireLogging);
    }

    private Model baseModel(String apiModelId, Provider provider, List<RequestTransformer> transformers) {
        return baseModel(apiModelId, provider, transformers, null);
    }

    private Model baseModel(String apiModelId,
                            Provider provider,
                            List<RequestTransformer> transformers,
                            WireLoggingMode wireLogging) {
        return ConfiguredModel.builder()
                .modelName(apiModelId)
                .modelId(apiModelId)
                .provider(provider)
                .httpClient(okHttpClient)
                .requestTransformers(transformers)
                .wireLogging(wireLogging)
                .build();
    }

    /**
     * Builds the env-var-backed model for providers that ignore protocol overrides, with
     * a wire logging mode.
     *
     * @param apiModelId  the model id sent on the wire
     * @param wireLogging the wire payload logging mode, or null for the default
     * @return the built model
     */
    private Model buildEnvVarModel(String apiModelId, WireLoggingMode wireLogging) {
        return switch (provider) {
            case Providers.AZURE -> azureModel(apiModelId, apiModelId, wireLogging);
            case Providers.OPENAI -> openAIModel(apiModelId, wireLogging);
            case Providers.COPILOT_PROXY -> copilotProxyModel(apiModelId, wireLogging);
            default -> throw new IllegalArgumentException("Unsupported provider: " + provider);
        };
    }

    /**
     * Builds a {@link Model} from a {@link ProviderEntry} in settings.yaml.
     *
     * @param entry            the provider configuration entry
     * @param modelName        the model name
     * @param apiModelId       the model id sent on the wire
     * @param protocolOverride wire protocol name, or null for the provider default
     * @param wireLogging      the wire payload logging mode, or null for the default
     * @return the built model
     * @throws IllegalArgumentException if the provider type is unsupported
     */
    private Model buildFromConfig(ProviderEntry entry,
                                  String modelName,
                                  String apiModelId,
                                  String protocolOverride,
                                  WireLoggingMode wireLogging) {
        var type = entry.getType() == null && Providers.COPILOT.equals(provider)
                ? Providers.COPILOT
                : entry.getType();
        if (type == null) {
            throw new IllegalArgumentException("Provider '" + provider + "' in settings.yaml has no 'type' field. "
                    + "Set type to 'openai' or 'azure'.");
        }
        log.info("Building model for provider '{}' (type: '{}') from settings.yaml for model: {}",
                 provider,
                 type,
                 modelName);
        return switch (type.toLowerCase()) {
            case Providers.COPILOT -> copilotDirectModel(modelName, apiModelId, protocolOverride, wireLogging);
            case Providers.OPENAI -> openAIModelFromConfig(entry, modelName, apiModelId, wireLogging);
            case Providers.AZURE -> azureModelFromConfig(entry, modelName, apiModelId, wireLogging);
            default -> throw new IllegalArgumentException(
                                                          "Unsupported provider type '" + type + "' for provider '"
                                                                  + provider + "'. Use 'openai' or 'azure'.");
        };
    }

    private Model copilotDirectModel(String modelName,
                                     String apiModelId,
                                     String protocolOverride,
                                     WireLoggingMode wireLogging) {
        log.debug("Creating Copilot Direct model for model: {}", modelName);
        if (copilotDirectProvider == null) {
            try {
                copilotDirectProvider = new CopilotDirectProvider(okHttpClient);
            }
            catch (java.io.IOException e) {
                throw new IllegalStateException("Failed to initialise Copilot direct provider", e);
            }
        }
        return copilotDirectProvider.model(modelName, apiModelId, protocolOverride, wireLogging);
    }

    private Model copilotProxyModel(String apiModelId) {
        return copilotProxyModel(apiModelId, null);
    }

    /**
     * Builds the env-var-backed Copilot proxy model with a wire logging mode.
     *
     * @param apiModelId  the model id sent on the wire
     * @param wireLogging the wire payload logging mode, or null for the default
     * @return the built model
     */
    private Model copilotProxyModel(String apiModelId, WireLoggingMode wireLogging) {
        log.debug("Creating Copilot Proxy model for model: {}", apiModelId);
        final var endpoint = EnvLoader.readEnv("COPILOT_PROXY_ENDPOINT", "http://localhost:4141");
        return baseModel(apiModelId,
                         Provider.builder()
                                 .baseUrl(endpoint)
                                 .protocol(new ChatCompletionsProtocol())
                                 .auth(HeaderAuth.of(COPILOT_PROXY_HEADERS))
                                 .build(),
                         extraRequestTransformers,
                         wireLogging);
    }

    private Model openAIModel(String apiModelId) {
        return openAIModel(apiModelId, null);
    }

    /**
     * Builds OpenAI-compatible {@link Model} from resolved connection values.
     *
     * <p>Organization, project and extra headers are attached through an
     * {@link ExtraHeadersRequestTransformer}.
     *
     * @param endpoint       the API base URL
     * @param apiKey         the API key
     * @param organizationId the OpenAI organization ID (may be null)
     * @param projectId      the OpenAI project ID (may be null)
     * @param extraHeaders   extra headers to inject into every request (may be null or empty)
     * @param apiModelId     the model id sent on the wire
     * @param wireLogging    the wire payload logging mode, or null for the default
     * @return the built model
     */
    private Model openAIModel(String endpoint,
                              String apiKey,
                              String organizationId,
                              String projectId,
                              Map<String, String> extraHeaders,
                              String apiModelId,
                              WireLoggingMode wireLogging) {
        final var headers = new LinkedHashMap<String, String>();
        if (!Strings.isNullOrEmpty(organizationId)) {
            headers.put("OpenAI-Organization", organizationId);
        }
        if (!Strings.isNullOrEmpty(projectId)) {
            headers.put("OpenAI-Project", projectId);
        }
        if (extraHeaders != null) {
            headers.putAll(extraHeaders);
        }
        return baseModel(apiModelId,
                         Provider.builder()
                                 .baseUrl(endpoint)
                                 .protocol(new ChatCompletionsProtocol())
                                 .auth(HeaderAuth.bearer(apiKey))
                                 .build(),
                         withHeaderTransformer(headers),
                         wireLogging);
    }

    private Model openAIModel(String apiModelId, WireLoggingMode wireLogging) {
        log.debug("Creating OpenAI model for model: {}", apiModelId);
        final var apiKey = readEnv("OPENAI_API_KEY",
                                   "OPENAI_API_KEY environment variable must be set");
        final var endpoint = EnvLoader.readEnv("OPENAI_ENDPOINT", "https://api.openai.com");
        final var organizationId = EnvLoader.readEnv("OPENAI_ORGANIZATION", null);
        final var projectId = EnvLoader.readEnv("OPENAI_PROJECT_ID", null);
        final var extraHeaders = EnvLoader.readEnv("OPENAI_EXTRA_HEADERS", null);
        log.debug("Using OpenAI endpoint: {}", endpoint);
        return openAIModel(endpoint,
                           apiKey,
                           organizationId,
                           projectId,
                           parseExtraHeaders(extraHeaders),
                           apiModelId,
                           wireLogging);
    }

    private Model openAIModelFromConfig(ProviderEntry entry, String modelName, String apiModelId) {
        return openAIModelFromConfig(entry, modelName, apiModelId, null);
    }

    private Model openAIModelFromConfig(ProviderEntry entry,
                                        String modelName,
                                        String apiModelId,
                                        WireLoggingMode wireLogging) {
        log.debug("Creating OpenAI-compatible model from config for provider: {}, model: {}",
                  provider,
                  modelName);
        final var endpoint = resolveValue(entry.getEndpoint(),
                                          "OPENAI_ENDPOINT",
                                          "OpenAI endpoint must be set in settings.yaml or OPENAI_ENDPOINT env var");
        final var apiKey = resolveValue(entry.getApiKey(),
                                        "OPENAI_API_KEY",
                                        "API key must be set in settings.yaml or OPENAI_API_KEY env var");
        final var organizationId = resolveValueOrNull(entry.getOrganizationId(), "OPENAI_ORGANIZATION");
        final var projectId = resolveValueOrNull(entry.getProjectId(), "OPENAI_PROJECT_ID");
        log.debug("Using OpenAI endpoint: {} for provider: {}", endpoint, provider);
        return openAIModel(endpoint,
                           apiKey,
                           organizationId,
                           projectId,
                           entry.getExtraHeaders(),
                           apiModelId,
                           wireLogging);
    }

    /**
     * Parses a comma-delimited "Key:Value,Key2:Value2" env-var string into a header map.
     *
     * @param extraHeaders comma-delimited header string, or null
     * @return the parsed headers, or an empty map if the string is null or empty
     */
    private Map<String, String> parseExtraHeaders(String extraHeaders) {
        if (Strings.isNullOrEmpty(extraHeaders)) {
            return Map.of();
        }
        final var headers = new LinkedHashMap<String, String>();
        for (String header : extraHeaders.split(",")) {
            String[] parts = header.split(":", 2);
            if (parts.length == 2) {
                headers.put(parts[0].trim(), parts[1].trim());
            }
        }
        return headers;
    }

    private String readEnv(String key, String errorMessage) {
        return EnvLoader.readEnv(key)
                .orElseThrow(() -> new IllegalArgumentException(errorMessage));
    }

    /**
     * Resolves a config value: use the config entry if non-null/non-empty, otherwise fall back
     * to the env var. If the env var is also missing and no default is provided, throws.
     *
     * @param configValue  the value from settings.yaml (may be null)
     * @param envVar       the env var name for fallback
     * @param errorMessage the error message if both config and env var are missing
     * @return the resolved value
     */
    private String resolveValue(String configValue, String envVar, String errorMessage) {
        if (!Strings.isNullOrEmpty(configValue)) {
            return configValue;
        }
        return readEnv(envVar, errorMessage);
    }

    /**
     * Resolves a config value that may be null: use the config entry if non-null/non-empty,
     * otherwise fall back to the env var, otherwise return null.
     *
     * @param configValue the value from settings.yaml (may be null)
     * @param envVar      the env var name for fallback
     * @return the resolved value, or null
     */
    private String resolveValueOrNull(String configValue, String envVar) {
        if (!Strings.isNullOrEmpty(configValue)) {
            return configValue;
        }
        return EnvLoader.readEnv(envVar, null);
    }

    /**
     * Resolves a config value with a default: use the config entry if non-null/non-empty,
     * otherwise fall back to the env var, otherwise use the default.
     *
     * @param configValue  the value from settings.yaml (may be null)
     * @param envVar       the env var name for fallback
     * @param defaultValue the default value if both are missing
     * @return the resolved value
     */
    private String resolveValueWithDefault(String configValue, String envVar, String defaultValue) {
        if (!Strings.isNullOrEmpty(configValue)) {
            return configValue;
        }
        return EnvLoader.readEnv(envVar, defaultValue);
    }

    private List<RequestTransformer> withHeaderTransformer(Map<String, String> headers) {
        final var transformers = new ArrayList<RequestTransformer>(extraRequestTransformers);
        if (!headers.isEmpty()) {
            transformers.add(ExtraHeadersRequestTransformer.builder()
                    .headers(headers)
                    .build());
        }
        return transformers;
    }
}
