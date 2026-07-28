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
package io.appform.sai.cli.slash.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.filesystem.session.FileSystemSessionStore;
import com.phonepe.sentinelai.session.SessionSummary;

import io.appform.sai.AgentConfig;
import io.appform.sai.Printer;
import io.appform.sai.SaiAgent;
import io.appform.sai.Settings;
import io.appform.sai.agent.AgentFactory;
import io.appform.sai.cli.slash.SlashCommandContext;
import io.appform.sai.cli.slash.SlashCommandDispatcher;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import lombok.SneakyThrows;

class InfoCommandTest {

    private static final String INITIAL_MODEL = "copilot/claude-haiku-4.5";
    private static final String TEST_SESSION_ID = "test-info-session";

    private static class CapturingPrinter extends Printer {

        final List<Printer.Update> captured = new CopyOnWriteArrayList<>();

        @SneakyThrows
        CapturingPrinter() {
            super(Settings.builder().headless(true).build(),
                  Executors.newSingleThreadExecutor(),
                  null,
                  null);
        }

        @Override
        public void print(List<Printer.Update> updates) {
            captured.addAll(updates);
            super.print(updates);
        }
    }

    @TempDir
    Path tempDir;

    private CapturingPrinter printer;
    private SlashCommandDispatcher dispatcher;

    @Test
    void infoWithNoSessionDataDirPrintsNoSessionDataMessage() {
        final var context = SlashCommandContext.builder()
                .currentModel(new AtomicReference<>(INITIAL_MODEL))
                .currentMode(new AtomicReference<>(null))
                .currentAgentConfig(new AtomicReference<>(AgentConfig.builder()
                        .agentId("test")
                        .name("Test")
                        .description("Test agent")
                        .model(INITIAL_MODEL)
                        .build()))
                .currentAgent(new AtomicReference<>(mock(SaiAgent.class)))
                .agentFactory(mock(AgentFactory.class))
                .printer(printer)
                .settings(Settings.builder()
                        .headless(true)
                        .sessionId("nonexistent")
                        .dataDir(tempDir.resolve("no-such-dir").toString())
                        .build())
                .mapper(new ObjectMapper())
                .build();
        final var noDataDispatcher = new SlashCommandDispatcher(context);

        noDataDispatcher.dispatch("info", printer);
        assertTrue(capturedContains("No session data found"));
    }

    @Test
    void infoWithSessionSummaryAndMessagesPrintsFullDetails() {
        dispatcher.dispatch("info", printer);

        assertTrue(capturedContains("Session Summary for:"));
        assertTrue(capturedContains(TEST_SESSION_ID));
        assertTrue(capturedContains("Test Title"));
        assertTrue(capturedContains("A test conversation summary."));
        assertTrue(capturedContains("keyword1"));
        assertTrue(capturedContains("keyword2"));
        assertTrue(capturedContains("Message Stats:"));
        assertTrue(capturedContains("Total Messages:"));
        assertTrue(capturedContains("User Prompts:"));
        assertTrue(capturedContains("Text Responses:"));
        assertFalse(capturedContains("No session data found"));
        assertFalse(capturedContains("Session not found"));
    }

    @Test
    void infoWithSessionsDirButMissingSessionPrintsSessionNotFound() {
        final var context = SlashCommandContext.builder()
                .currentModel(new AtomicReference<>(INITIAL_MODEL))
                .currentMode(new AtomicReference<>(null))
                .currentAgentConfig(new AtomicReference<>(AgentConfig.builder()
                        .agentId("test")
                        .name("Test")
                        .description("Test agent")
                        .model(INITIAL_MODEL)
                        .build()))
                .currentAgent(new AtomicReference<>(mock(SaiAgent.class)))
                .agentFactory(mock(AgentFactory.class))
                .printer(printer)
                .settings(Settings.builder()
                        .headless(true)
                        .sessionId("nonexistent-session-id")
                        .dataDir(tempDir.toString())
                        .build())
                .mapper(new ObjectMapper())
                .build();
        final var notFoundDispatcher = new SlashCommandDispatcher(context);

        notFoundDispatcher.dispatch("info", printer);
        assertTrue(capturedContains("Session not found"));
    }

    @BeforeEach
    @SneakyThrows
    void setUp() {
        final var mapper = JsonUtils.createMapper();
        final var sessionStore = FileSystemSessionStore.builder()
                .baseDir(tempDir.resolve("sessions").toString())
                .mapper(mapper)
                .cacheSize(1)
                .build();

        final var summary = SessionSummary.builder()
                .sessionId(TEST_SESSION_ID)
                .title("Test Title")
                .summary("A test conversation summary.")
                .keywords(List.of("keyword1", "keyword2"))
                .lastSummarizedMessageId("msg-001")
                .updatedAt(System.currentTimeMillis() * 1000L)
                .build();
        sessionStore.saveSession(summary);

        sessionStore.saveMessages(TEST_SESSION_ID,
                                  "run-1",
                                  List.of(
                                          UserPrompt.builder()
                                                  .sessionId(TEST_SESSION_ID)
                                                  .runId("run-1")
                                                  .content("Hello, what can you do?")
                                                  .build(),
                                          Text.builder()
                                                  .sessionId(TEST_SESSION_ID)
                                                  .runId("run-1")
                                                  .content("I can help you with coding tasks!")
                                                  .stats(new ModelUsageStats())
                                                  .elapsedTimeMs(100)
                                                  .build()));

        printer = new CapturingPrinter();
        printer.start();

        final var agentFactory = mock(AgentFactory.class);
        final var mockAgent = mock(SaiAgent.class);
        when(agentFactory.createAgent(any(), any(), any(), any())).thenReturn(mockAgent);

        final var agentConfig = AgentConfig.builder()
                .agentId("test")
                .name("Test")
                .description("Test agent")
                .model(INITIAL_MODEL)
                .build();

        final var settings = Settings.builder()
                .headless(true)
                .sessionId(TEST_SESSION_ID)
                .dataDir(tempDir.toString())
                .build();

        final var context = SlashCommandContext.builder()
                .currentModel(new AtomicReference<>(INITIAL_MODEL))
                .currentMode(new AtomicReference<>(null))
                .currentAgentConfig(new AtomicReference<>(agentConfig))
                .currentAgent(new AtomicReference<>(mockAgent))
                .agentFactory(agentFactory)
                .printer(printer)
                .settings(settings)
                .mapper(new ObjectMapper())
                .build();

        dispatcher = new SlashCommandDispatcher(context);
    }

    @AfterEach
    @SneakyThrows
    void tearDown() {
        printer.close();
    }

    private boolean capturedContains(String substring) {
        try {
            Thread.sleep(200);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return printer.captured.stream()
                .anyMatch(u -> u.getData() != null && u.getData().contains(substring));
    }
}
