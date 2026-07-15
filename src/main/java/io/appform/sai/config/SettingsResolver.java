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
package io.appform.sai.config;

import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.models.SimpleOpenAIModelOptions;

import java.util.Objects;

import javax.annotation.Nullable;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves effective model settings through the hierarchical provider → model → mode merge,
 * then applies persona tuning and CLI overrides on top.
 *
 * <p>The merge order (bottom-up, rhs wins if non-null):
 * <pre>
 * provider-level defaults (ModelTuning)
 * ⊕ model-level settings
 * ⊕ mode-level overrides
 * ⊕ persona tuning (fallback if no settings.yaml entry)
 * ⊕ CLI/session overrides
 * </pre>
 *
 * <p>If no {@code settings.yaml} entry exists for the requested model, a warning is logged
 * and the persona tuning is used directly. If neither exists, framework defaults apply
 * (handled by {@link io.appform.sai.agent.AgentFactory}).
 */
@Slf4j
public class SettingsResolver {

    /**
     * Resolved settings result containing the effective {@link ModelSettings} and
     * {@link SimpleOpenAIModelOptions}.
     */
    @Value
    @Builder
    public static class ResolvedSettings {
        @Nullable
        ModelSettings modelSettings;

        @Nullable
        SimpleOpenAIModelOptions modelOptions;

        @Nullable
        ModelTuning tuning;
    }

    /**
     * Resolves effective settings for the given provider/model/mode combination.
     *
     * @param provider       the provider name (e.g. {@code "openai"}, {@code "copilot"})
     * @param model          the model ID (may contain slashes, e.g. {@code "anthropic/claude-3.5-sonnet"})
     * @param mode           the mode name (may be {@code null})
     * @param settingsConfig the loaded settings config (may be empty/null)
     * @param personaTuning  the persona's inline tuning (fallback); may be {@code null}
     * @return the resolved settings, or an empty result if no tuning is found
     */
    public static ResolvedSettings resolve(
                                           String provider,
                                           String model,
                                           @Nullable String mode,
                                           @Nullable SettingsConfig settingsConfig,
                                           @Nullable ModelTuning personaTuning) {

        final var config = Objects.requireNonNullElseGet(settingsConfig,
                                                         () -> SettingsConfig.builder().build());
        final var providerEntry = config.getProvider(provider).orElse(null);

        ModelTuning effectiveTuning = null;
        var foundInSettings = false;

        if (providerEntry != null) {
            // Start with provider-level defaults
            effectiveTuning = providerEntry.getTuning();

            final var modelEntry = providerEntry.getModel(model);
            if (modelEntry != null) {
                foundInSettings = true;
                // Merge model-level settings on top of provider defaults
                effectiveTuning = ModelTuning.merge(effectiveTuning, modelEntry.getTuning());

                // Resolve the effective mode name (auto-default if single mode)
                final var resolvedMode = modelEntry.resolveModeName(mode);
                if (resolvedMode != null) {
                    final var modeEntry = modelEntry.getMode(resolvedMode);
                    if (modeEntry != null) {
                        // Merge mode-level overrides on top
                        effectiveTuning = ModelTuning.merge(effectiveTuning, modeEntry.getTuning());
                    }
                    else if (mode != null) {
                        log.warn("Mode '{}' not found for model '{}' under provider '{}'", mode, model, provider);
                    }
                }
            }
        }

        if (!foundInSettings) {
            // No settings.yaml entry for this model — warn and fall back to persona tuning
            if (providerEntry == null) {
                log.debug("No provider '{}' in settings.yaml; using env-var fallback for provider config", provider);
            }
            else {
                log.warn("No settings found for model '{}' under provider '{}'; using persona defaults.",
                         model,
                         provider);
            }
            effectiveTuning = personaTuning;
        }
        else {
            // Merge persona tuning on top of settings.yaml resolution
            effectiveTuning = ModelTuning.merge(effectiveTuning, personaTuning);
        }

        if (effectiveTuning == null || effectiveTuning.isEmpty()) {
            return ResolvedSettings.builder().build();
        }

        return ResolvedSettings.builder()
                .modelSettings(effectiveTuning.toModelSettings())
                .modelOptions(effectiveTuning.toModelOptions())
                .tuning(effectiveTuning)
                .build();
    }
}
