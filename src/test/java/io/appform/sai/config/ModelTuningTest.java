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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knuddels.jtokkit.api.EncodingType;
import com.phonepe.sentinelai.core.model.Reasoning;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

class ModelTuningTest {

    @Test
    void isEmptyReturnsFalseWhenAnyFieldSet() {
        final var tuning = ModelTuning.builder()
                .temperature(0.7f)
                .build();
        assertTrue(!tuning.isEmpty());
    }

    @Test
    void isEmptyReturnsFalseWhenExtraArgsHasEntries() {
        final var tuning = ModelTuning.builder()
                .extraArgs(Map.of("key", "value"))
                .build();
        assertTrue(!tuning.isEmpty());
    }

    @Test
    void isEmptyReturnsTrueForAllNullFields() {
        final var tuning = ModelTuning.builder().build();
        assertTrue(tuning.isEmpty());
    }

    @Test
    void isEmptyReturnsTrueForEmptyExtraArgs() {
        final var tuning = ModelTuning.builder()
                .extraArgs(Map.of())
                .build();
        assertTrue(tuning.isEmpty());
    }

    @Test
    void mergeBothNullReturnsNull() {
        assertNull(ModelTuning.merge(null, null));
    }

    @Test
    void mergeKeepsLhsFieldsWhenRhsDoesNotSetThem() {
        final var lhs = ModelTuning.builder()
                .temperature(0.3f)
                .maxTokens(2048)
                .seed(42)
                .build();
        final var rhs = ModelTuning.builder()
                .temperature(0.9f)
                .build();
        final var result = ModelTuning.merge(lhs, rhs);
        assertEquals(0.9f, result.getTemperature());
        assertEquals(2048, result.getMaxTokens());
        assertEquals(42, result.getSeed());
    }

    @Test
    void mergeLhsNullReturnsRhs() {
        final var rhs = ModelTuning.builder()
                .temperature(0.7f)
                .build();
        final var result = ModelTuning.merge(null, rhs);
        assertEquals(rhs, result);
    }

    @Test
    void mergePreservesContextWindowSizeFromRhs() {
        final var lhs = ModelTuning.builder()
                .contextWindowSize(64_000)
                .build();
        final var rhs = ModelTuning.builder()
                .contextWindowSize(128_000)
                .build();
        final var result = ModelTuning.merge(lhs, rhs);
        assertEquals(128_000, result.getContextWindowSize());
    }

    @Test
    void mergePreservesEncodingTypeFromRhs() {
        final var lhs = ModelTuning.builder()
                .encodingType(EncodingType.O200K_BASE)
                .build();
        final var rhs = ModelTuning.builder()
                .encodingType(EncodingType.CL100K_BASE)
                .build();
        final var result = ModelTuning.merge(lhs, rhs);
        assertEquals(EncodingType.CL100K_BASE, result.getEncodingType());
    }

    @Test
    void mergePreservesReasoningFromRhs() {
        final var lhs = ModelTuning.builder()
                .reasoning(Reasoning.LOW)
                .build();
        final var rhs = ModelTuning.builder()
                .reasoning(Reasoning.HIGH)
                .build();
        final var result = ModelTuning.merge(lhs, rhs);
        assertEquals(Reasoning.HIGH, result.getReasoning());
    }

    @Test
    void mergePreservesTimeoutFromRhs() {
        final var lhs = ModelTuning.builder()
                .timeout(Duration.ofSeconds(30))
                .build();
        final var rhs = ModelTuning.builder()
                .timeout(Duration.ofSeconds(60))
                .build();
        final var result = ModelTuning.merge(lhs, rhs);
        assertEquals(Duration.ofSeconds(60), result.getTimeout());
    }

    @Test
    void mergeRhsExtraArgsReplacesLhsExtraArgs() {
        final var lhs = ModelTuning.builder()
                .extraArgs(Map.of("lhsKey", "lhsVal"))
                .build();
        final var rhs = ModelTuning.builder()
                .extraArgs(Map.of("rhsKey", "rhsVal"))
                .build();
        final var result = ModelTuning.merge(lhs, rhs);
        assertEquals(Map.of("rhsKey", "rhsVal"), result.getExtraArgs());
    }

    @Test
    void mergeRhsNullExtraArgsKeepsLhsExtraArgs() {
        final var lhs = ModelTuning.builder()
                .extraArgs(Map.of("lhsKey", "lhsVal"))
                .build();
        final var rhs = ModelTuning.builder()
                .temperature(0.5f)
                .build();
        final var result = ModelTuning.merge(lhs, rhs);
        assertEquals(Map.of("lhsKey", "lhsVal"), result.getExtraArgs());
    }

    @Test
    void mergeRhsNullReturnsLhs() {
        final var lhs = ModelTuning.builder()
                .temperature(0.3f)
                .build();
        final var result = ModelTuning.merge(lhs, null);
        assertEquals(lhs, result);
    }

    @Test
    void mergeRhsWinsForNonNullFields() {
        final var lhs = ModelTuning.builder()
                .temperature(0.3f)
                .maxTokens(2048)
                .build();
        final var rhs = ModelTuning.builder()
                .temperature(0.9f)
                .build();
        final var result = ModelTuning.merge(lhs, rhs);
        assertEquals(0.9f, result.getTemperature());
        assertEquals(2048, result.getMaxTokens());
    }

    @Test
    void toModelAttributesReturnsNonNullWhenContextWindowSizeSet() {
        final var tuning = ModelTuning.builder()
                .contextWindowSize(128_000)
                .build();
        final var attrs = tuning.toModelAttributes();
        assertNotNull(attrs);
        assertEquals(128_000, attrs.getContextWindowSize());
    }

    @Test
    void toModelAttributesReturnsNonNullWhenEncodingTypeSet() {
        final var tuning = ModelTuning.builder()
                .encodingType(EncodingType.O200K_BASE)
                .build();
        final var attrs = tuning.toModelAttributes();
        assertNotNull(attrs);
        assertEquals(EncodingType.O200K_BASE, attrs.getEncodingType());
    }

    @Test
    void toModelAttributesReturnsNullWhenBothNull() {
        final var tuning = ModelTuning.builder().build();
        assertNull(tuning.toModelAttributes());
    }

    @Test
    void toModelOptionsReturnsDefaultWhenToolChoiceNull() {
        final var tuning = ModelTuning.builder().build();
        final var options = tuning.toModelOptions();
        assertNotNull(options);
    }

    @Test
    void toModelOptionsReturnsNonNullWhenToolChoiceSet() {
        final var tuning = ModelTuning.builder()
                .toolChoice(com.phonepe.sentinelai.models.SimpleOpenAIModelOptions.ToolChoice.AUTO)
                .build();
        final var options = tuning.toModelOptions();
        assertNotNull(options);
        assertEquals(com.phonepe.sentinelai.models.SimpleOpenAIModelOptions.ToolChoice.AUTO,
                     options.getToolChoice());
    }

    @Test
    void toModelSettingsReturnsNonNullWhenFieldsSet() {
        final var tuning = ModelTuning.builder()
                .temperature(0.5f)
                .maxTokens(4096)
                .build();
        final var settings = tuning.toModelSettings();
        assertNotNull(settings);
        assertEquals(0.5f, settings.getTemperature());
        assertEquals(4096, settings.getMaxTokens());
    }

    @Test
    void toModelSettingsReturnsNullWhenEmpty() {
        final var tuning = ModelTuning.builder().build();
        assertNull(tuning.toModelSettings());
    }
}
