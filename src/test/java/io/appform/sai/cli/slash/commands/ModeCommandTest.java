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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.appform.sai.AgentConfig;
import io.appform.sai.Printer;
import io.appform.sai.SaiAgent;
import io.appform.sai.Settings;
import io.appform.sai.agent.AgentFactory;
import io.appform.sai.cli.slash.SlashCommandContext;
import io.appform.sai.cli.slash.SlashCommandDispatcher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import lombok.SneakyThrows;

class ModeCommandTest {

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

    private AgentFactory agentFactory;
    private SaiAgent mockAgent;
    private CapturingPrinter printer;
    private SlashCommandContext context;
    private SlashCommandDispatcher dispatcher;

    @Test
    void modeNoArgsPrintsCurrentMode() {
        context.getCurrentMode().set("coding");
        dispatcher.dispatch("mode", printer);
        assertTrue(capturedContains("coding"));
    }

    @Test
    void modeNoArgsPrintsNoneWhenNoMode() {
        dispatcher.dispatch("mode", printer);
        assertTrue(capturedContains("(none)"));
    }

    @Test
    void modeWithArgDoesNotChangeModel() {
        final var originalModel = context.getCurrentModel().get();
        dispatcher.dispatch("mode planning", printer);

        assertEquals(originalModel, context.getCurrentModel().get());
        assertEquals("planning", context.getCurrentMode().get());
    }

    @Test
    void modeWithArgSetsModeAndRebuilds() {
        dispatcher.dispatch("mode coding", printer);

        assertEquals("coding", context.getCurrentMode().get());
        verify(agentFactory).createAgent(any(), any(), any(), any());
        assertTrue(capturedContains("switched"));
    }

    @Test
    void modeWithBlankArgDoesNothing() {
        dispatcher.dispatch("mode ", printer);
        verify(agentFactory, never()).createAgent(any(), any(), any(), any());
    }

    @BeforeEach
    @SneakyThrows
    void setUp() {
        agentFactory = mock(AgentFactory.class);
        mockAgent = mock(SaiAgent.class);
        when(agentFactory.createAgent(any(), any(), any(), any())).thenReturn(mockAgent);

        printer = new CapturingPrinter();
        printer.start();

        final var agentConfig = AgentConfig.builder()
                .agentId("test")
                .name("Test")
                .description("Test agent")
                .model(INITIAL_MODEL)
                .build();

        context = SlashCommandContext.builder()
                .currentModel(new AtomicReference<>(INITIAL_MODEL))
                .currentMode(new AtomicReference<>(null))
                .currentAgentConfig(new AtomicReference<>(agentConfig))
                .currentAgent(new AtomicReference<>(mockAgent))
                .agentFactory(agentFactory)
                .printer(printer)
                .settings(Settings.builder().headless(true).build())
                .mapper(new ObjectMapper())
                .build();

        dispatcher = new SlashCommandDispatcher(context);
    }

    private boolean capturedContains(String substring) {
        try {
            Thread.sleep(150);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return printer.captured.stream()
                .anyMatch(u -> u.getData() != null && u.getData().contains(substring));
    }
}
