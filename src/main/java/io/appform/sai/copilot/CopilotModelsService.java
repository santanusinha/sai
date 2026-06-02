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
package io.appform.sai.copilot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

import lombok.Data;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fetches the list of models available via the GitHub Copilot API.
 *
 * <p>Requires a valid Copilot bearer token, which can be obtained by calling
 * {@link CopilotTokenExchanger#fetchCopilotToken(String, OkHttpClient, ObjectMapper)}.
 *
 * <p>Endpoint: {@code GET https://api.githubcopilot.com/models}
 */
public class CopilotModelsService {

    static final String COPILOT_API_BASE_URL = "https://api.githubcopilot.com";
    static final String MODELS_PATH = "/models";

    private static final String COPILOT_VERSION = "0.26.7";
    private static final String EDITOR_PLUGIN_VERSION = "copilot-chat/" + COPILOT_VERSION;
    private static final String USER_AGENT = "GitHubCopilotChat/" + COPILOT_VERSION;
    private static final String API_VERSION = "2025-04-01";

    /** Trimmed wrapper around the {@code /models} response envelope. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ModelsResponse {

        @JsonProperty("data")
        private List<CopilotModel> data;
    }

    private final OkHttpClient httpClient;

    private final ObjectMapper mapper;

    public CopilotModelsService(OkHttpClient httpClient, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    /**
     * Fetches available models from the Copilot API.
     *
     * @param copilotToken a valid short-lived Copilot bearer token
     * @return list of available models
     * @throws IOException if the HTTP request fails or the response cannot be parsed
     */
    public List<CopilotModel> listModels(String copilotToken) throws IOException {
        final var request = new Request.Builder()
                .url(COPILOT_API_BASE_URL + MODELS_PATH)
                .get()
                .header("Authorization", "Bearer " + copilotToken)
                .header("content-type", "application/json")
                .header("copilot-integration-id", "vscode-chat")
                .header("editor-version", "vscode/1.104.3")
                .header("editor-plugin-version", EDITOR_PLUGIN_VERSION)
                .header("user-agent", USER_AGENT)
                .header("openai-intent", "conversation-panel")
                .header("x-github-api-version", API_VERSION)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Failed to fetch models: HTTP " + response.code());
            }
            final var modelsResponse = mapper.readValue(response.body().string(), ModelsResponse.class);
            return modelsResponse.getData() != null ? modelsResponse.getData() : List.of();
        }
    }
}
