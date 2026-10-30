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
package io.appform.sai.repl;

import static org.assertj.core.api.Assertions.assertThat;

import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.filesystem.session.FileSystemSessionStore;
import com.phonepe.sentinelai.session.QueryDirection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import lombok.SneakyThrows;

/**
 * Verifies the session-open replay cap: the read pattern used by
 * {@link ReplRunner#HISTORY_REPLAY_LIMIT} must return the last N messages
 * in chronological order, not the first N.
 */
class ReplRunnerReplayLimitTest {

    private static final String SESSION_ID = "test-replay-limit-session";

    @TempDir
    Path tempDir;

    @Test
    @SneakyThrows
    void replayLimitIsOneHundred() {
        assertThat(ReplRunner.HISTORY_REPLAY_LIMIT).isEqualTo(100);
    }

    @Test
    @SneakyThrows
    void replayReadReturnsTheLastMessagesInChronologicalOrder() {
        final var sessionStore = FileSystemSessionStore.builder()
                .baseDir(tempDir.resolve("sessions").toString())
                .mapper(JsonUtils.createMapper())
                .cacheSize(1)
                .build();
        final var messages = new java.util.ArrayList<com.phonepe.sentinelai.core.agentmessages.AgentMessage>();
        for (var i = 1; i <= 150; i++) {
            messages.add(UserPrompt.builder()
                    .sessionId(SESSION_ID)
                    .runId("run-1")
                    .content("message-" + i)
                    .build());
        }
        sessionStore.saveMessages(SESSION_ID, "run-1", messages);

        final var response = sessionStore.readMessages(SESSION_ID,
                                                       ReplRunner.HISTORY_REPLAY_LIMIT,
                                                       true,
                                                       null,
                                                       QueryDirection.OLDER);
        final var items = response.getItems();

        assertThat(items).hasSize(ReplRunner.HISTORY_REPLAY_LIMIT);
        assertThat(items).allSatisfy(message -> assertThat(message).isInstanceOf(UserPrompt.class));
        final var contents = items.stream()
                .map(message -> ((UserPrompt) message).getContent())
                .toList();
        assertThat(contents.get(0)).isEqualTo("message-51");
        assertThat(contents.get(contents.size() - 1)).isEqualTo("message-150");
    }
}
