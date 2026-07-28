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
 * {@code /providers} — list all valid {@code -m} argument values from {@code settings.yaml}.
 *
 * <p>One line per usable value:
 * <pre>
 * copilot/claude-haiku-4.5
 * copilot/claude-haiku-4.5/coding
 * openrouter/kimi-k3
 * openrouter/kimi-k3/coding
 * </pre>
 */
@Command(name = "providers", description = "List valid -m (provider/model[/mode]) values from settings.yaml")
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
        final var D = Printer.Colours.GRAY;
        final var R = Printer.Colours.RESET;

        final var sb = new StringBuilder();

        // copilot is always available — print a header line so it's discoverable
        sb.append(C).append("# copilot").append(R).append(D).append("  (built-in, use copilot/<model>)").append(R)
                .append('\n');
        appendModels(sb,
                     "copilot",
                     settings.isEmpty() ? null
                             : settings.getProvider("copilot").map(e -> e.getModels()).orElse(null),
                     C,
                     D,
                     R);

        if (settings.isEmpty()) {
            sb.append(D).append("No additional providers configured in settings.yaml.\n").append(R);
        }
        else {
            final var providers = Objects.requireNonNullElse(settings.getProviders(),
                                                             Map.<String, io.appform.sai.config.ProviderEntry>of());
            new TreeMap<>(providers).forEach((providerName, entry) -> {
                if ("copilot".equalsIgnoreCase(providerName)) {
                    return;
                }
                appendModels(sb, providerName, entry.getModels(), C, D, R);
            });
        }

        printer.print(Printer.raw(sb.toString()));
    }

    private void appendModels(StringBuilder sb,
                              String providerName,
                              Map<String, io.appform.sai.config.ModelEntry> models,
                              String C,
                              String D,
                              String R) {
        if (models == null || models.isEmpty()) {
            return;
        }
        new TreeMap<>(models).forEach((modelId, modelEntry) -> {
            final var base = providerName + "/" + modelId;
            sb.append(C).append(base).append(R).append('\n');
            final var modes = modelEntry.getModes();
            if (modes != null) {
                new TreeMap<>(modes).keySet()
                        .forEach(mode -> sb.append(C).append(base).append('/').append(mode).append(R).append('\n'));
            }
        });
    }
}
