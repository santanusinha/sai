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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import lombok.SneakyThrows;

class ProvidersCommandTest {

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

    @TempDir
    Path tempDir;

    private CapturingPrinter printer;
    private SlashCommandDispatcher dispatcherWithProviders;
    private SlashCommandDispatcher dispatcherNoProviders;

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

        // Config dir WITH a real settings.yaml
        final var settingsYaml = """
                providers:
                  my-provider:
                    type: openai
                    endpoint: https://api.example.com/v1
                    apiKey: ${MY_KEY}
                    models:
                      my-model:
                        tuning:
                          temperature: 0.7
                        modes:
                          coding:
                            tuning:
                              temperature: 0.3
                          planning:
                            tuning:
                              temperature: 0.5
                """;
        Files.writeString(tempDir.resolve("settings.yaml"), settingsYaml);

        final var settingsWithProviders = Settings.builder()
                .headless(true)
                .configDir(tempDir.toAbsolutePath().toString())
                .build();

        final var contextWithProviders = SlashCommandContext.builder()
                .currentModel(new AtomicReference<>(INITIAL_MODEL))
                .currentMode(new AtomicReference<>(null))
                .currentAgentConfig(new AtomicReference<>(agentConfig))
                .currentAgent(new AtomicReference<>(mockAgent))
                .agentFactory(agentFactory)
                .printer(printer)
                .settings(settingsWithProviders)
                .mapper(new ObjectMapper())
                .build();
        dispatcherWithProviders = new SlashCommandDispatcher(contextWithProviders);

        // Empty config dir — no settings.yaml
        final var emptyDir = tempDir.resolve("empty");
        Files.createDirectories(emptyDir);
        final var settingsEmpty = Settings.builder()
                .headless(true)
                .configDir(emptyDir.toAbsolutePath().toString())
                .build();

        final var contextNoProviders = SlashCommandContext.builder()
                .currentModel(new AtomicReference<>(INITIAL_MODEL))
                .currentMode(new AtomicReference<>(null))
                .currentAgentConfig(new AtomicReference<>(agentConfig))
                .currentAgent(new AtomicReference<>(mockAgent))
                .agentFactory(agentFactory)
                .printer(printer)
                .settings(settingsEmpty)
                .mapper(new ObjectMapper())
                .build();
        dispatcherNoProviders = new SlashCommandDispatcher(contextNoProviders);
    }

    @Test
    void providersAlwaysShowsCopilotBuiltIn() {
        dispatcherWithProviders.dispatch("providers", printer);
        assertTrue(capturedContains("copilot"));
    }

    @Test
    void providersListsConfiguredProvider() {
        dispatcherWithProviders.dispatch("providers", printer);
        assertTrue(capturedContains("my-provider"));
    }

    @Test
    void providersListsModel() {
        dispatcherWithProviders.dispatch("providers", printer);
        assertTrue(capturedContains("my-model"));
    }

    @Test
    void providersListsModes() {
        dispatcherWithProviders.dispatch("providers", printer);
        assertTrue(capturedContains("coding"));
        assertTrue(capturedContains("planning"));
    }

    @Test
    void providersShowsEndpoint() {
        dispatcherWithProviders.dispatch("providers", printer);
        assertTrue(capturedContains("https://api.example.com/v1"));
    }

    @Test
    void providersNoSettingsShowsInfoMessage() {
        dispatcherNoProviders.dispatch("providers", printer);
        assertTrue(capturedContains("copilot"));
        assertTrue(capturedContains("No additional providers"));
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
