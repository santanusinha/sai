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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.knuddels.jtokkit.api.EncodingType;

import org.junit.jupiter.api.Test;

import java.util.Map;

class SettingsResolverTest {

    @Test
    void resolveAutoDefaultSingleMode() {
        final var modeTuning = ModelTuning.builder()
                .maxTokens(8192)
                .build();
        final var config = SettingsConfig.builder()
                .providers(Map.of("openai",
                                  ProviderEntry.builder()
                                          .models(Map.of("gpt-4o",
                                                         ModelEntry.builder()
                                                                 .modes(Map.of("coding",
                                                                               ModeEntry.builder()
                                                                                       .tuning(modeTuning)
                                                                                       .build()))
                                                                 .build()))
                                          .build()))
                .build();
        final var result = SettingsResolver.resolve("openai", "gpt-4o", null, config, null);
        assertNotNull(result.getModelSettings());
        assertEquals(8192, result.getModelSettings().getMaxTokens());
    }

    @Test
    void resolveEmptyConfigReturnsEmpty() {
        final var config = SettingsConfig.builder().build();
        final var result = SettingsResolver.resolve("openai", "gpt-4o", null, config, null);
        assertNull(result.getModelSettings());
        assertNull(result.getModelOptions());
        assertNull(result.getTuning());
    }

    @Test
    void resolveEncodingTypeAndContextWindowSize() {
        final var modelTuning = ModelTuning.builder()
                .encodingType(EncodingType.O200K_BASE)
                .contextWindowSize(128_000)
                .build();
        final var config = SettingsConfig.builder()
                .providers(Map.of("openai",
                                  ProviderEntry.builder()
                                          .models(Map.of("gpt-4o",
                                                         ModelEntry.builder()
                                                                 .tuning(modelTuning)
                                                                 .build()))
                                          .build()))
                .build();
        final var result = SettingsResolver.resolve("openai", "gpt-4o", null, config, null);
        assertNotNull(result.getModelSettings());
        assertNotNull(result.getModelSettings().getModelAttributes());
        assertEquals(EncodingType.O200K_BASE, result.getModelSettings().getModelAttributes().getEncodingType());
        assertEquals(128_000, result.getModelSettings().getModelAttributes().getContextWindowSize());
    }

    @Test
    void resolveExplicitDefaultMode() {
        final var codingTuning = ModelTuning.builder()
                .maxTokens(4096)
                .build();
        final var planningTuning = ModelTuning.builder()
                .maxTokens(8192)
                .build();
        final var config = SettingsConfig.builder()
                .providers(Map.of("openai",
                                  ProviderEntry.builder()
                                          .models(Map.of("gpt-4o",
                                                         ModelEntry.builder()
                                                                 .defaultMode("planning")
                                                                 .modes(Map.of(
                                                                               "coding",
                                                                               ModeEntry.builder().tuning(codingTuning)
                                                                                       .build(),
                                                                               "planning",
                                                                               ModeEntry.builder().tuning(
                                                                                                          planningTuning)
                                                                                       .build()))
                                                                 .build()))
                                          .build()))
                .build();
        final var result = SettingsResolver.resolve("openai", "gpt-4o", null, config, null);
        assertNotNull(result.getModelSettings());
        assertEquals(8192, result.getModelSettings().getMaxTokens());
    }

    @Test
    void resolveModeOverridesModelLevel() {
        final var modelTuning = ModelTuning.builder()
                .temperature(0.3f)
                .maxTokens(2048)
                .build();
        final var modeTuning = ModelTuning.builder()
                .temperature(0.9f)
                .build();
        final var config = SettingsConfig.builder()
                .providers(Map.of("openai",
                                  ProviderEntry.builder()
                                          .models(Map.of("gpt-4o",
                                                         ModelEntry.builder()
                                                                 .tuning(modelTuning)
                                                                 .modes(Map.of("coding",
                                                                               ModeEntry.builder()
                                                                                       .tuning(modeTuning)
                                                                                       .build()))
                                                                 .build()))
                                          .build()))
                .build();
        final var result = SettingsResolver.resolve("openai", "gpt-4o", "coding", config, null);
        assertNotNull(result.getModelSettings());
        assertEquals(0.9f, result.getModelSettings().getTemperature());
        assertEquals(2048, result.getModelSettings().getMaxTokens());
    }

    @Test
    void resolveNullConfigReturnsEmpty() {
        final var result = SettingsResolver.resolve("openai", "gpt-4o", null, null, null);
        assertNull(result.getModelSettings());
        assertNull(result.getModelOptions());
        assertNull(result.getTuning());
    }

    @Test
    void resolvePersonaTuningFallbackWhenNoSettingsEntry() {
        final var personaTuning = ModelTuning.builder()
                .temperature(0.7f)
                .build();
        final var config = SettingsConfig.builder().build();
        final var result = SettingsResolver.resolve("openai", "gpt-4o", null, config, personaTuning);
        assertNotNull(result.getModelSettings());
        assertEquals(0.7f, result.getModelSettings().getTemperature());
    }

    @Test
    void resolvePersonaTuningMergedOnTopOfSettings() {
        final var modelTuning = ModelTuning.builder()
                .temperature(0.3f)
                .maxTokens(2048)
                .build();
        final var personaTuning = ModelTuning.builder()
                .temperature(0.9f)
                .build();
        final var config = SettingsConfig.builder()
                .providers(Map.of("openai",
                                  ProviderEntry.builder()
                                          .models(Map.of("gpt-4o",
                                                         ModelEntry.builder()
                                                                 .tuning(modelTuning)
                                                                 .build()))
                                          .build()))
                .build();
        final var result = SettingsResolver.resolve("openai", "gpt-4o", null, config, personaTuning);
        assertNotNull(result.getModelSettings());
        assertEquals(0.9f, result.getModelSettings().getTemperature());
        assertEquals(2048, result.getModelSettings().getMaxTokens());
    }

    @Test
    void resolveProviderAndModelTuning() {
        final var providerTuning = ModelTuning.builder()
                .temperature(0.5f)
                .build();
        final var modelTuning = ModelTuning.builder()
                .maxTokens(4096)
                .build();
        final var config = SettingsConfig.builder()
                .providers(Map.of("openai",
                                  ProviderEntry.builder()
                                          .tuning(providerTuning)
                                          .models(Map.of("gpt-4o",
                                                         ModelEntry.builder()
                                                                 .tuning(modelTuning)
                                                                 .build()))
                                          .build()))
                .build();
        final var result = SettingsResolver.resolve("openai", "gpt-4o", null, config, null);
        assertNotNull(result.getModelSettings());
        assertEquals(0.5f, result.getModelSettings().getTemperature());
        assertEquals(4096, result.getModelSettings().getMaxTokens());
    }

    @Test
    void resolveProviderModelAndModeTuning() {
        final var providerTuning = ModelTuning.builder()
                .temperature(0.5f)
                .build();
        final var modelTuning = ModelTuning.builder()
                .maxTokens(4096)
                .build();
        final var modeTuning = ModelTuning.builder()
                .maxTokens(8192)
                .build();
        final var config = SettingsConfig.builder()
                .providers(Map.of("openai",
                                  ProviderEntry.builder()
                                          .tuning(providerTuning)
                                          .models(Map.of("gpt-4o",
                                                         ModelEntry.builder()
                                                                 .tuning(modelTuning)
                                                                 .modes(Map.of("coding",
                                                                               ModeEntry.builder()
                                                                                       .tuning(modeTuning)
                                                                                       .build()))
                                                                 .build()))
                                          .build()))
                .build();
        final var result = SettingsResolver.resolve("openai", "gpt-4o", "coding", config, null);
        assertNotNull(result.getModelSettings());
        assertEquals(0.5f, result.getModelSettings().getTemperature());
        assertEquals(8192, result.getModelSettings().getMaxTokens());
    }

    @Test
    void resolveProviderNotFoundReturnsEmpty() {
        final var config = SettingsConfig.builder()
                .providers(Map.of("azure", ProviderEntry.builder().build()))
                .build();
        final var result = SettingsResolver.resolve("openai", "gpt-4o", null, config, null);
        assertNull(result.getModelSettings());
    }

    @Test
    void resolveProviderOnlyTuning() {
        final var providerTuning = ModelTuning.builder()
                .temperature(0.5f)
                .build();
        final var config = SettingsConfig.builder()
                .providers(Map.of("openai",
                                  ProviderEntry.builder()
                                          .tuning(providerTuning)
                                          .build()))
                .build();
        final var result = SettingsResolver.resolve("openai", "gpt-4o", null, config, null);
        assertNull(result.getModelSettings());
        assertNull(result.getTuning());
    }

    @Test
    void resolveTuningFieldPopulated() {
        final var modelTuning = ModelTuning.builder()
                .temperature(0.5f)
                .build();
        final var config = SettingsConfig.builder()
                .providers(Map.of("openai",
                                  ProviderEntry.builder()
                                          .models(Map.of("gpt-4o",
                                                         ModelEntry.builder()
                                                                 .tuning(modelTuning)
                                                                 .build()))
                                          .build()))
                .build();
        final var result = SettingsResolver.resolve("openai", "gpt-4o", null, config, null);
        assertNotNull(result.getTuning());
        assertEquals(0.5f, result.getTuning().getTemperature());
    }

    @Test
    void resolveUnknownModeLogsWarningAndIgnores() {
        final var modelTuning = ModelTuning.builder()
                .temperature(0.3f)
                .build();
        final var config = SettingsConfig.builder()
                .providers(Map.of("openai",
                                  ProviderEntry.builder()
                                          .models(Map.of("gpt-4o",
                                                         ModelEntry.builder()
                                                                 .tuning(modelTuning)
                                                                 .modes(Map.of("coding",
                                                                               ModeEntry.builder()
                                                                                       .tuning(ModelTuning.builder()
                                                                                               .maxTokens(8192).build())
                                                                                       .build()))
                                                                 .build()))
                                          .build()))
                .build();
        final var result = SettingsResolver.resolve("openai", "gpt-4o", "nonexistent", config, null);
        assertNotNull(result.getModelSettings());
        assertEquals(0.3f, result.getModelSettings().getTemperature());
        assertNull(result.getModelSettings().getMaxTokens());
    }
}
