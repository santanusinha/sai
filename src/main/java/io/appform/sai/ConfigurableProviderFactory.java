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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.utils.EnvLoader;
import com.phonepe.sentinelai.models.ChatCompletionServiceFactory;

import io.appform.sai.config.ProviderEntry;
import io.appform.sai.config.SettingsConfig;
import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.cleverclient.retry.RetryConfig;
import io.github.sashirestela.openai.SimpleOpenAI;
import io.github.sashirestela.openai.SimpleOpenAIAzure;
import io.github.sashirestela.openai.service.ChatCompletionServices;

import java.util.Map;

import lombok.Getter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

@Slf4j
public class ConfigurableProviderFactory implements ChatCompletionServiceFactory {

    private static final RetryConfig RETRY_CONFIG = RetryConfig.builder()
            .maxAttempts(1) // disabling implicit retries by default for tests
            .build();

    @UtilityClass
    public static class Providers {
        public static final String AZURE = "azure";
        public static final String OPENAI = "openai";
        public static final String COPILOT_PROXY = "copilot-proxy";
        public static final String COPILOT = "copilot";
    }

    @Getter
    private final String provider;
    private final ObjectMapper mapper;
    private final OkHttpClient okHttpClient;
    private final SettingsConfig settingsConfig;

    private CopilotDirectProvider copilotDirectProvider = null;

    /**
     * Constructs a factory without a settings config (env-var fallback only).
     *
     * <p>This constructor is retained for backward compatibility and for call sites that
     * do not yet have a loaded {@link SettingsConfig} (e.g.
     * {@link io.appform.sai.agent.AgentFactory#resolveProviderFactory}
     * when creating a transform-injected factory).
     *
     * @param provider     the provider name
     * @param mapper       the shared Jackson {@link ObjectMapper}
     * @param okHttpClient the shared {@link OkHttpClient}
     */
    public ConfigurableProviderFactory(String provider, ObjectMapper mapper, OkHttpClient okHttpClient) {
        this(provider, mapper, okHttpClient, null);
    }

    /**
     * Constructs a factory with a loaded {@link SettingsConfig} for config-driven providers.
     *
     * @param provider       the provider name (e.g. "openai", "azure", "copilot")
     * @param mapper         the shared Jackson {@link ObjectMapper}
     * @param okHttpClient   the shared {@link OkHttpClient}
     * @param settingsConfig the loaded settings.yaml configuration (may be empty for env-var fallback)
     */
    public ConfigurableProviderFactory(String provider,
                                       ObjectMapper mapper,
                                       OkHttpClient okHttpClient,
                                       SettingsConfig settingsConfig) {
        this.provider = provider;
        this.mapper = mapper;
        this.okHttpClient = okHttpClient;
        this.settingsConfig = settingsConfig != null ? settingsConfig : SettingsConfig.builder().build();

        /* if (Providers.COPILOT.equals(provider)) {
            if (this.settingsConfig.getProvider(Providers.COPILOT) != null) {
                log.warn("Provider 'copilot' is listed in settings.yaml but is always handled by the built-in "
                        + "CopilotDirectProvider. The config entry (connection details) is ignored. "
                        + "Model/mode tuning entries may still be used by SettingsResolver.");
            }
            try {
                this.copilotDirectProvider = new CopilotDirectProvider(mapper, okHttpClient, RETRY_CONFIG);
            }
            catch (java.io.IOException e) {
                throw new IllegalStateException("Failed to initialise Copilot direct provider", e);
            }
        } */
    }

    @Override
    public ChatCompletionServices get(String modelName) {
        // Copilot is always handled by the built-in CopilotDirectProvider — never config-driven.
        //       if (Providers.COPILOT.equals(provider)) {
        //           return copilotDirectProvider.get(modelName);
        //       }
        // All other providers (including built-in names like openai/azure/copilot-proxy) check
        // settings.yaml first. If a config entry exists, build from it. If not, fall back to
        // env-var behavior for backward compatibility.
        final var providerEntry = settingsConfig.getProvider(provider);
        if (providerEntry != null) {
            return buildFromConfig(providerEntry, modelName);
        }
        // No config entry — fall back to env-var behavior for known built-in provider types.
        return switch (provider) {
            case Providers.COPILOT -> copilotDirectModel(modelName);
            case Providers.AZURE -> azureModel(modelName);
            case Providers.OPENAI -> openAIModel(modelName);
            case Providers.COPILOT_PROXY -> copilotProxyModel(modelName);
            default -> throw new IllegalArgumentException("Unsupported provider: " + provider
                    + ". Add it to settings.yaml or use a built-in provider (openai, azure, copilot, copilot-proxy).");
        };
    }

    /**
     * Applies extra headers from a comma-delimited env-var string (legacy format).
     *
     * @param client       the base OkHttpClient
     * @param extraHeaders comma-delimited "Key:Value,Key2:Value2" string, or null
     * @return the client with headers injected, or the original client if no headers
     */
    private OkHttpClient applyExtraHeaders(OkHttpClient client, String extraHeaders) {
        if (Strings.isNullOrEmpty(extraHeaders)) {
            return client;
        }
        return client.newBuilder()
                .addInterceptor(chain -> {
                    var requestBuilder = chain.request().newBuilder();
                    for (String header : extraHeaders.split(",")) {
                        String[] parts = header.split(":", 2);
                        if (parts.length == 2) {
                            requestBuilder.addHeader(parts[0].trim(), parts[1].trim());
                            log.debug("Adding extra header to OpenAI request: {}", header);
                        }
                    }
                    return chain.proceed(requestBuilder.build());
                })
                .build();
    }

    /**
     * Applies extra headers from a Map (config-driven format).
     *
     * @param client       the base OkHttpClient
     * @param extraHeaders map of header name to value, or null
     * @return the client with headers injected, or the original client if no headers
     */
    private OkHttpClient applyExtraHeadersFromMap(OkHttpClient client, Map<String, String> extraHeaders) {
        if (extraHeaders == null || extraHeaders.isEmpty()) {
            return client;
        }
        return client.newBuilder()
                .addInterceptor(chain -> {
                    var requestBuilder = chain.request().newBuilder();
                    extraHeaders.forEach((key, value) -> {
                        requestBuilder.addHeader(key, value);
                        log.debug("Adding extra header from config: {}:{}", key, value);
                    });
                    return chain.proceed(requestBuilder.build());
                })
                .build();
    }

    private ChatCompletionServices azureModel(String modelName) {
        log.debug("Creating Azure ChatCompletionServices for model: {}", modelName);
        final var endpoint = readEnv("AZURE_ENDPOINT",
                                     "AZURE_ENDPOINT environment variable must be set");
        final var apiKey = readEnv("AZURE_API_KEY",
                                   "AZURE_API_KEY environment variable must be set");
        return SimpleOpenAIAzure.builder()
                .baseUrl(endpoint)
                .apiKey(apiKey)
                .apiVersion(EnvLoader.readEnv("AZURE_API_VERSION", "2024-10-21"))
                .objectMapper(mapper)
                .clientAdapter(new OkHttpClientAdapter(okHttpClient))
                .retryConfig(RETRY_CONFIG)
                .build();
    }

    private ChatCompletionServices azureModelFromConfig(ProviderEntry entry, String modelName) {
        log.debug("Creating Azure ChatCompletionServices from config for provider: {}, model: {}", provider, modelName);
        final var endpoint = resolveValue(entry.getEndpoint(),
                                          "AZURE_ENDPOINT",
                                          "Azure endpoint must be set in settings.yaml or AZURE_ENDPOINT env var");
        final var apiKey = resolveValue(entry.getApiKey(),
                                        "AZURE_API_KEY",
                                        "Azure API key must be set in settings.yaml or AZURE_API_KEY env var");
        final var apiVersion = resolveValueWithDefault(entry.getApiVersion(), "AZURE_API_VERSION", "2024-10-21");
        return SimpleOpenAIAzure.builder()
                .baseUrl(endpoint)
                .apiKey(apiKey)
                .apiVersion(apiVersion)
                .objectMapper(mapper)
                .clientAdapter(new OkHttpClientAdapter(okHttpClient))
                .retryConfig(RETRY_CONFIG)
                .build();
    }

    /**
     * Builds {@link ChatCompletionServices} from a {@link ProviderEntry} in settings.yaml.
     *
     * @param entry     the provider configuration entry
     * @param modelName the model name
     * @return the built {@link ChatCompletionServices}
     * @throws IllegalArgumentException if the provider type is unsupported
     */
    private ChatCompletionServices buildFromConfig(ProviderEntry entry, String modelName) {
        var type = entry.getType() == null && Providers.COPILOT.equals(provider)
                ? Providers.COPILOT
                : entry.getType();
        if (type == null) {
            throw new IllegalArgumentException("Provider '" + provider + "' in settings.yaml has no 'type' field. "
                    + "Set type to 'openai' or 'azure'.");
        }
        log.info("Building ChatCompletionServices for provider '{}' (type: '{}') from settings.yaml for model: {}",
                 provider,
                 type,
                 modelName);
        return switch (type.toLowerCase()) {
            case Providers.COPILOT -> copilotDirectModel(modelName);
            case Providers.OPENAI -> openAIModelFromConfig(entry, modelName);
            case Providers.AZURE -> azureModelFromConfig(entry, modelName);
            default -> throw new IllegalArgumentException(
                                                          "Unsupported provider type '" + type + "' for provider '"
                                                                  + provider + "'. Use 'openai' or 'azure'.");
        };
    }

    private ChatCompletionServices copilotDirectModel(String modelName) {
        log.debug("Creating Copilot Direct ChatCompletionServices for model: {}", modelName);
        if (copilotDirectProvider == null) {
            try {
                copilotDirectProvider = new CopilotDirectProvider(mapper, okHttpClient, RETRY_CONFIG);
            }
            catch (java.io.IOException e) {
                throw new IllegalStateException("Failed to initialise Copilot direct provider", e);
            }
        }
        return copilotDirectProvider.get(modelName);
    }

    private ChatCompletionServices copilotProxyModel(String modelName) {
        log.debug("Creating Copilot Proxy ChatCompletionServices for model: {}", modelName);
        final var endpoint = EnvLoader.readEnv("COPILOT_PROXY_ENDPOINT", "http://localhost:4141");
        return GithubCopilot.builder()
                .objectMapper(mapper)
                .clientAdapter(new OkHttpClientAdapter(okHttpClient))
                .baseUrl(endpoint)
                .retryConfig(RETRY_CONFIG)
                .build();
    }

    private ChatCompletionServices openAIModel(String modelName) {
        log.debug("Creating OpenAI ChatCompletionServices for model: {}", modelName);
        final var apiKey = readEnv("OPENAI_API_KEY",
                                   "OPENAI_API_KEY environment variable must be set");
        final var endpoint = EnvLoader.readEnv("OPENAI_ENDPOINT", "https://api.openai.com/v1");
        final var organizationId = EnvLoader.readEnv("OPENAI_ORGANIZATION", null);
        final var projectId = EnvLoader.readEnv("OPENAI_PROJECT_ID", null);
        final var extraHeaders = EnvLoader.readEnv("OPENAI_EXTRA_HEADERS", null);
        log.debug("Using OpenAI endpoint: {}", endpoint);
        var httpClient = applyExtraHeaders(okHttpClient, extraHeaders);
        return SimpleOpenAI.builder()
                .baseUrl(endpoint)
                .apiKey(apiKey)
                .objectMapper(mapper)
                .organizationId(organizationId)
                .projectId(projectId)
                .clientAdapter(new OkHttpClientAdapter(httpClient))
                .retryConfig(RETRY_CONFIG)
                .build();
    }

    private ChatCompletionServices openAIModelFromConfig(ProviderEntry entry, String modelName) {
        log.debug("Creating OpenAI-compatible ChatCompletionServices from config for provider: {}, model: {}",
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
        var httpClient = applyExtraHeadersFromMap(okHttpClient, entry.getExtraHeaders());
        log.info("Using OpenAI endpoint: {} for provider: {}. Api key: {}", endpoint, provider, apiKey);
        return SimpleOpenAI.builder()
                .baseUrl(endpoint)
                .apiKey(apiKey)
                .objectMapper(mapper)
                .organizationId(organizationId)
                .projectId(projectId)
                .clientAdapter(new OkHttpClientAdapter(httpClient))
                .retryConfig(RETRY_CONFIG)
                .build();
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

}
