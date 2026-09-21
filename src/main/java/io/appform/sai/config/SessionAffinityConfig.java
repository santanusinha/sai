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

import javax.annotation.Nullable;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Opt-in session cache affinity settings for a provider.
 *
 * <p>When set, SAI sends the current session id to the provider so that
 * provider-side prompt caches stay warm. Providers differ in the mechanism
 * they accept, so both the header name and the body field name are
 * configurable:
 *
 * <ul>
 * <li>OpenRouter: header {@code x-session-id} or body field {@code session_id}</li>
 * <li>OpenAI API: body field {@code prompt_cache_key} (no header)</li>
 * <li>Fireworks AI: header {@code x-session-affinity}</li>
 * <li>Anthropic-compatible gateways: header {@code X-Session-Id}</li>
 * </ul>
 *
 * <p>Both fields are optional. Set only the mechanism the provider supports.
 * When both are {@code null}, the feature is disabled.
 */
@Value
@Builder
@Jacksonized
public class SessionAffinityConfig {

    /**
     * HTTP header name that carries the session id (for example
     * {@code x-session-id}). When set, the header is added to every request.
     */
    @Nullable
    String header;

    /**
     * Top-level JSON body field that carries the session id in
     * {@code /v1/chat/completions} requests (for example {@code session_id}
     * for OpenRouter or {@code prompt_cache_key} for OpenAI). When set, the
     * field is injected into the request body.
     */
    @Nullable
    String bodyField;

    /**
     * Returns {@code true} when at least one affinity mechanism is configured.
     *
     * @return {@code true} when a header or body field is set
     */
    public boolean enabled() {
        return header != null && !header.isBlank() || bodyField != null && !bodyField.isBlank();
    }
}
