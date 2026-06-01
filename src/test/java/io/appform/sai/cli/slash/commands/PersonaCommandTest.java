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
import com.phonepe.sentinelai.core.utils.JsonUtils;

import io.appform.sai.AgentConfig;
import io.appform.sai.Printer;
import io.appform.sai.SaiAgent;
import io.appform.sai.Settings;
import io.appform.sai.agent.AgentFactory;
import io.appform.sai.cli.slash.SlashCommandContext;
import io.appform.sai.cli.slash.SlashCommandDispatcher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import lombok.SneakyThrows;

class PersonaCommandTest {

    private static final String INITIAL_PERSONA = "Default Persona";

    private static final String INITIAL_MODEL = "copilot-proxy/claude-haiku-4.5";

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

    private AgentFactory agentFactory;
    private SaiAgent mockAgent;
    private CapturingPrinter printer;
    private SlashCommandContext context;
    private SlashCommandDispatcher dispatcher;
    private ObjectMapper mapper;

    @Test
    void personaNoArgsPrintsCurrentPersonaName() {
        dispatcher.dispatch("persona", printer);
        assertTrue(capturedContains(INITIAL_PERSONA));
    }

    @Test
    void personaNonexistentPathPrintsError() {
        dispatcher.dispatch("persona /does/not/exist/file.yaml", printer);

        verify(agentFactory, never()).createAgent(any(), any());
        assertTrue(capturedContains("Failed"));
    }

    @Test
    @SneakyThrows
    void personaValidPathLoadsConfig() {
        final var personaFile = tempDir.resolve("my-persona.json");
        final var newConfig = AgentConfig.builder()
                .agentId("new-agent")
                .name("New Persona")
                .description("A different persona")
                .model("copilot-proxy/gpt-4o")
                .build();
        Files.writeString(personaFile, mapper.writeValueAsString(newConfig));

        dispatcher.dispatch("persona " + personaFile.toAbsolutePath(), printer);

        assertEquals("New Persona", context.getCurrentAgentConfig().get().getName());
        assertEquals("copilot-proxy/gpt-4o", context.getCurrentModel().get());
        verify(agentFactory).createAgent(any(), any());
        assertTrue(capturedContains("loaded"));
    }

    @BeforeEach
    @SneakyThrows
    void setUp() {
        agentFactory = mock(AgentFactory.class);
        mockAgent = mock(SaiAgent.class);
        when(agentFactory.createAgent(any(), any())).thenReturn(mockAgent);

        printer = new CapturingPrinter();
        printer.start();

        mapper = JsonUtils.createMapper();

        final var agentConfig = AgentConfig.builder()
                .agentId("test")
                .name(INITIAL_PERSONA)
                .description("Test agent")
                .model(INITIAL_MODEL)
                .build();

        final var settings = Settings.builder()
                .headless(true)
                .configDir(tempDir.toString())
                .build();

        context = SlashCommandContext.builder()
                .currentModel(new AtomicReference<>(INITIAL_MODEL))
                .currentAgentConfig(new AtomicReference<>(agentConfig))
                .currentAgent(new AtomicReference<>(mockAgent))
                .agentFactory(agentFactory)
                .printer(printer)
                .settings(settings)
                .mapper(mapper)
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
