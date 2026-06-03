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
package io.appform.sai.transform;

import java.util.Map;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Represents a single Jolt transform operation to be applied to an outgoing
 * LLM request payload.
 *
 * <p>The {@code operation} field must be a valid Jolt operation name
 * (e.g. {@code default}, {@code add}, {@code remove}, {@code modify},
 * {@code shift}, {@code cardinality}, {@code sort}).
 *
 * <p>The {@code spec} field is the Jolt spec map for that operation.
 * For example, to set {@code chat_template_kwargs.thinking = false}:
 * <pre>
 * operation: "default"
 * spec:
 * chat_template_kwargs:
 * thinking: false
 * </pre>
 */
@Value
@Builder
@Jacksonized
public class RequestTransform {

    @NonNull
    String operation;

    @NonNull
    Map<String, Object> spec;
}
