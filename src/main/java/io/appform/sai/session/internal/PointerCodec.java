/*
 * Copyright (c) 2026 Original Author(s)
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
package io.appform.sai.session.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.utils.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class PointerCodec {
    private static final ObjectMapper mapper = JsonUtils.createMapper();

    public static class Anchor {
        public final long ts;
        public final String id;

        public Anchor(long ts, String id) {
            this.ts = ts;
            this.id = id;
        }
    }

    public static class SessionAnchor {
        public final long updatedAt;
        public final String sessionId;

        public SessionAnchor(long updatedAt, String sessionId) {
            this.updatedAt = updatedAt;
            this.sessionId = sessionId;
        }
    }

    public static Anchor decodeMessagePointer(String pointer) {
        if (pointer == null || pointer.isEmpty()) return null;
        try {
            String json = new String(Base64.getDecoder().decode(pointer), StandardCharsets.UTF_8);
            JsonNode node = mapper.readTree(json);
            long ts = node.get("ts").asLong();
            String id = node.get("id").asText();
            return new Anchor(ts, id);
        }
        catch (Exception e) {
            return null;
        }
    }

    public static SessionAnchor decodeSessionPointer(String pointer) {
        if (pointer == null || pointer.isEmpty()) return null;
        try {
            String json = new String(Base64.getDecoder().decode(pointer), StandardCharsets.UTF_8);
            JsonNode node = mapper.readTree(json);
            long updatedAt = node.get("updatedAt").asLong();
            String sessionId = node.get("sessionId").asText();
            return new SessionAnchor(updatedAt, sessionId);
        }
        catch (Exception e) {
            return null;
        }
    }

    public static String encodeMessagePointer(long ts, String id) {
        try {
            String json = String.format("{\"ts\":%d,\"id\":\"%s\",\"ver\":1}", ts, id);
            return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        }
        catch (Exception e) {
            return null;
        }
    }

    public static String encodeSessionPointer(Long updatedAt, String sessionId) {
        long ts = updatedAt == null ? 0 : updatedAt;
        try {
            String json = String.format("{\"updatedAt\":%d,\"sessionId\":\"%s\",\"ver\":1}", ts, sessionId);
            return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        }
        catch (Exception e) {
            return null;
        }
    }
}
