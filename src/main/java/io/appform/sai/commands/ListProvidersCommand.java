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
 * {@code list-providers} — prints all valid {@code -m} argument values, one per line.
 *
 * <p>Output format:
 * <pre>
 * copilot/claude-haiku-4.5
 * copilot/claude-haiku-4.5/coding
 * openrouter/kimi-k3
 * openrouter/kimi-k3/coding
 * </pre>
 */
@Slf4j
@Command(name = "list-providers", description = "List all valid -m (provider/model[/mode]) values from settings.yaml")
@SuppressWarnings("java:S106")
public class ListProvidersCommand implements Callable<Integer> {

    @ParentCommand
    private SaiCommand parent;

    @Override
    public Integer call() {
        final var settings = SaiCommand.resolveSettings(parent);
        final var config = SettingsConfigLoader.load(settings.getConfigDir());

        // copilot built-in — no extra models to enumerate unless configured
        printModels("copilot",
                    config.isEmpty() ? null
                            : config.getProvider("copilot").map(e -> e.getModels()).orElse(null));

        if (config.isEmpty()) {
            return 0;
        }

        final var providers = Objects.requireNonNullElse(
                                                         config.getProviders(),
                                                         Map.<String, io.appform.sai.config.ProviderEntry>of());

        new TreeMap<>(providers).forEach((providerName, entry) -> {
            if ("copilot".equalsIgnoreCase(providerName)) {
                return;
            }
            printModels(providerName, entry.getModels());
        });

        return 0;
    }

    private void printModels(String providerName,
                             Map<String, io.appform.sai.config.ModelEntry> models) {
        if (models == null || models.isEmpty()) {
            return;
        }
        new TreeMap<>(models).forEach((modelId, modelEntry) -> {
            final var base = providerName + "/" + modelId;
            System.out.println(base);
            final var modes = modelEntry.getModes();
            if (modes != null) {
                new TreeMap<>(modes).keySet().forEach(mode -> System.out.println(base + "/" + mode));
            }
        });
    }
}
