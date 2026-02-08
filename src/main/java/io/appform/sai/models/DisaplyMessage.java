/*
 * Copyright 2026 authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.appform.sai.models;

import java.time.LocalDateTime;
import java.util.UUID;

import com.phonepe.sentinelai.core.errors.SentinelError;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.With;

@Value
@Builder
@With
public class DisaplyMessage {

    public enum MessageType {
        USER_MESSAGE,
        MODEL_RESPONSE,
        MODEL_ERROR,
        TOOL_CALL,
        TOOL_OUTPUT,
        EVENT
    }

    @NonNull
    MessageType type;

    @NonNull
    String messageId;

    @NonNull
    String sessionId;

    String ruinId;

    @NonNull
    Actor actor;

    @NonNull
    Severity severity;

    @NonNull
    String content;

    @Builder.Default
    LocalDateTime timestamp = LocalDateTime.now();

    public static DisaplyMessage userMessage(String sessionId, String runId, String content) {
        return DisaplyMessage.builder()
                .type(MessageType.USER_MESSAGE)
                .messageId(generateMessageId(sessionId, runId))
                .sessionId(sessionId)
                .ruinId(runId)
                .actor(Actor.USER)
                .severity(Severity.INFO)
                .content(content)
                .build();
    }

    public static DisaplyMessage modelSuccess(String sessionId, String runId, String content) {
        return DisaplyMessage.builder()
                .type(MessageType.MODEL_RESPONSE)
                .messageId(generateMessageId(sessionId, runId))
                .sessionId(sessionId)
                .ruinId(runId)
                .actor(Actor.ASSISTANT)
                .severity(Severity.SUCCESS)
                .content(content)
                .build();
    }

    public static DisaplyMessage modelError(String sessionId, String runId, final String error) {
        return DisaplyMessage.builder()
                .type(MessageType.MODEL_RESPONSE)
                .messageId(generateMessageId(sessionId, runId))
                .sessionId(sessionId)
                .ruinId(runId)
                .actor(Actor.ASSISTANT)
                .severity(Severity.ERROR)
                .content(error)
                .build();
    }

    public static DisaplyMessage toolCall(String sessionId, String runId, String content) {
        return DisaplyMessage.builder()
                .type(MessageType.TOOL_CALL)
                .messageId(generateMessageId(sessionId, runId))
                .sessionId(sessionId)
                .ruinId(runId)
                .actor(Actor.ASSISTANT)
                .severity(Severity.INFO)
                .content(content)
                .build();
    }

    public static DisaplyMessage toolOutput(String sessionId, String runId, String content) {
        return DisaplyMessage.builder()
                .type(MessageType.TOOL_OUTPUT)
                .messageId(generateMessageId(sessionId, runId))
                .sessionId(sessionId)
                .ruinId(runId)
                .actor(Actor.SYSTEM)
                .severity(Severity.INFO)
                .content(content)
                .build();
    }

    private static String generateMessageId(String sessionId, String runId) {
        return "msg-" + UUID.nameUUIDFromBytes("%s-%s-%d".formatted(sessionId, runId, System.currentTimeMillis()).getBytes());
    }

}
