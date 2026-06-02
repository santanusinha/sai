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

import io.appform.sai.SaiCommand;
import io.appform.sai.copilot.auth.GitHubOAuthService;

import java.nio.file.Files;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * CLI command for authenticating with GitHub Copilot.
 *
 * <p>Usage:
 * <pre>
 * sai copilot-auth # Authenticate (skips if already authenticated)
 * sai copilot-auth --force # Force re-authentication
 * sai copilot-auth --show-token # Display token after authentication
 * sai copilot-auth --remove # Remove the stored token
 * </pre>
 */
@Command(name = "copilot-auth", description = "Authenticate with GitHub Copilot")
public class CopilotAuthCommand implements Callable<Integer> {

    @ParentCommand
    private SaiCommand parent;

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

    @Override
    @SuppressWarnings("java:S106")
    public Integer call() {
        try {
            GitHubOAuthService authService = new GitHubOAuthService();

            // Handle --remove flag
            if (remove) {
                authService.removeToken();
                System.out.println("Authentication token removed from " + authService.getTokenPath());
                return 0;
            }

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
}
