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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.MediaType;
import com.phonepe.sentinelai.models.ChatCompletionServiceFactory;

import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.cleverclient.retry.RetryConfig;
import io.github.sashirestela.openai.SimpleOpenAI;
import io.github.sashirestela.openai.service.ChatCompletionServices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * A {@link ChatCompletionServiceFactory} that talks directly to the GitHub Copilot API without
 * requiring an external proxy server such as {@code copilot-api}.
 *
 * <p>On construction it:
 * <ol>
 * <li>Reads the GitHub OAuth token from SAI's config directory
 * ({@code ~/.config/sai/copilot_token}), overridable via the
 * {@code COPILOT_TOKEN_PATH} environment variable.</li>
 * <li>Exchanges the GitHub token for a short-lived Copilot bearer token by calling
 * {@code GET https://api.github.com/copilot_internal/v2/token}.</li>
 * <li>Schedules automatic token refresh 60 s before the reported expiry.</li>
 * </ol>
 *
 * <p>Every call to {@link #get(String)} returns a {@link SimpleOpenAI} instance whose
 * underlying {@link OkHttpClient} is equipped with two interceptors:
 * <ul>
 * <li>A URL-rewrite interceptor that strips the {@code /v1} prefix added by the SimpleOpenAI
 * library — the Copilot chat endpoint is {@code /chat/completions}, not
 * {@code /v1/chat/completions}.</li>
 * <li>A header interceptor that injects all Copilot-required request headers (editor version,
 * plugin version, integration id, etc.) and replaces the {@code Authorization} header with
 * the current Copilot bearer token.</li>
 * </ul>
 *
 * <p>Call {@link #close()} when the provider is no longer needed to release the background
 * refresh thread.
 */
@Slf4j
public class CopilotDirectProvider implements ChatCompletionServiceFactory, AutoCloseable {

    static final String GITHUB_API_BASE_URL = "https://api.github.com";
    static final String COPILOT_API_BASE_URL = "https://api.githubcopilot.com";

    private static final String COPILOT_VERSION = "0.26.7";
    private static final String EDITOR_PLUGIN_VERSION = "copilot-chat/" + COPILOT_VERSION;
    private static final String USER_AGENT = "GitHubCopilotChat/" + COPILOT_VERSION;
    private static final String API_VERSION = "2025-04-01";
    private static final String INITIATOR_HEADER = "X-Initiator";

    /**
     * Minimum number of seconds before token expiry at which a refresh is triggered.
     * Mirrors the behaviour of copilot-api (refresh_in - 60 seconds).
     */
    private static final int REFRESH_BUFFER_SECONDS = 60;

    private final ObjectMapper mapper;
    private final OkHttpClient baseHttpClient;
    private final RetryConfig retryConfig;
    private final String githubToken;
    private final AtomicReference<String> copilotToken = new AtomicReference<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        final var t = new Thread(r, "copilot-token-refresh");
        t.setDaemon(true);
        return t;
    });

    /**
     * Creates and initialises a {@code CopilotDirectProvider}.
     *
     * @param mapper         Jackson mapper shared with the rest of the application
     * @param baseHttpClient the application-wide HTTP client (timeouts already configured)
     * @param retryConfig    retry settings for the chat-completion client
     * @throws IOException           if the GitHub token file cannot be read
     * @throws IllegalStateException if the GitHub token file is empty or token exchange fails
     */
    public CopilotDirectProvider(ObjectMapper mapper,
                                 OkHttpClient baseHttpClient,
                                 RetryConfig retryConfig) throws IOException {
        this.mapper = mapper;
        this.baseHttpClient = baseHttpClient;
        this.retryConfig = retryConfig;
        this.githubToken = readGithubToken();
        final var tokenResponse = fetchCopilotToken();
        copilotToken.set(tokenResponse.token());
        scheduleRefresh(tokenResponse.refreshIn());
        log.info("Copilot direct provider initialised");
    }

    @Override
    public ChatCompletionServices get(String modelName) {
        final var interceptedClient = baseHttpClient.newBuilder()
                .addInterceptor(chain -> {
                    // SimpleOpenAI uses @Resource("/v1/chat/completions") but the Copilot
                    // API endpoint has no /v1 prefix — rewrite the path before sending.
                    final var original = chain.request();
                    final var originalUrl = original.url();
                    final var rewrittenUrl = originalUrl.newBuilder()
                            .encodedPath(originalUrl.encodedPath().replaceFirst("^/v1", ""))
                            .build();
                    return chain.proceed(original.newBuilder().url(rewrittenUrl).build());
                })
                .addInterceptor(chain -> {
                    final var original = chain.request();
                    final var requestId = UUID.randomUUID().toString();
                    final var isAgentCall = original.header(INITIATOR_HEADER) != null;
                    final var initiator = isAgentCall ? original.header(INITIATOR_HEADER) : "user";
                    final var withHeaders = original.newBuilder()
                            .header("Authorization", "Bearer " + copilotToken.get())
                            .header("content-type", MediaType.JSON_UTF_8.toString())
                            .header("copilot-integration-id", "vscode-chat")
                            .header("editor-version", "vscode/1.104.3")
                            .header("editor-plugin-version", EDITOR_PLUGIN_VERSION)
                            .header("user-agent", USER_AGENT)
                            .header("openai-intent", "conversation-panel")
                            .header("x-github-api-version", API_VERSION)
                            .header("x-request-id", requestId)
                            .header("x-vscode-user-agent-library-version", "electron-fetch")
                            .header(INITIATOR_HEADER, initiator)
                            .build();
                    return chain.proceed(withHeaders);
                })
                .build();

        return SimpleOpenAI.builder()
                .baseUrl(COPILOT_API_BASE_URL)
                .apiKey("dummy") // replaced by interceptor
                .objectMapper(mapper)
                .clientAdapter(new OkHttpClientAdapter(interceptedClient))
                .retryConfig(retryConfig)
                .build();
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }

    @VisibleForTesting
    static String resolveTokenPath() {
        final var envOverride = System.getenv("COPILOT_TOKEN_PATH");
        if (envOverride != null && !envOverride.isBlank()) {
            return envOverride;
        }
        final var home = System.getProperty("user.home",
                                            System.getenv().getOrDefault("HOME", ""));
        return home + "/.config/sai/copilot_token";
    }

    private String readGithubToken() throws IOException {
        final var tokenPath = resolveTokenPath();
        log.debug("Reading GitHub token from: {}", tokenPath);
        final var token = Files.readString(Paths.get(tokenPath)).strip();
        if (token.isEmpty()) {
            throw new IllegalStateException(
                                            "GitHub token file is empty: " + tokenPath +
                                                    ". Run `sai auth` first to authenticate.");
        }
        return token;
    }

    private CopilotTokenResponse fetchCopilotToken() {
        log.debug("Fetching Copilot token from GitHub API");
        final var request = new Request.Builder()
                .url(GITHUB_API_BASE_URL + "/copilot_internal/v2/token")
                .header("authorization", "token " + githubToken)
                .header("content-type", "application/json")
                .header("accept", "application/json")
                .header("editor-version", "vscode/1.104.3")
                .header("editor-plugin-version", EDITOR_PLUGIN_VERSION)
                .header("user-agent", USER_AGENT)
                .header("x-github-api-version", API_VERSION)
                .get()
                .build();
        try (Response response = baseHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IllegalStateException("Failed to fetch Copilot token, HTTP " + response.code());
            }
            return mapper.readValue(response.body().string(), CopilotTokenResponse.class);
        }
        catch (IOException e) {
            throw new IllegalStateException("Failed to fetch Copilot token", e);
        }
    }

    private void scheduleRefresh(int refreshInSeconds) {
        final var delaySeconds = Math.max(1L, (long) refreshInSeconds - REFRESH_BUFFER_SECONDS);
        log.debug("Scheduling Copilot token refresh in {} s", delaySeconds);
        scheduler.schedule(this::refreshToken, delaySeconds, TimeUnit.SECONDS);
    }

    private void refreshToken() {
        log.debug("Refreshing Copilot token");
        try {
            final var tokenResponse = fetchCopilotToken();
            copilotToken.set(tokenResponse.token());
            log.debug("Copilot token refreshed successfully");
            scheduleRefresh(tokenResponse.refreshIn());
        }
        catch (Exception e) {
            log.error("Failed to refresh Copilot token; retrying in 30 s", e);
            scheduler.schedule(this::refreshToken, 30, TimeUnit.SECONDS);
        }
    }

    /**
     * Trimmed representation of the {@code /copilot_internal/v2/token} response.
     */
    record CopilotTokenResponse(
            @JsonProperty("token") String token,
            @JsonProperty("expires_at") long expiresAt,
            @JsonProperty("refresh_in") int refreshIn
    ) {

        static CopilotTokenResponse of(String token, long expiresAt, int refreshIn) {
            return new CopilotTokenResponse(token, expiresAt, refreshIn);
        }
    }
}
