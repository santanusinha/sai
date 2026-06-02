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
package io.appform.sai.copilot.auth;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Service for handling GitHub OAuth Device Flow authentication.
 * This implements the OAuth 2.0 Device Authorization Grant flow
 * as specified in RFC 8628.
 *
 * <p>Flow overview:
 * <ol>
 * <li>Request device code from GitHub</li>
 * <li>User enters the code at verification URI</li>
 * <li>Poll for access token</li>
 * <li>Save token to local file</li>
 * </ol>
 */
public class GitHubOAuthService {
    private static final String GITHUB_BASE_URL = "https://github.com";
    private static final String GITHUB_API_BASE_URL = "https://api.github.com";
    private static final String GITHUB_CLIENT_ID = "Iv1.b507a08c87ecfe98";
    private static final String GITHUB_APP_SCOPES = "read:user";
    private static final MediaType JSON = MediaType.parse("application/json");

    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public GitHubOAuthService() {
        this.client = new OkHttpClient();
        this.mapper = new ObjectMapper();
    }

    /**
     * Request a device code from GitHub.
     * This is the first step in the device flow.
     *
     * @return DeviceCodeResponse containing the device code, user code, and verification URI
     * @throws IOException if the request fails
     */
    public DeviceCodeResponse getDeviceCode() throws IOException {
        String json = mapper.writeValueAsString(Map.of(
                                                       "client_id",
                                                       GITHUB_CLIENT_ID,
                                                       "scope",
                                                       GITHUB_APP_SCOPES
        ));

        Request request = new Request.Builder()
                .url(GITHUB_BASE_URL + "/login/device/code")
                .post(RequestBody.create(json, JSON))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get device code: " + response.code());
            }
            return mapper.readValue(response.body().string(), DeviceCodeResponse.class);
        }
    }

    /**
     * Get the authenticated GitHub user's information.
     *
     * @param token the access token
     * @return GitHubUserResponse containing the user's login
     * @throws IOException if the request fails
     */
    public GitHubUserResponse getGitHubUser(String token) throws IOException {
        Request request = new Request.Builder()
                .url(GITHUB_API_BASE_URL + "/user")
                .get()
                .header("Accept", "application/json")
                .header("Authorization", "token " + token)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get user info: " + response.code());
            }
            return mapper.readValue(response.body().string(), GitHubUserResponse.class);
        }
    }

    /**
     * Get the path where the token should be stored.
     * Can be overridden via COPILOT_TOKEN_PATH environment variable.
     *
     * @return the path to the token file
     */
    public Path getTokenPath() {
        String envOverride = System.getenv("COPILOT_TOKEN_PATH");
        if (envOverride != null && !envOverride.isBlank()) {
            return Paths.get(envOverride);
        }
        String home = System.getProperty("user.home");
        return Paths.get(home, ".config", "sai", "copilot_token");
    }

    /**
     * Poll GitHub for an access token after the user has entered the code.
     * This will block until the user completes authentication or the code expires.
     *
     * @param deviceCode the device code response from getDeviceCode()
     * @return the access token string
     * @throws IOException          if the request fails or access is denied
     * @throws InterruptedException if the polling is interrupted
     */
    public String pollAccessToken(DeviceCodeResponse deviceCode) throws IOException, InterruptedException {
        int interval = (deviceCode.getInterval() + 1) * 1000; // Add 1 second buffer

        String json = mapper.writeValueAsString(Map.of(
                                                       "client_id",
                                                       GITHUB_CLIENT_ID,
                                                       "device_code",
                                                       deviceCode.getDeviceCode(),
                                                       "grant_type",
                                                       "urn:ietf:params:oauth:grant-type:device_code"
        ));

        while (true) {
            Request request = new Request.Builder()
                    .url(GITHUB_BASE_URL + "/login/oauth/access_token")
                    .post(RequestBody.create(json, JSON))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                AccessTokenResponse tokenResponse = mapper.readValue(
                                                                     response.body().string(),
                                                                     AccessTokenResponse.class);

                if (tokenResponse.getAccessToken() != null) {
                    return tokenResponse.getAccessToken();
                }

                // Handle error cases
                String error = tokenResponse.getError();
                if ("slow_down".equals(error)) {
                    interval += 5000; // Add 5 seconds as per OAuth spec
                }
                else if ("expired_token".equals(error)) {
                    throw new IOException("Device code expired. Please restart authentication.");
                }
                else if ("access_denied".equals(error)) {
                    throw new IOException("Access denied. User cancelled authentication.");
                }
                // For "authorization_pending", just continue polling
            }

            Thread.sleep(interval);
        }
    }

    /**
     * Remove the authentication token file.
     *
     * @throws IOException if deletion fails
     */
    public void removeToken() throws IOException {
        Path tokenFile = getTokenPath();
        if (Files.exists(tokenFile)) {
            Files.delete(tokenFile);
        }
    }

    /**
     * Save the access token to the token file with secure permissions.
     *
     * @param token the access token to save
     * @throws IOException if saving fails
     */
    public void saveToken(String token) throws IOException {
        Path tokenFile = getTokenPath();
        Files.createDirectories(tokenFile.getParent());
        Files.writeString(tokenFile, token);

        // Set file permissions to 600 (owner read/write only)
        try {
            Set<PosixFilePermission> perms = Set.of(
                                                    PosixFilePermission.OWNER_READ,
                                                    PosixFilePermission.OWNER_WRITE
            );
            Files.setPosixFilePermissions(tokenFile, perms);
        }
        catch (UnsupportedOperationException e) {
            // Windows doesn't support POSIX permissions, ignore
        }
    }
}
