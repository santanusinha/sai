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

import lombok.Data;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Exchanges a GitHub OAuth token for a short-lived GitHub Copilot bearer token.
 *
 * <p>Endpoint: {@code GET https://api.github.com/copilot_internal/v2/token}
 */
public class CopilotTokenExchanger {

    static final String GITHUB_API_BASE_URL = "https://api.github.com";
    static final String TOKEN_PATH = "/copilot_internal/v2/token";

    private static final String COPILOT_VERSION = "0.26.7";
    private static final String EDITOR_PLUGIN_VERSION = "copilot-chat/" + COPILOT_VERSION;
    private static final String USER_AGENT = "GitHubCopilotChat/" + COPILOT_VERSION;
    private static final String API_VERSION = "2025-04-01";

    /** Trimmed representation of the {@code /copilot_internal/v2/token} response. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class CopilotTokenResponse {

        @JsonProperty("token")
        private String token;
    }

    private CopilotTokenExchanger() {
    }

    /**
     * Exchanges a GitHub OAuth token for a short-lived Copilot bearer token.
     *
     * @param githubToken  the GitHub OAuth token stored in the SAI config directory
     * @param httpClient   an OkHttpClient instance (shared with the caller)
     * @param objectMapper a Jackson ObjectMapper for JSON parsing
     * @return a valid Copilot bearer token string
     * @throws IOException if the HTTP request fails or the response cannot be parsed
     */
    public static String fetchCopilotToken(String githubToken,
                                           OkHttpClient httpClient,
                                           ObjectMapper objectMapper) throws IOException {
        final var request = new Request.Builder()
                .url(GITHUB_API_BASE_URL + TOKEN_PATH)
                .get()
                .header("authorization", "token " + githubToken)
                .header("content-type", "application/json")
                .header("accept", "application/json")
                .header("editor-version", "vscode/1.104.3")
                .header("editor-plugin-version", EDITOR_PLUGIN_VERSION)
                .header("user-agent", USER_AGENT)
                .header("x-github-api-version", API_VERSION)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException(
                                      "Failed to exchange GitHub token for Copilot token: HTTP " +
                                              response.code());
            }
            return objectMapper
                    .readValue(response.body().string(), CopilotTokenResponse.class)
                    .getToken();
        }
    }
}
