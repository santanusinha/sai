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
import com.knuddels.jtokkit.api.EncodingType;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.model.ModelAttributes;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.utils.EnvLoader;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.filesystem.session.FileSystemSessionStore;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;
import com.phonepe.sentinelai.models.SimpleOpenAIModelOptions;
import com.phonepe.sentinelai.models.TokenCountingConfig;
import com.phonepe.sentinelai.session.AgentSessionExtension;
import com.phonepe.sentinelai.session.AgentSessionExtensionSetup;
import com.phonepe.sentinelai.session.QueryDirection;
import com.phonepe.sentinelai.session.history.selectors.RemoveAllToolCallsSelector;

import io.appform.sai.CommandProcessor.CommandType;
import io.appform.sai.CommandProcessor.InputCommand;
import io.appform.sai.Printer.Update;
import io.appform.sai.commands.ListCommand;
import io.appform.sai.models.Actor;
import io.appform.sai.models.Severity;
import io.appform.sai.tools.CoreToolBox;

import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Slf4j
@Getter
@Command(name = "sai", mixinStandardHelpOptions = true, version = "1.0", description = "Sai AI Agent", subcommands = {
        ListCommand.class
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
            "-p", "--prompt"
    }, description = "Execute a single prompt and exit")
    private String prompt;

    @Override
    public Integer call() throws Exception {
        final var sessionIdProvided = !Strings.isNullOrEmpty(sessionId);
        final var effectiveSessionId = Objects.requireNonNullElseGet(sessionId,
                                                                     () -> UUID.randomUUID().toString());

        final var mapper = JsonUtils.createMapper();
        final var executorService = Executors.newCachedThreadPool();

        final var okHttpClient = new OkHttpClient.Builder().readTimeout(Duration
                .ofSeconds(300))
                .callTimeout(Duration.ofSeconds(300))
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        final var provider = EnvLoader.readEnv(
                                               "MODEL_PROVIDER")
                .orElseThrow(() -> new IllegalArgumentException("MODEL_PROVIDER environment variable is required to specify the model provider to use. Supported values are 'azure', 'openai' and 'copilot-proxy'"));
        final var modelProviderFactory = new ConfigurableDefaultChatCompletionFactory(provider, mapper, okHttpClient);

        var settingsBuilder = Settings.builder()
                .sessionId(effectiveSessionId)
                .debug(debug)
                .headless(headless);

        if (!Strings.isNullOrEmpty(prompt)) {
            settingsBuilder.headless(true);
        }
        if (!Strings.isNullOrEmpty(dataDir)) {
            settingsBuilder.dataDir(dataDir);
        }

        final var settings = settingsBuilder.build();
        final var eventBus = new EventBus(executorService);

        final var modelName = EnvLoader.readEnv("MODEL", "gemini-3-pro-preview");
        final var agentSetup = AgentSetup.builder()
                .executorService(executorService)
                .mapper(mapper)
                .eventBus(eventBus)
                .modelSettings(ModelSettings.builder()
                        .parallelToolCalls(false)
                        .modelAttributes(ModelAttributes.builder()
                                .contextWindowSize(128_000)
                                .encodingType(EncodingType.O200K_BASE)
                                .build())
                        .build())
                .model(new SimpleOpenAIModel<>(modelName,
                                               modelProviderFactory,
                                               mapper,
                                               SimpleOpenAIModelOptions
                                                       .builder()
                                                       .tokenCountingConfig(TokenCountingConfig.DEFAULT)
                                                       .build()))
                // .outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT)
                .build();


        final var dataDirPath = Paths.get(settings.getDataDir(), "sessions");
        Files.createDirectories(dataDirPath);
        final var sessionStore = new FileSystemSessionStore(dataDirPath.toAbsolutePath().normalize().toString(),
                                                            mapper,
                                                            1);
        final var sessionExtension = AgentSessionExtension.<String, String, SaiAgent>builder()
                .sessionStore(sessionStore)
                .mapper(mapper)
                .setup(AgentSessionExtensionSetup.builder()
                        .autoSummarizationThresholdPercentage(50)
                        .build())
                .build()
                .addMessageSelector(new RemoveAllToolCallsSelector());
        final var agent = new SaiAgent(agentSetup,
                                       Strings.isNullOrEmpty(prompt)
                                               ? List.of(sessionExtension)
                                               : List.of(),
                                       Map.of());
        final var printer = Printer.builder()
                .settings(settings)
                .executorService(executorService)
                .build()
                .start();

        if (!Strings.isNullOrEmpty(prompt)) {
            agent.registerToolbox(new CoreToolBox(printer));
            final var user = Objects.requireNonNullElse(System.getProperty("USER"), "User");
            try {
                final var responseF = agent.executeAsync(AgentInput
                        .<String>builder()
                        .requestMetadata(AgentRequestMetadata.builder()
                                .sessionId(settings.getSessionId())
                                .runId("run-" + UUID.randomUUID())
                                .userId(user)
                                .build())
                        .request(prompt)
                        .build());
                final var response = responseF.get();
                if (response.getError().getErrorType().equals(ErrorType.SUCCESS)) {
                    System.out.println(response.getData());
                }
                else {
                    System.err.println("Error: " + response.getError().getMessage());
                    return 1;
                }
            }
            catch (Exception e) {
                log.error("Error executing prompt", e);
                return 1;
            }
            finally {
                printer.close();
                executorService.shutdownNow();
                executorService.awaitTermination(1, TimeUnit.SECONDS);
            }
            return 0;
        }

        try (printer) {
            agent.registerToolbox(new CoreToolBox(printer));

            try (final var commandProcessor = CommandProcessor.builder()
                    .sessionId(settings.getSessionId())
                    .agent(agent)
                    .executorService(executorService)
                    .printer(printer)
                    .build()
                    .start()) {
                printer.print(Update.builder()
                        .actor(Actor.SYSTEM)
                        .severity(Severity.INFO)
                        .colour(Printer.Colours.YELLOW)
                        .data("Welcome to SAI! Session ID: [%s] Type 'exit' to quit...."
                                .formatted(effectiveSessionId))
                        .build());
                final var eventPrinter = new EventPrinter(printer,
                                                          (ObjectMapper) mapper);
                eventBus.onEvent().connect(event -> {
                    final var eventSessionId = event.getSessionId();
                    // There might be events for other LLM capps like for example compaction, memory extraction etc, so we filter based on session id to avoid printing irrelevant events
                    if (!Strings.isNullOrEmpty(eventSessionId) && effectiveSessionId.equals(
                                                                                            eventSessionId)) {
                        event.accept(eventPrinter);
                    }
                });

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

                var prompt = Printer.Colours.CYAN + "Enter input ";
                prompt += Printer.Colours.GRAY + "(type 'exit' to quit)";
                prompt += Printer.Colours.WHITE + ": ";
                while (true) {
                    try {
                        final var input = printer.getLineReader().readLine(prompt);
                        if (Strings.isNullOrEmpty(input)) {
                            continue;
                        }
                        if (Objects.equals(input, "exit")) {
                            break;
                        }
                        //TODO::FIND OUT WHICH COMMAND AND HANDLE
                        commandProcessor.handle(CommandProcessor.Command.builder()
                                .command(CommandType.INPUT)
                                .input(new InputCommand("run-" + UUID.randomUUID()
                                        .toString(), input))
                                .build());
                    }
                    catch (EndOfFileException | UserInterruptException e) {
                        break;
                    }
                }
            }
        }
        catch (Exception e) {
            log.error("Error processing input", e);
            return 1;
        }
        finally {
            executorService.shutdownNow();
            executorService.awaitTermination(1, TimeUnit.SECONDS);
        }
        return 0;
    }
}
