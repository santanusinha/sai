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
package io.appform.sai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.utils.EnvLoader;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.filesystem.session.FileSystemSessionStore;
import com.phonepe.sentinelai.session.AgentSessionExtension;
import com.phonepe.sentinelai.session.AgentSessionExtensionSetup;
import com.phonepe.sentinelai.session.QueryDirection;
import com.phonepe.sentinelai.session.history.selectors.RemoveAllToolCallsSelector;

import io.appform.sai.CommandProcessor.CommandType;
import io.appform.sai.CommandProcessor.InputCommand;
import io.appform.sai.Printer.Colours;
import io.appform.sai.Printer.Update;
import io.appform.sai.agent.AgentFactory;
import io.appform.sai.commands.DeleteCommand;
import io.appform.sai.commands.ListCommand;
import io.appform.sai.config.AgentConfigLoader;
import io.appform.sai.models.Actor;
import io.appform.sai.models.Severity;
import io.appform.sai.tools.CoreToolBox;

import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Slf4j
@Getter
@Command(name = "sai", mixinStandardHelpOptions = true, version = "1.0", description = "Sai AI Agent", subcommands = {
        ListCommand.class,
        DeleteCommand.class
})
public class SaiCommand implements Callable<Integer> {

    @Option(names = {
            "-s", "--session-id"
    }, description = "Resume a specific session")
    private String sessionId;

    @Option(names = {
            "-d", "--debug"
    }, description = "Enable debug mode")
    private boolean debug;

    @Option(names = {
            "--headless"
    }, description = "Run in headless mode")
    private boolean headless;

    @Option(names = {
            "--data-dir"
    }, description = "Override data directory")
    private String dataDir;

    @Option(names = {
            "--config-dir"
    }, description = "Override config directory")
    private String configDir;

    @Option(names = {
            "-i", "--input"
    }, description = "Execute a single input and exit. If the value starts with '@', read input from the specified file.")
    private String input;

    @Option(names = {
            "-p", "--persona"
    }, description = "Path to AgentConfig persona file (.yaml/.yml/.json)")
    private String persona;

    @Override
    @SuppressWarnings("java:S106")
    public Integer call() throws Exception {
        final var sessionIdProvided = !Strings.isNullOrEmpty(sessionId);
        final var effectiveSessionId = Objects.requireNonNullElseGet(sessionId,
                                                                     () -> UUID.randomUUID().toString());

        final var mapper = JsonUtils.createMapper();
        final var executorService = Executors.newCachedThreadPool();
        final var eventBus = new EventBus(executorService);

        final var okHttpClient = new OkHttpClient.Builder().readTimeout(Duration
                .ofSeconds(300))
                .callTimeout(Duration.ofSeconds(300))
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        final var provider = EnvLoader.readEnv("MODEL_PROVIDER")
                .orElseThrow(() -> new IllegalArgumentException("MODEL_PROVIDER environment variable is required to specify the model provider to use. Supported values are 'azure', 'openai' and 'copilot-proxy'"));
        final var modelProviderFactory = new ConfigurableDefaultChatCompletionFactory(provider, mapper, okHttpClient);

        final var settingsBuilder = Settings.builder()
                .sessionId(effectiveSessionId)
                .debug(debug)
                .headless(headless || (!Strings.isNullOrEmpty(input)))
                .noSession(!sessionIdProvided);
        if (sessionIdProvided) {
            if (!Strings.isNullOrEmpty(dataDir)) {
                settingsBuilder.dataDir(dataDir);
            }
            if (!Strings.isNullOrEmpty(configDir)) {
                settingsBuilder.configDir(configDir);
            }
        }
        else {
            // When no session, we still create session to allow for the session extension
            // to perform compaction etc. We create a temp directory for this
            final var tempDataDir = Files.createTempDirectory("sai-data-")
                    .toAbsolutePath()
                    .normalize()
                    .toString();
            settingsBuilder.dataDir(tempDataDir);
        }
        // Apply configDir override regardless of session
        if (!Strings.isNullOrEmpty(configDir)) {
            settingsBuilder.configDir(configDir);
        }
        final var settings = settingsBuilder.build();

        final var sessionDataPath = Paths.get(settings.getDataDir(), "sessions");
        Files.createDirectories(sessionDataPath);

        final var sessionStore = new FileSystemSessionStore(sessionDataPath.toString(), mapper, 1);
        final var sessionExtension = AgentSessionExtension.<String, String, SaiAgent>builder()
                .sessionStore(sessionStore)
                .mapper(mapper)
                .setup(AgentSessionExtensionSetup.builder()
                        .autoSummarizationThresholdPercentage(50)
                        .build())
                .build()
                .addMessageSelector(new RemoveAllToolCallsSelector());
        final var agentFactory = new AgentFactory(
                                                  sessionExtension,
                                                  executorService,
                                                  modelProviderFactory,
                                                  mapper,
                                                  eventBus,
                                                  okHttpClient);
        final AgentConfig agentConfig;
        try {
            agentConfig = resolveAgentConfig(persona, mapper);
        }
        catch (Exception e) {
            log.error("Error loading persona: {}", persona, e);
            System.err.println("Error: Failed to load persona file: " + persona + " (" + e.getMessage() + ")");
            return 1;
        }
        final var agent = agentFactory.createAgent(agentConfig);

        try (final var printer = Printer.builder()
                .settings(settings)
                .executorService(executorService)
                .build()
                .start()) {
            // Setup rest of the connentcions
            agent.registerToolbox(new CoreToolBox(printer));
            sessionExtension.onSessionSummarized()
                    .connect(sessionSummary -> printer.print(Printer.systemMessage(Colours.YELLOW
                            + "Session compacted with summary: " + Colours.WHITE
                            + sessionSummary.getTitle() + Colours.RESET)));
            final var eventPrinter = new EventPrinter(printer, mapper);
            eventBus.onEvent().connect(event -> {
                final var eventSessionId = event.getSessionId();
                // There might be events for other LLM events like for example compaction,
                // memory extraction etc, so we filter based on session id to avoid printing irrelevant events
                if (!Strings.isNullOrEmpty(eventSessionId) && effectiveSessionId.equals(eventSessionId)) {
                    event.accept(eventPrinter);
                }
            });

            try (final var commandProcessor = CommandProcessor.builder()
                    .sessionId(settings.getSessionId())
                    .agent(agent)
                    .printer(printer)
                    .build()
                    .start()) {
                if (!settings.isHeadless()) {
                    printer.print(Update.builder()
                            .actor(Actor.SYSTEM)
                            .severity(Severity.INFO)
                            .colour(Printer.Colours.YELLOW)
                            .data("Welcome to SAI! Session ID: [%s] Type 'exit' to quit...."
                                    .formatted(effectiveSessionId))
                            .build());
                }
                if (sessionIdProvided) {
                    final var response = sessionStore.readMessages(effectiveSessionId,
                                                                   Integer.MAX_VALUE,
                                                                   true,
                                                                   null,
                                                                   QueryDirection.OLDER);
                    final var messagePrinter = new MessagePrinter(printer, mapper, true);
                    response.getItems().forEach(message -> {
                        final var updates = message.accept(messagePrinter);
                        printer.print(updates);
                    });
                }

                var userInput = Strings.isNullOrEmpty(input) ? null : resolveInput(input);
                while (Strings.isNullOrEmpty(userInput) || !userInput.equalsIgnoreCase("exit")) {
                    if (Strings.isNullOrEmpty(userInput)) {
                        userInput = readInput(printer).orElse("exit");
                    }
                    else {
                        final var command = CommandProcessor.Command.builder()
                                .command(CommandType.INPUT)
                                .input(new InputCommand("run-" + UUID.randomUUID()
                                        .toString(), userInput))
                                .build();
                        try {
                            commandProcessor.handle(command);
                        }
                        finally {
                            userInput = !Strings.isNullOrEmpty(input) ? "exit" : null;
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            log.error("Error processing input", e);
            return 1;
        }
        finally {
            executorService.shutdown();
            executorService.awaitTermination(1, TimeUnit.SECONDS);
        }
        return 0;
    }

    private Optional<String> readInput(final Printer printer) {
        var prompt = Printer.Colours.YELLOW + "> " + Printer.Colours.RESET;
        try {
            return Optional.of(printer.getLineReader().readLine(prompt));
        }
        catch (EndOfFileException | UserInterruptException e) {
            return Optional.empty();
        }
    }

    private AgentConfig resolveAgentConfig(String persona, ObjectMapper mapper) {
        if (Strings.isNullOrEmpty(persona)) {
            return AgentConfig.builder()
                    .agentId("sai-agent")
                    .name("Sai Agent")
                    .description("An AI agent that can execute tasks and answer questions.")
                    .build();
        }
        return AgentConfigLoader.load(Paths.get(persona), mapper);
    }

    @SneakyThrows
    private String resolveInput(String input) {
        if (input.startsWith("@")) {
            final var filePath = input.substring(1);
            if (Strings.isNullOrEmpty(filePath)) {
                throw new IllegalArgumentException("--input '@' requires a file path");
            }
            return Files.readString(Paths.get(filePath), StandardCharsets.UTF_8);
        }
        return input;
    }

}
