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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.session.AgentSessionExtension;
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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import lombok.SneakyThrows;

class CompactCommandTest {

    private static final String INITIAL_MODEL = "copilot/claude-haiku-4.5";

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

    private CapturingPrinter printer;
    private SlashCommandDispatcher dispatcherWithExtension;
    private SlashCommandDispatcher dispatcherNoExtension;

    @Test
    void compactWithExtensionAndEmptyResultPrintsNoSummaryMessage() {
        dispatcherWithExtension.dispatch("compact", printer);
        assertTrue(capturedContains("no summary was produced"));
    }

    @Test
    void compactWithExtensionAndSummaryPrintsSuccess() {
        // Re-setup with a session extension that returns a non-empty summary
        final var agentFactory = mock(AgentFactory.class);
        final var mockAgent = mock(SaiAgent.class);
        when(agentFactory.createAgent(any(), any(), any(), any())).thenReturn(mockAgent);

        final var agentConfig = AgentConfig.builder()
                .agentId("test")
                .name("Test Agent")
                .description("Test agent")
                .model(INITIAL_MODEL)
                .build();

        final var settings = Settings.builder().headless(true).build();

        final var sessionExtension = mock(AgentSessionExtension.class);
        final var summary = SessionSummary.builder()
                .sessionId("test-session")
                .title("Test Summary")
                .summary("A brief summary of the conversation.")
                .updatedAt(System.currentTimeMillis())
                .build();
        when(sessionExtension.forceCompaction(any()))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(summary)));

        final var context = SlashCommandContext.builder()
                .currentModel(new AtomicReference<>(INITIAL_MODEL))
                .currentMode(new AtomicReference<>(null))
                .currentAgentConfig(new AtomicReference<>(agentConfig))
                .currentAgent(new AtomicReference<>(mockAgent))
                .agentFactory(agentFactory)
                .printer(printer)
                .settings(settings)
                .mapper(new ObjectMapper())
                .sessionExtension(sessionExtension)
                .build();
        final var dispatcher = new SlashCommandDispatcher(context);

        dispatcher.dispatch("compact", printer);
        assertTrue(capturedContains("Session compacted"));
        assertTrue(capturedContains("A brief summary of the conversation."));
    }

    @Test
    void compactWithExtensionPrintsCompactingMessage() {
        dispatcherWithExtension.dispatch("compact", printer);
        assertTrue(capturedContains("Compacting session"));
    }

    @Test
    void compactWithNoExtensionPrintsInfoMessage() {
        dispatcherNoExtension.dispatch("compact", printer);
        assertTrue(capturedContains("No session extension"));
    }

    @BeforeEach
    @SneakyThrows
    void setUp() {
        final var agentFactory = mock(AgentFactory.class);
        final var mockAgent = mock(SaiAgent.class);
        when(agentFactory.createAgent(any(), any(), any(), any())).thenReturn(mockAgent);

        printer = new CapturingPrinter();
        printer.start();

        final var agentConfig = AgentConfig.builder()
                .agentId("test")
                .name("Test Agent")
                .description("Test agent")
                .model(INITIAL_MODEL)
                .build();

        final var settings = Settings.builder().headless(true).build();

        // Mock session extension that returns an empty Optional (no messages to compact)
        final var sessionExtension = mock(AgentSessionExtension.class);
        when(sessionExtension.forceCompaction(any()))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        final var contextWithExtension = SlashCommandContext.builder()
                .currentModel(new AtomicReference<>(INITIAL_MODEL))
                .currentMode(new AtomicReference<>(null))
                .currentAgentConfig(new AtomicReference<>(agentConfig))
                .currentAgent(new AtomicReference<>(mockAgent))
                .agentFactory(agentFactory)
                .printer(printer)
                .settings(settings)
                .mapper(new ObjectMapper())
                .sessionExtension(sessionExtension)
                .build();
        dispatcherWithExtension = new SlashCommandDispatcher(contextWithExtension);

        final var contextNoExtension = SlashCommandContext.builder()
                .currentModel(new AtomicReference<>(INITIAL_MODEL))
                .currentMode(new AtomicReference<>(null))
                .currentAgentConfig(new AtomicReference<>(agentConfig))
                .currentAgent(new AtomicReference<>(mockAgent))
                .agentFactory(agentFactory)
                .printer(printer)
                .settings(settings)
                .mapper(new ObjectMapper())
                .build();
        dispatcherNoExtension = new SlashCommandDispatcher(contextNoExtension);
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
