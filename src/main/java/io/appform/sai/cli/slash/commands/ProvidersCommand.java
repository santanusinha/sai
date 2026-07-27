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
import io.appform.sai.config.SettingsConfig;
import io.appform.sai.config.SettingsConfigLoader;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * {@code /providers} — list all providers, models, and modes defined in {@code settings.yaml}.
 *
 * <p>Loads the settings file from the current config directory and renders a tree:
 * <pre>
 *   copilot  (built-in)
 *     claude-haiku-4.5
 *       coding
 *     claude-sonnet-4.6
 *       coding  medium  review
 *   openrouter  [openai → https://openrouter.ai/api/v1]
 *     kimi-k3  (→ moonshotai/kimi-k3)
 *       coding
 * </pre>
 *
 * <p>If no providers are configured a short informational message is printed instead.
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

        final var sb = new StringBuilder();
        sb.append(Printer.Colours.YELLOW).append("Providers from settings.yaml:\n").append(Printer.Colours.RESET);

        // Always show copilot as a built-in first
        sb.append(Printer.Colours.CYAN).append("  copilot").append(Printer.Colours.RESET)
                .append(Printer.Colours.WHITE).append("  (built-in)").append(Printer.Colours.RESET).append('\n');

        if (settings.isEmpty()) {
            sb.append(Printer.Colours.WHITE)
                    .append("  No additional providers configured in settings.yaml.\n")
                    .append(Printer.Colours.RESET);
            printer.print(Printer.raw(sb.toString()));
            return;
        }

        final var providers = Objects.requireNonNullElse(settings.getProviders(), Map.<String, io.appform.sai.config.ProviderEntry>of());
        // Sort alphabetically for stable output
        new TreeMap<>(providers).forEach((providerName, entry) -> {
            // Provider header line
            sb.append(Printer.Colours.CYAN).append("  ").append(providerName).append(Printer.Colours.RESET);
            if (entry.getType() != null || entry.getEndpoint() != null) {
                sb.append(Printer.Colours.WHITE).append("  [");
                if (entry.getType() != null) {
                    sb.append(entry.getType());
                }
                if (entry.getEndpoint() != null) {
                    if (entry.getType() != null) {
                        sb.append(" → ");
                    }
                    sb.append(entry.getEndpoint());
                }
                sb.append(']').append(Printer.Colours.RESET);
            }
            sb.append('\n');

            // Models
            final var models = entry.getModels();
            if (models == null || models.isEmpty()) {
                return;
            }
            new TreeMap<>(models).forEach((modelId, modelEntry) -> {
                sb.append(Printer.Colours.GREEN).append("    ").append(modelId).append(Printer.Colours.RESET);
                if (modelEntry.getEffectiveModelId() != null) {
                    sb.append(Printer.Colours.WHITE)
                            .append("  (→ ").append(modelEntry.getEffectiveModelId()).append(')')
                            .append(Printer.Colours.RESET);
                }
                sb.append('\n');

                // Modes
                final var modes = modelEntry.getModes();
                if (modes != null && !modes.isEmpty()) {
                    sb.append("      ");
                    new TreeMap<>(modes).forEach((modeName, modeEntry) ->
                            sb.append(Printer.Colours.WHITE).append(modeName).append(Printer.Colours.RESET).append("  "));
                    sb.append('\n');
                }
            });
        });

        printer.print(Printer.raw(sb.toString()));
    }
}
