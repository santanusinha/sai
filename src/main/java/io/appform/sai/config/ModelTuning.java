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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.knuddels.jtokkit.api.EncodingType;
import com.phonepe.sentinelai.core.model.ModelAttributes;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.Reasoning;
import com.phonepe.sentinelai.models.SimpleOpenAIModelOptions;

import java.time.Duration;
import java.util.Map;

import javax.annotation.Nullable;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Shared tuning data model used in both {@code settings.yaml} (provider/model/mode levels) and
 * persona files ({@code tuning} section).
 *
 * <p>Carries all model-tuning fields — those from {@link ModelSettings}, the nested
 * {@link ModelAttributes}, and {@link SimpleOpenAIModelOptions} — in a single flat structure
 * suitable for YAML/JSON configuration.
 *
 * <p>Provides:
 * <ul>
 * <li>{@link #merge(ModelTuning, ModelTuning)} — chains the framework merge methods
 * ({@code ModelSettings.merge}, {@code ModelAttributes.merge},
 * {@code SimpleOpenAIModelOptions.merge}). <strong>rhs wins if non-null</strong>.</li>
 * <li>{@link #toModelSettings()} — converts to {@link ModelSettings}.</li>
 * <li>{@link #toModelAttributes()} — converts to {@link ModelAttributes}.</li>
 * <li>{@link #toModelOptions()} — converts to {@link SimpleOpenAIModelOptions}.</li>
 * </ul>
 */
@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelTuning {

    // ── ModelSettings fields ──────────────────────────────────────────────

    @Nullable
    Integer maxTokens;

    @Nullable
    Float temperature;

    @Nullable
    Float topP;

    @Nullable
    Duration timeout;

    @Nullable
    Boolean parallelToolCalls;

    @Nullable
    Integer seed;

    @Nullable
    Float presencePenalty;

    @Nullable
    Float frequencyPenalty;

    @Nullable
    Map<String, Integer> logitBias;

    @Nullable
    Reasoning reasoning;

    // ── ModelAttributes fields ────────────────────────────────────────────

    @Nullable
    EncodingType encodingType;

    @Nullable
    Integer contextWindowSize;

    // ── SimpleOpenAIModelOptions fields ───────────────────────────────────

    @Nullable
    @JsonProperty("toolChoice")
    SimpleOpenAIModelOptions.ToolChoice toolChoice;

    // ── Free-form passthrough ─────────────────────────────────────────────

    @Nullable
    Map<String, String> extraArgs;

    /**
     * Merges two {@link ModelTuning} instances, chaining the framework merge methods.
     *
     * <p>Semantics: <strong>rhs wins if non-null</strong> for each field. For
     * {@link ModelAttributes} the framework's sentinel-based merge applies (lhs wins if
     * non-default, else rhs). For maps ({@code logitBias}, {@code extraArgs}) the rhs map
     * replaces the lhs map entirely if non-null.
     *
     * @param lhs left-hand side tuning (lower precedence); may be {@code null}
     * @param rhs right-hand side tuning (higher precedence); may be {@code null}
     * @return the merged tuning, or {@code null} if both inputs are {@code null}
     */
    public static ModelTuning merge(@Nullable ModelTuning lhs, @Nullable ModelTuning rhs) {
        if (lhs == null) {
            return rhs;
        }
        if (rhs == null) {
            return lhs;
        }

        final var mergedSettings = ModelSettings.merge(lhs.toModelSettings(), rhs.toModelSettings());
        final var lhsOptions = lhs.toModelOptions();
        final var rhsOptions = rhs.toModelOptions();
        final var mergedOptions = lhsOptions.merge(rhsOptions);

        return ModelTuning.builder()
                .maxTokens(mergedSettings.getMaxTokens())
                .temperature(mergedSettings.getTemperature())
                .topP(mergedSettings.getTopP())
                .timeout(mergedSettings.getTimeout())
                .parallelToolCalls(mergedSettings.getParallelToolCalls())
                .seed(mergedSettings.getSeed())
                .presencePenalty(mergedSettings.getPresencePenalty())
                .frequencyPenalty(mergedSettings.getFrequencyPenalty())
                .logitBias(mergedSettings.getLogitBias())
                .reasoning(mergedSettings.getReasoning())
                .encodingType(rhs.getEncodingType() != null ? rhs.getEncodingType() : lhs.getEncodingType())
                .contextWindowSize(rhs.getContextWindowSize() != null
                        ? rhs.getContextWindowSize()
                        : lhs.getContextWindowSize())
                .toolChoice(mergedOptions.getToolChoice())
                .extraArgs(rhs.getExtraArgs() != null ? rhs.getExtraArgs() : lhs.getExtraArgs())
                .build();
    }

    /**
     * Checks whether this tuning instance has no non-null fields.
     *
     * @return {@code true} if all tuning fields are null
     */
    public boolean isEmpty() {
        return maxTokens == null
                && temperature == null
                && topP == null
                && timeout == null
                && parallelToolCalls == null
                && seed == null
                && presencePenalty == null
                && frequencyPenalty == null
                && logitBias == null
                && reasoning == null
                && encodingType == null
                && contextWindowSize == null
                && toolChoice == null
                && (extraArgs == null || extraArgs.isEmpty());
    }

    /**
     * Converts this tuning to a {@link ModelAttributes} instance.
     *
     * @return a {@link ModelAttributes} with the encoding type and context window size,
     *         or {@code null} if neither is set
     */
    @Nullable
    public ModelAttributes toModelAttributes() {
        if (encodingType == null && contextWindowSize == null) {
            return null;
        }
        final var builder = ModelAttributes.builder();
        if (encodingType != null) {
            builder.encodingType(encodingType);
        }
        if (contextWindowSize != null) {
            builder.contextWindowSize(contextWindowSize);
        }
        return builder.build();
    }

    /**
     * Converts this tuning to a {@link SimpleOpenAIModelOptions} instance.
     *
     * @return a {@link SimpleOpenAIModelOptions} with the tool choice, or
     *         {@link SimpleOpenAIModelOptions#DEFAULT} if toolChoice is null
     */
    public SimpleOpenAIModelOptions toModelOptions() {
        if (toolChoice == null) {
            return SimpleOpenAIModelOptions.DEFAULT;
        }
        return new SimpleOpenAIModelOptions(toolChoice, null);
    }

    /**
     * Converts this tuning to a {@link ModelSettings} instance.
     *
     * <p>Returns {@code null} if no relevant fields are set (all null), so that callers can
     * distinguish "no tuning" from "tuning with framework defaults".
     *
     * @return a {@link ModelSettings} populated from this tuning, or {@code null} if empty
     */
    @Nullable
    public ModelSettings toModelSettings() {
        if (isEmpty()) {
            return null;
        }
        return ModelSettings.builder()
                .maxTokens(maxTokens)
                .temperature(temperature)
                .topP(topP)
                .timeout(timeout)
                .parallelToolCalls(parallelToolCalls)
                .seed(seed)
                .presencePenalty(presencePenalty)
                .frequencyPenalty(frequencyPenalty)
                .logitBias(logitBias)
                .reasoning(reasoning)
                .modelAttributes(toModelAttributes())
                .build();
    }
}
