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
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.utils.EnvLoader;
import com.phonepe.sentinelai.models.ChatCompletionServiceFactory;

import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.cleverclient.retry.RetryConfig;
import io.github.sashirestela.openai.SimpleOpenAI;
import io.github.sashirestela.openai.SimpleOpenAIAzure;
import io.github.sashirestela.openai.service.ChatCompletionServices;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

@Slf4j
@AllArgsConstructor(access = AccessLevel.PUBLIC)
public class ConfigurableDefaultChatCompletionFactory implements ChatCompletionServiceFactory {
    private static final RetryConfig RETRY_CONFIG = RetryConfig.builder()
            .maxAttempts(1) // disabling implicit retries by default for tests
            .build();

    @UtilityClass
    public static class Providers {
        public static final String AZURE = "azure";
        public static final String OPENAI = "openai";
        public static final String COPILOT_PROXY = "copilot-proxy";
    }

    private final String provider;
    private final ObjectMapper mapper;
    private final OkHttpClient okHttpClient;

    @Override
    public ChatCompletionServices get(String modelName) {
        return switch (provider) {
            case Providers.AZURE -> azureModel(modelName);
            case Providers.OPENAI -> openAIModel(modelName);
            case Providers.COPILOT_PROXY -> copilotProxyModel(modelName);
            default -> throw new IllegalArgumentException("Unsupported provider: " + provider);
        };
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

    private ChatCompletionServices copilotProxyModel(String modelName) {
        log.debug("Creating Copilot Proxy ChatCompletionServices for model: {}", modelName);
        final var endpoint = readEnv("COPILOT_PROXY_ENDPOINT", "http://localhost:4141");
        final var apiKey = readEnv("COPILOT_GITHUB_PAT", "COPILOT_GITHUB_PAT environment variable must be set");
        return GithubCopilot.builder()
                .apiKey(apiKey)
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
        var httpClient = okHttpClient;
        if (!Strings.isNullOrEmpty(extraHeaders)) {
            httpClient = okHttpClient.newBuilder()
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

}
