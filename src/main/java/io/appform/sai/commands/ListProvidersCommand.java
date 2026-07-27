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
import io.appform.sai.config.SettingsConfigLoader;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.Callable;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * {@code list-providers} — prints all providers, models, and modes defined in
 * {@code settings.yaml}, then exits.
 *
 * <p>Output format (plain text, no ANSI):
 * <pre>
 *   copilot  (built-in)
 *   openrouter  [openai -> https://openrouter.ai/api/v1]
 *     kimi-k3  (-> moonshotai/kimi-k3)
 *       modes: coding
 *   godric  [openai -> https://godric-internal.phonepe.com]
 *     global:LLM_GLOBAL_ZAI_ORG_GLM_5_2_FP8_PRD
 * </pre>
 */
@Slf4j
@Command(name = "list-providers",
         description = "List all providers, models, and modes defined in settings.yaml")
@SuppressWarnings("java:S106")
public class ListProvidersCommand implements Callable<Integer> {

    @ParentCommand
    private SaiCommand parent;

    @Override
    public Integer call() {
        final var settings = SaiCommand.resolveSettings(parent);
        final var config = SettingsConfigLoader.load(settings.getConfigDir());

        System.out.printf("%-30s  %-12s  %-50s%n", "PROVIDER / MODEL", "TYPE", "ENDPOINT / NOTES");
        System.out.println("-".repeat(96));

        // copilot is always available as a built-in
        System.out.printf("%-30s  %-12s  %-50s%n", "copilot", "(built-in)", "");

        if (config.isEmpty()) {
            System.out.println();
            System.out.println("No additional providers configured in settings.yaml.");
            return 0;
        }

        final var providers = Objects.requireNonNullElse(
                config.getProviders(),
                Map.<String, io.appform.sai.config.ProviderEntry>of());

        new TreeMap<>(providers).forEach((providerName, entry) -> {
            // copilot is always shown as a built-in above; skip it here to avoid duplication
            if ("copilot".equalsIgnoreCase(providerName)) {
                return;
            }
            final var type = Objects.requireNonNullElse(entry.getType(), "");
            final var endpoint = Objects.requireNonNullElse(entry.getEndpoint(), "");
            System.out.printf("%-30s  %-12s  %-50s%n", providerName, type, endpoint);

            final var models = entry.getModels();
            if (models == null || models.isEmpty()) {
                return;
            }
            new TreeMap<>(models).forEach((modelId, modelEntry) -> {
                final var notes = modelEntry.getEffectiveModelId() != null
                        ? "-> " + modelEntry.getEffectiveModelId()
                        : "";
                System.out.printf("  %-28s  %-12s  %-50s%n", modelId, "", notes);

                final var modes = modelEntry.getModes();
                if (modes != null && !modes.isEmpty()) {
                    final var modeList = new TreeMap<>(modes).keySet()
                            .stream()
                            .reduce((a, b) -> a + "  " + b)
                            .orElse("");
                    System.out.printf("    modes: %s%n", modeList);
                }
            });
        });

        return 0;
    }
}
