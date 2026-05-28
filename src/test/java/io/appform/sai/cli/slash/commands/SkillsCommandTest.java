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
package io.appform.sai.cli.slash.commands;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.filesystem.skills.AgentSkillsExtension;

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

class SkillsCommandTest {

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

    private CapturingPrinter printer;
    private SlashCommandDispatcher dispatcherWithSkills;
    private SlashCommandDispatcher dispatcherNoSkills;

    @BeforeEach
    @SneakyThrows
    void setUp() {
        final var agentFactory = mock(AgentFactory.class);
        final var mockAgent = mock(SaiAgent.class);
        when(agentFactory.createAgent(any(), any())).thenReturn(mockAgent);

        printer = new CapturingPrinter();
        printer.start();

        final var agentConfig = AgentConfig.builder()
                .agentId("test")
                .name("Test Agent")
                .description("Test agent")
                .model(INITIAL_MODEL)
                .build();

        final var settings = Settings.builder().headless(true).build();

        // Build a real skills extension from the test-skills directory
        final var skillsDir = Path.of("src/test/resources/test-skills");
        final var skillsDirAbs = skillsDir.toAbsolutePath().normalize().toString();
        final var skillsExtension = AgentSkillsExtension.<String, String, SaiAgent>withMultipleSkills()
                .baseDir(skillsDirAbs)
                .skillsDirectories(List.of(skillsDirAbs))
                .skillsToLoad(List.of())
                .build();

        final var contextWithSkills = SlashCommandContext.builder()
                .currentModel(new AtomicReference<>(INITIAL_MODEL))
                .currentAgentConfig(new AtomicReference<>(agentConfig))
                .currentAgent(new AtomicReference<>(mockAgent))
                .agentFactory(agentFactory)
                .printer(printer)
                .settings(settings)
                .mapper(new ObjectMapper())
                .agentSkillsExtension(skillsExtension)
                .build();
        dispatcherWithSkills = new SlashCommandDispatcher(contextWithSkills);

        final var contextNoSkills = SlashCommandContext.builder()
                .currentModel(new AtomicReference<>(INITIAL_MODEL))
                .currentAgentConfig(new AtomicReference<>(agentConfig))
                .currentAgent(new AtomicReference<>(mockAgent))
                .agentFactory(agentFactory)
                .printer(printer)
                .settings(settings)
                .mapper(new ObjectMapper())
                .build();
        dispatcherNoSkills = new SlashCommandDispatcher(contextNoSkills);
    }

    @Test
    void skillsListsAvailableSkills() {
        dispatcherWithSkills.dispatch("skills", printer);
        assertTrue(capturedContains("example-skill"));
    }

    @Test
    void skillsNoExtensionPrintsInfoMessage() {
        dispatcherNoSkills.dispatch("skills", printer);
        assertTrue(capturedContains("No skills extension"));
    }

    @Test
    void skillsShowsDescriptionInCatalog() {
        dispatcherWithSkills.dispatch("skills", printer);
        assertTrue(capturedContains("Available skills:"));
    }

    @AfterEach
    @SneakyThrows
    void tearDown() {
        printer.close();
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
