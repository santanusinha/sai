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
package io.appform.sai;

import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.models.wire.WireLoggingMode;

/**
 * Seam that maps a model name to a concrete {@link Model}.
 *
 * <p>Implementations resolve provider configuration (settings.yaml or environment variables)
 * and return a fully wired model. Extra request transformers produced by the caller are
 * attached as model-level transformers on the returned model.
 */
public interface ModelFactory {

    /**
     * Builds a model for the given model name.
     *
     * @param modelName  the display name of the model
     * @param apiModelId the model id sent on the wire (falls back to {@code modelName})
     * @return a wired model instance
     */
    Model model(String modelName, String apiModelId);

    /**
     * Builds a model for the given model name with a protocol override.
     *
     * @param modelName        the display name of the model
     * @param apiModelId       the model id sent on the wire (falls back to {@code modelName})
     * @param protocolOverride wire protocol name (for example {@code responses}), or null
     *                         for the provider default; unsupported names fail fast
     * @return a wired model instance
     */
    default Model model(String modelName, String apiModelId, String protocolOverride) {
        if (protocolOverride == null || protocolOverride.isBlank()) {
            return model(modelName, apiModelId);
        }
        throw new IllegalArgumentException("Provider '" + getClass().getSimpleName()
                + "' does not support a protocol override");
    }

    /**
     * Builds a model for the given model name with a protocol override and a wire
     * logging mode. The default implementation forwards the protocol override to
     * {@link #model(String, String, String)} when the wire logging mode is null or
     * {@link WireLoggingMode#ON}. Factories that support wire logging override this
     * method; other factories fail fast when a non-default mode is requested.
     *
     * @param modelName        the display name of the model
     * @param apiModelId       the model id sent on the wire (falls back to {@code modelName})
     * @param protocolOverride wire protocol name (for example {@code responses}), or null
     *                         for the provider default; unsupported names fail fast
     * @param wireLogging      the wire payload logging mode, or null for the default
     * @return a wired model instance
     */
    default Model model(String modelName, String apiModelId, String protocolOverride, WireLoggingMode wireLogging) {
        if (wireLogging == null || wireLogging == WireLoggingMode.ON) {
            return model(modelName, apiModelId, protocolOverride);
        }
        throw new IllegalArgumentException("Provider '" + getClass().getSimpleName()
                + "' does not support a wire logging override");
    }
}
