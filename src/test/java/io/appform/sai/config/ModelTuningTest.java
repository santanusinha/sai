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
import com.phonepe.sentinelai.models.ModelOptions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

class ModelTuningTest {

    static Stream<Arguments> isEmptyCases() {
        return Stream.of(
                         Arguments.of("empty builder is empty",
                                      (Consumer<ModelTuning.ModelTuningBuilder>) b -> {},
                                      true),
                         Arguments.of("empty extraArgs is empty",
                                      (Consumer<ModelTuning.ModelTuningBuilder>) b -> b.extraArgs(Map.of()),
                                      true),
                         Arguments.of("temperature set is not empty",
                                      (Consumer<ModelTuning.ModelTuningBuilder>) b -> b.temperature(0.7f),
                                      false),
                         Arguments.of("extraArgs set is not empty",
                                      (Consumer<ModelTuning.ModelTuningBuilder>) b -> b.extraArgs(Map.of("key",
                                                                                                         "value")),
                                      false),
                         Arguments.of("reasoningReplay set is not empty",
                                      (Consumer<ModelTuning.ModelTuningBuilder>) b -> b
                                              .reasoningReplay(ModelOptions.ReasoningReplay.ON),
                                      false));
    }

    static Stream<String> mergeFields() {
        return fieldSetters().keySet().stream().sorted();
    }

    private static void assertField(final ModelTuning tuning, final String field) {
        switch (field) {
            case "temperature" -> assertEquals(0.9f, tuning.getTemperature(), field);
            case "maxTokens" -> assertEquals(4096, tuning.getMaxTokens(), field);
            case "seed" -> assertEquals(42, tuning.getSeed(), field);
            case "timeout" -> assertEquals(Duration.ofSeconds(60), tuning.getTimeout(), field);
            case "contextWindowSize" -> assertEquals(128_000, tuning.getContextWindowSize(), field);
            case "encodingType" -> assertEquals(EncodingType.CL100K_BASE, tuning.getEncodingType(), field);
            case "reasoning" -> assertEquals(Reasoning.HIGH, tuning.getReasoning(), field);
            case "reasoningReplay" -> assertEquals(ModelOptions.ReasoningReplay.ON,
                                                   tuning.getReasoningReplay(),
                                                   field);
            case "protocol" -> assertEquals("responses", tuning.getProtocol(), field);
            case "extraArgs" -> assertEquals(Map.of("key", "value"), tuning.getExtraArgs(), field);
            default -> throw new AssertionError("unknown field: " + field);
        }
    }

    /**
     * Field setters used by the merge tests: each entry sets one field on the
     * builder, so the merge behaviour can be checked for every field.
     */
    private static Map<String, Consumer<ModelTuning.ModelTuningBuilder>> fieldSetters() {
        return Map.ofEntries(
                             Map.entry("temperature", b -> b.temperature(0.9f)),
                             Map.entry("maxTokens", b -> b.maxTokens(4096)),
                             Map.entry("seed", b -> b.seed(42)),
                             Map.entry("timeout", b -> b.timeout(Duration.ofSeconds(60))),
                             Map.entry("contextWindowSize", b -> b.contextWindowSize(128_000)),
                             Map.entry("encodingType", b -> b.encodingType(EncodingType.CL100K_BASE)),
                             Map.entry("reasoning", b -> b.reasoning(Reasoning.HIGH)),
                             Map.entry("reasoningReplay",
                                       b -> b.reasoningReplay(ModelOptions.ReasoningReplay.ON)),
                             Map.entry("protocol", b -> b.protocol("responses")),
                             Map.entry("extraArgs", b -> b.extraArgs(Map.of("key", "value"))));
    }

    private static ModelTuning tuning(final Consumer<ModelTuning.ModelTuningBuilder> setter) {
        final var builder = ModelTuning.builder();
        setter.accept(builder);
        return builder.build();
    }

    @Test
    void defaultsAreNull() {
        final var tuning = ModelTuning.builder().build();
        assertNull(tuning.getProtocol());
        assertNull(tuning.getReasoningReplay());
        assertTrue(tuning.isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("isEmptyCases")
    void isEmpty(String description, Consumer<ModelTuning.ModelTuningBuilder> setter, boolean expected) {
        final var builder = ModelTuning.builder();
        setter.accept(builder);
        assertEquals(expected, builder.build().isEmpty(), description);
    }

    @ParameterizedTest(name = "merge keeps lhs {0} when rhs does not set it")
    @MethodSource("mergeFields")
    void mergeKeepsLhsFieldWhenRhsUnset(String field) {
        final var lhs = tuning(b -> fieldSetters().get(field).accept(b));
        final var rhs = ModelTuning.builder().build();

        // An empty rhs tunes nothing, so the merge result must keep the lhs value.
        final var merged = ModelTuning.merge(lhs, rhs);
        assertField(merged, field);
    }

    @Test
    void mergeNullHandling() {
        assertNull(ModelTuning.merge(null, null));

        final var rhsOnly = ModelTuning.builder().temperature(0.7f).build();
        assertEquals(rhsOnly, ModelTuning.merge(null, rhsOnly));

        final var lhsOnly = ModelTuning.builder().temperature(0.3f).build();
        assertEquals(lhsOnly, ModelTuning.merge(lhsOnly, null));
    }

    @ParameterizedTest(name = "merge takes {0} from rhs when rhs sets it")
    @MethodSource("mergeFields")
    void mergeRhsFieldWins(String field) {
        final var lhs = ModelTuning.builder().build();
        final var rhs = tuning(b -> fieldSetters().get(field).accept(b));

        // An empty lhs tunes nothing, so the merge result must match the rhs for
        // every field, and null the fields rhs leaves unset.
        final var merged = ModelTuning.merge(lhs, rhs);
        assertField(merged, field);
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
    void toModelAttributes() {
        assertNull(ModelTuning.builder().build().toModelAttributes());

        final var contextWindowSize = ModelTuning.builder()
                .contextWindowSize(128_000)
                .build()
                .toModelAttributes();
        assertNotNull(contextWindowSize);
        assertEquals(128_000, contextWindowSize.getContextWindowSize());

        final var encodingType = ModelTuning.builder()
                .encodingType(EncodingType.O200K_BASE)
                .build()
                .toModelAttributes();
        assertNotNull(encodingType);
        assertEquals(EncodingType.O200K_BASE, encodingType.getEncodingType());
    }

    @Test
    void toModelOptions() {
        // Empty tuning produces no options; each supported field alone produces them.
        assertNull(ModelTuning.builder().build().toModelOptions());

        final var toolChoiceOnly = ModelTuning.builder()
                .toolChoice(ModelOptions.ToolChoice.AUTO)
                .build()
                .toModelOptions();
        assertNotNull(toolChoiceOnly);
        assertEquals(ModelOptions.ToolChoice.AUTO, toolChoiceOnly.getToolChoice());

        final var reasoningReplayOnly = ModelTuning.builder()
                .reasoningReplay(ModelOptions.ReasoningReplay.ON)
                .build()
                .toModelOptions();
        assertNotNull(reasoningReplayOnly);
        assertEquals(ModelOptions.ReasoningReplay.ON, reasoningReplayOnly.getReasoningReplay());

        final var both = ModelTuning.builder()
                .toolChoice(ModelOptions.ToolChoice.AUTO)
                .reasoningReplay(ModelOptions.ReasoningReplay.ON)
                .build()
                .toModelOptions();
        assertEquals(ModelOptions.ToolChoice.AUTO, both.getToolChoice());
        assertEquals(ModelOptions.ReasoningReplay.ON, both.getReasoningReplay());
    }

    @Test
    void toModelSettings() {
        assertNull(ModelTuning.builder().build().toModelSettings());

        final var settings = ModelTuning.builder()
                .temperature(0.5f)
                .maxTokens(4096)
                .build()
                .toModelSettings();
        assertNotNull(settings);
        assertEquals(0.5f, settings.getTemperature());
        assertEquals(4096, settings.getMaxTokens());
    }
}
