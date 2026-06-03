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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.utils.JsonUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class JoltRequestTransformEngineTest {

    private ObjectMapper mapper;
    private JoltRequestTransformEngine engine;

    @BeforeEach
    void setUp() {
        mapper = JsonUtils.createMapper();
        engine = new JoltRequestTransformEngine(mapper);
    }

    @Test
    void testDefaultOperationAddsNestedField() {
        var payload = mapper.createObjectNode();
        payload.put("model", "gpt-4");

        var transforms = List.of(RequestTransform.builder()
                .operation("default")
                .spec(Map.of("chat_template_kwargs", Map.of("thinking", false)))
                .build());

        var result = engine.apply(payload, transforms);

        assertNotNull(result.get("chat_template_kwargs"));
        assertFalse(result.get("chat_template_kwargs").get("thinking").asBoolean());
        assertEquals("gpt-4", result.get("model").asText());
    }

    @Test
    void testDefaultOperationDoesNotOverwriteExistingField() {
        var payload = mapper.createObjectNode();
        var chatTemplate = mapper.createObjectNode();
        chatTemplate.put("thinking", true);
        payload.set("chat_template_kwargs", chatTemplate);

        var transforms = List.of(RequestTransform.builder()
                .operation("default")
                .spec(Map.of("chat_template_kwargs", Map.of("thinking", false)))
                .build());

        var result = engine.apply(payload, transforms);

        assertTrue(result.get("chat_template_kwargs").get("thinking").asBoolean());
    }

    @Test
    void testEmptyTransformListReturnsPayloadUnchanged() {
        var payload = mapper.createObjectNode();
        payload.put("model", "gpt-4");

        var result = engine.apply(payload, List.of());

        assertEquals("gpt-4", result.get("model").asText());
    }

    @Test
    void testMultipleTransformsAppliedInOrder() {
        var payload = mapper.createObjectNode();
        payload.put("model", "gpt-4");
        payload.put("field_to_remove", "value");

        var transforms = List.of(
                                 RequestTransform.builder()
                                         .operation("remove")
                                         .spec(Map.of("field_to_remove", ""))
                                         .build(),
                                 RequestTransform.builder()
                                         .operation("default")
                                         .spec(Map.of("chat_template_kwargs", Map.of("thinking", false)))
                                         .build()
        );

        var result = engine.apply(payload, transforms);

        assertFalse(result.has("field_to_remove"));
        assertNotNull(result.get("chat_template_kwargs"));
        assertFalse(result.get("chat_template_kwargs").get("thinking").asBoolean());
    }

    @Test
    void testNullTransformListReturnsPayloadUnchanged() {
        var payload = mapper.createObjectNode();
        payload.put("model", "gpt-4");

        var result = engine.apply(payload, null);

        assertEquals("gpt-4", result.get("model").asText());
    }

    @Test
    void testRemoveOperationDeletesField() {
        var payload = mapper.createObjectNode();
        payload.put("model", "gpt-4");
        payload.put("unwanted_field", "value");

        var transforms = List.of(RequestTransform.builder()
                .operation("remove")
                .spec(Map.of("unwanted_field", ""))
                .build());

        var result = engine.apply(payload, transforms);

        assertFalse(result.has("unwanted_field"));
        assertEquals("gpt-4", result.get("model").asText());
    }
}
