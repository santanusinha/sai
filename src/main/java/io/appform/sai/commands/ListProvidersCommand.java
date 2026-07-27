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
 * <p>One line per model, modes inline in brackets:
 * <pre>
 * copilot (built-in)
 * claude-haiku-4.5 [coding]
 *
 * openrouter openai → https://openrouter.ai/api/v1
 * kimi-k3 → moonshotai/kimi-k3 [coding]
 * </pre>
 */
@Slf4j
@Command(name = "list-providers", description = "List all providers, models, and modes defined in settings.yaml")
@SuppressWarnings("java:S106")
public class ListProvidersCommand implements Callable<Integer> {

    @ParentCommand
    private SaiCommand parent;

    @Override
    public Integer call() {
        final var settings = SaiCommand.resolveSettings(parent);
        final var config = SettingsConfigLoader.load(settings.getConfigDir());

        System.out.println("Providers (settings.yaml):");

        // copilot is always available as a built-in
        System.out.println();
        System.out.println("copilot  (built-in)");
        appendCopilotModels(config);

        if (config.isEmpty()) {
            System.out.println();
            System.out.println("No additional providers configured in settings.yaml.");
            return 0;
        }

        final var providers = Objects.requireNonNullElse(
                                                         config.getProviders(),
                                                         Map.<String, io.appform.sai.config.ProviderEntry>of());

        new TreeMap<>(providers).forEach((providerName, entry) -> {
            if ("copilot".equalsIgnoreCase(providerName)) {
                return;
            }
            System.out.println();
            final var sb = new StringBuilder(providerName);
            if (entry.getType() != null || entry.getEndpoint() != null) {
                sb.append("  ");
                if (entry.getType() != null) {
                    sb.append(entry.getType());
                }
                if (entry.getEndpoint() != null) {
                    if (entry.getType() != null) {
                        sb.append(" -> ");
                    }
                    sb.append(entry.getEndpoint());
                }
            }
            System.out.println(sb);

            final var models = entry.getModels();
            if (models == null || models.isEmpty()) {
                return;
            }
            new TreeMap<>(models).forEach((modelId, modelEntry) -> {
                final var line = new StringBuilder("  ").append(modelId);
                if (modelEntry.getEffectiveModelId() != null) {
                    line.append("  -> ").append(modelEntry.getEffectiveModelId());
                }
                final var modes = modelEntry.getModes();
                if (modes != null && !modes.isEmpty()) {
                    line.append("  [").append(String.join(", ", new TreeMap<>(modes).keySet())).append(']');
                }
                System.out.println(line);
            });
        });

        return 0;
    }

    private void appendCopilotModels(io.appform.sai.config.SettingsConfig config) {
        if (config.isEmpty()) {
            return;
        }
        final var copilotEntry = config.getProvider("copilot").orElse(null);
        if (copilotEntry == null || copilotEntry.getModels() == null) {
            return;
        }
        new TreeMap<>(copilotEntry.getModels()).forEach((modelId, modelEntry) -> {
            final var line = new StringBuilder("  ").append(modelId);
            final var modes = modelEntry.getModes();
            if (modes != null && !modes.isEmpty()) {
                line.append("  [").append(String.join(", ", new TreeMap<>(modes).keySet())).append(']');
            }
            System.out.println(line);
        });
    }
}
