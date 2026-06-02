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
package io.appform.sai.commands;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.appform.sai.SaiCommand;
import io.appform.sai.copilot.CopilotModelsService;
import io.appform.sai.copilot.CopilotTokenExchanger;
import io.appform.sai.copilot.auth.GitHubOAuthService;

import java.nio.file.Files;
import java.util.concurrent.Callable;

import okhttp3.OkHttpClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * CLI command for managing GitHub Copilot authentication and settings.
 *
 * <p>Usage:
 * <pre>
 * sai copilot --auth # Authenticate (skips if already authenticated)
 * sai copilot --auth --force # Force re-authentication
 * sai copilot --auth --show-token # Display token after authentication
 * sai copilot --remove # Remove the stored token
 * sai copilot --list # List available Copilot models
 * </pre>
 */
@Command(name = "copilot", description = "Manage GitHub Copilot authentication and settings")
public class CopilotCommand implements Callable<Integer> {

    @ParentCommand
    private SaiCommand parent;

    @Option(names = {
            "-a", "--auth"
    }, description = "Authenticate with GitHub Copilot")
    private boolean auth;

    @Option(names = {
            "-f", "--force"
    }, description = "Force re-authentication even if token exists")
    private boolean force;

    @Option(names = {
            "--show-token"
    }, description = "Display the token after authentication")
    private boolean showToken;

    @Option(names = {
            "--remove"
    }, description = "Remove the stored authentication token")
    private boolean remove;

    @Option(names = {
            "--list"
    }, description = "List available GitHub Copilot models")
    private boolean list;

    @Override
    @SuppressWarnings("java:S106")
    public Integer call() {
        if (remove) {
            return runRemove();
        }
        if (list) {
            return runList();
        }
        if (auth) {
            return runAuth();
        }
        System.out.println("Usage: sai copilot [--auth] [--list] [--remove]");
        System.out.println("  --auth, -a     Authenticate with GitHub Copilot");
        System.out.println("  --list         List available GitHub Copilot models");
        System.out.println("  --remove       Remove the stored authentication token");
        return 0;
    }

    @SuppressWarnings("java:S106")
    private int runAuth() {
        try {
            GitHubOAuthService authService = new GitHubOAuthService();

            // Check if token already exists
            if (!force && Files.exists(authService.getTokenPath())) {
                String existingToken = Files.readString(authService.getTokenPath()).trim();
                if (!existingToken.isEmpty()) {
                    System.out.println("Already authenticated. Use --force to re-authenticate.");
                    try {
                        var user = authService.getGitHubUser(existingToken);
                        System.out.println("Logged in as " + user.getLogin());
                    }
                    catch (Exception e) {
                        System.out.println("Token may be invalid. Use --force to re-authenticate.");
                    }
                    return 0;
                }
            }

            // Step 1: Get device code
            System.out.println("Initiating GitHub authentication...");
            var deviceCode = authService.getDeviceCode();

            // Step 2: Display instructions
            System.out.println();
            System.out.println("Please enter the code \"" + deviceCode.getUserCode() +
                    "\" at " + deviceCode.getVerificationUri());
            System.out.println();
            System.out.println("Waiting for authorization...");

            // Step 3: Poll for access token
            String token = authService.pollAccessToken(deviceCode);

            // Step 4: Save token
            authService.saveToken(token);

            // Step 5: Verify and display user
            var user = authService.getGitHubUser(token);
            System.out.println("Logged in as " + user.getLogin());
            System.out.println("GitHub token written to " + authService.getTokenPath());

            if (showToken) {
                System.out.println("Token: " + token);
            }

            return 0;
        }
        catch (Exception e) {
            System.err.println("Authentication failed: " + e.getMessage());
            return 1;
        }
    }

    @SuppressWarnings("java:S106")
    private int runList() {
        try {
            GitHubOAuthService authService = new GitHubOAuthService();
            if (!Files.exists(authService.getTokenPath())) {
                System.err.println(
                                   "Not authenticated. Run `sai copilot --auth` first to authenticate.");
                return 1;
            }
            final var githubToken = Files.readString(authService.getTokenPath()).strip();
            if (githubToken.isEmpty()) {
                System.err.println(
                                   "Authentication token is empty. Run `sai copilot --auth` to re-authenticate.");
                return 1;
            }

            final var httpClient = new OkHttpClient();
            final var mapper = new ObjectMapper();
            final var copilotToken = CopilotTokenExchanger.fetchCopilotToken(githubToken, httpClient, mapper);
            final var modelsService = new CopilotModelsService(httpClient, mapper);
            final var models = modelsService.listModels(copilotToken);

            if (models.isEmpty()) {
                System.out.println("No models available.");
                return 0;
            }

            System.out.printf("%-45s %-20s %-12s %s%n", "Model ID", "Vendor", "Type", "Name");
            System.out.println("-".repeat(100));
            for (final var model : models) {
                final var type = model.getCapabilities() != null ? model.getCapabilities().getType() : "";
                System.out.printf("%-45s %-20s %-12s %s%n",
                                  model.getId(),
                                  model.getVendor() != null ? model.getVendor() : "",
                                  type != null ? type : "",
                                  model.getName() != null ? model.getName() : "");
            }
            System.out.println();
            System.out.println("Use with: sai --model copilot/<Model ID>");
            return 0;
        }
        catch (Exception e) {
            System.err.println("Failed to list models: " + e.getMessage());
            return 1;
        }
    }

    @SuppressWarnings("java:S106")
    private int runRemove() {
        try {
            GitHubOAuthService authService = new GitHubOAuthService();
            authService.removeToken();
            System.out.println("Authentication token removed from " + authService.getTokenPath());
            return 0;
        }
        catch (Exception e) {
            System.err.println("Failed to remove token: " + e.getMessage());
            return 1;
        }
    }
}
