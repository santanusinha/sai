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
package io.appform.sai.cli.slash.commands;

import io.appform.sai.Printer;
import io.appform.sai.cli.slash.SlashRootCommand;
import io.appform.sai.config.SettingsConfigLoader;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * {@code /providers} — list all providers, models, and modes defined in {@code settings.yaml}.
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
@Command(name = "providers", description = "List providers, models, and modes from settings.yaml")
public class ProvidersCommand implements Runnable {

    @ParentCommand
    private SlashRootCommand parent;

    @Override
    public void run() {
        final var context = parent.getContext();
        final var printer = context.getPrinter();
        final var configDir = context.getSettings().getConfigDir();
        final var settings = SettingsConfigLoader.load(configDir);

        final var C = Printer.Colours.CYAN;
        final var G = Printer.Colours.GREEN;
        final var W = Printer.Colours.WHITE;
        final var D = Printer.Colours.GRAY;
        final var R = Printer.Colours.RESET;

        final var sb = new StringBuilder();
        sb.append(W).append("Providers (settings.yaml):\n").append(R);

        // copilot built-in
        sb.append('\n').append(C).append("copilot").append(R)
                .append(D).append("  (built-in)").append(R).append('\n');
        appendCopilotModels(sb, settings, G, W, D, R);

        if (settings.isEmpty()) {
            sb.append(D).append("\nNo additional providers configured in settings.yaml.").append(R).append('\n');
        }
        else {
            final var providers = Objects.requireNonNullElse(settings.getProviders(),
                                                             Map.<String, io.appform.sai.config.ProviderEntry>of());
            new TreeMap<>(providers).forEach((providerName, entry) -> {
                if ("copilot".equalsIgnoreCase(providerName)) {
                    return;
                }
                sb.append('\n').append(C).append(providerName).append(R);
                if (entry.getType() != null || entry.getEndpoint() != null) {
                    sb.append(D).append("  ");
                    if (entry.getType() != null) {
                        sb.append(entry.getType());
                    }
                    if (entry.getEndpoint() != null) {
                        if (entry.getType() != null) {
                            sb.append(" → ");
                        }
                        sb.append(entry.getEndpoint());
                    }
                    sb.append(R);
                }
                sb.append('\n');

                final var models = entry.getModels();
                if (models == null || models.isEmpty()) {
                    return;
                }
                new TreeMap<>(models).forEach((modelId, modelEntry) -> {
                    sb.append("  ").append(G).append(modelId).append(R);
                    if (modelEntry.getEffectiveModelId() != null) {
                        sb.append(D).append("  → ").append(modelEntry.getEffectiveModelId()).append(R);
                    }
                    final var modes = modelEntry.getModes();
                    if (modes != null && !modes.isEmpty()) {
                        final var modeList = String.join(", ", new TreeMap<>(modes).keySet());
                        sb.append(W).append("  [").append(modeList).append(']').append(R);
                    }
                    sb.append('\n');
                });
            });
        }

        printer.print(Printer.raw(sb.toString()));
    }

    private void appendCopilotModels(StringBuilder sb,
                                     io.appform.sai.config.SettingsConfig settings,
                                     String G,
                                     String W,
                                     String D,
                                     String R) {
        if (settings.isEmpty()) {
            return;
        }
        final var copilotEntry = settings.getProvider("copilot").orElse(null);
        if (copilotEntry == null || copilotEntry.getModels() == null) {
            return;
        }
        new TreeMap<>(copilotEntry.getModels()).forEach((modelId, modelEntry) -> {
            sb.append("  ").append(G).append(modelId).append(R);
            final var modes = modelEntry.getModes();
            if (modes != null && !modes.isEmpty()) {
                final var modeList = String.join(", ", new TreeMap<>(modes).keySet());
                sb.append(W).append("  [").append(modeList).append(']').append(R);
            }
            sb.append('\n');
        });
    }
}
