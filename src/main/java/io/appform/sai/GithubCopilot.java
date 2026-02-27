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
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;

import io.github.sashirestela.cleverclient.client.HttpClientAdapter;
import io.github.sashirestela.cleverclient.retry.RetryConfig;
import io.github.sashirestela.openai.OpenAI;
import io.github.sashirestela.openai.OpenAI.ChatCompletions;
import io.github.sashirestela.openai.OpenAI.Models;
import io.github.sashirestela.openai.base.ClientConfig;
import io.github.sashirestela.openai.base.OpenAIConfigurator;
import io.github.sashirestela.openai.base.OpenAIProvider;
import io.github.sashirestela.openai.service.ChatCompletionServices;
import io.github.sashirestela.openai.service.ModelServices;

import java.util.Map;
import java.util.Objects;

import lombok.Builder;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

class GithubCopilot extends OpenAIProvider implements
        ChatCompletionServices,
        ModelServices {

    public static final String GITHUB_COPILOT_BASE_URL = "https://models.github.ai/inference";
    public static final String API_VERSION = "2022-11-28";
    public static final String APPLICATION_VND_GITHUB_JSON = "application/vnd.github+json";
    public static final String API_VERSION_HEADER = "X-GitHub-Api-Version";

    @SuperBuilder
    static class GithubCopilotConfigurator extends OpenAIConfigurator {
        private String apiVersion;

        @Override
        public ClientConfig buildConfig() {
            final var headers = Map.of(
                                       HttpHeaders.ACCEPT,
                                       APPLICATION_VND_GITHUB_JSON,
                                       HttpHeaders.CONTENT_TYPE,
                                       MediaType.JSON_UTF_8.toString(),
                                       HttpHeaders.AUTHORIZATION,
                                       apiKey,
                                       API_VERSION_HEADER,
                                       Objects.<String>requireNonNullElse(apiVersion, API_VERSION)
            );
            return ClientConfig.builder()
                    .baseUrl(Objects.requireNonNullElse(baseUrl, GITHUB_COPILOT_BASE_URL))
                    .headers(headers)
                    .clientAdapter(clientAdapter)
                    .retryConfig(retryConfig)
                    .objectMapper(objectMapper)
                    .build();

        }
    }

    @Builder
    public GithubCopilot(@NonNull String apiKey,
                         String apiVersion,
                         String baseUrl,
                         HttpClientAdapter clientAdapter,
                         RetryConfig retryConfig,
                         ObjectMapper objectMapper) {
        super(GithubCopilotConfigurator.builder()
                .apiKey(apiKey)
                .apiVersion(apiVersion)
                .baseUrl(baseUrl)
                .clientAdapter(clientAdapter)
                .retryConfig(retryConfig)
                .objectMapper(objectMapper)
                .build());
    }

    @Override
    public ChatCompletions chatCompletions() {
        return getOrCreateService(ChatCompletions.class);
    }

    @Override
    public Models models() {
        return getOrCreateService(OpenAI.Models.class);
    }

}
