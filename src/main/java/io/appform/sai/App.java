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
package io.appform.sai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.knuddels.jtokkit.api.EncodingType;
import com.phonepe.sentinelai.core.agent.AgentSetup;
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
import io.appform.sai.models.Actor;
import io.appform.sai.models.Severity;
import io.appform.sai.tools.CoreToolBox;

import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

@Slf4j
public class App {
    @SneakyThrows
    public static void main(String[] args) {
        final var providedId = args.length > 0 ? args[0] : null;
        boolean sessionIdProvided = !Strings.isNullOrEmpty(providedId);
        final var sessionId = Objects.requireNonNullElseGet(providedId,
                                                            () -> UUID.randomUUID().toString());

        setupLogging();


        final var mapper = JsonUtils.createMapper();
        final var executorSerivce = Executors.newCachedThreadPool();

        final var okHttpClient = new OkHttpClient.Builder().readTimeout(Duration
                .ofSeconds(300))
                .callTimeout(Duration.ofSeconds(300))
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        final var provider = EnvLoader.readEnv(
                                               "MODEL_PROVIDER")
                .orElseThrow(() -> new IllegalArgumentException("MODEL_PROVIDER environment variable is required to specify the model provider to use. Supported values are 'azure', 'openai' and 'copilot-proxy'"));
        final var modelProviderFactory = new ConfigurableDefaultChatCompletionFactory(provider, mapper, okHttpClient);

        final var settings = Settings.builder()
                .sessionId(sessionId)
                .debug(false)
                .headless(false)
                .build();
        final var eventBus = new EventBus(executorSerivce);

        final var modelName = EnvLoader.readEnv("MODEL", "gemini-3-pro-preview");
        final var agentSetup = AgentSetup.builder()
                .executorService(executorSerivce)
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


        final var dataDir = Paths.get(settings.getDataDir(), "sessions");
        Files.createDirectories(dataDir);
        final var sessionStore = new FileSystemSessionStore(dataDir.toAbsolutePath().normalize().toString(), mapper, 1);
        final var sessionExtension = AgentSessionExtension.<String, String, SaiAgent>builder()
                .sessionStore(sessionStore)
                .mapper(mapper)
                .setup(AgentSessionExtensionSetup.builder()
                        .autoSummarizationThresholdPercentage(50)
                        .build())
                .build()
                .addMessageSelector(new RemoveAllToolCallsSelector());
        final var agent = new SaiAgent(agentSetup, List.of(sessionExtension), Map.of());

        try (final var printer = Printer.builder()
                .settings(settings)
                .executorService(executorSerivce)
                .build()
                .start();
             final var commandProcessor = CommandProcessor.builder()
                     .sessionId(settings.getSessionId())
                     .agent(agent)
                     .executorService(executorSerivce)
                     .printer(printer)
                     .build()
                     .start()) {
            agent.registerToolbox(new CoreToolBox(printer));
            printer.print(Update.builder()
                    .actor(Actor.SYSTEM)
                    .severity(Severity.INFO)
                    .colour(Printer.Colours.YELLOW)
                    .data("Welcome to SAI! Session ID: [%s] Type 'exit' to quit...."
                            .formatted(sessionId))
                    .build());
            final var eventPrinter = new EventPrinter(printer,
                                                      (ObjectMapper) mapper);
            eventBus.onEvent().connect(event -> {
                final var eventSessionId = event.getSessionId();
                // There might be events for other LLM capps like for example compaction, memory extraction etc, so we filter based on session id to avoid printing irrelevant events
                if (!Strings.isNullOrEmpty(eventSessionId) && sessionId.equals(
                                                                               eventSessionId)) {
                    event.accept(eventPrinter);
                }
            });

            if (sessionIdProvided) {
                final var response = sessionStore.readMessages(sessionId,
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
        catch (Exception e) {
            log.error("Error processing input", e);
        }
        finally {
            executorSerivce.shutdownNow();
            executorSerivce.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    private static void setupLogging() {
        try {
            final var context = (LoggerContext) LoggerFactory
                    .getILoggerFactory();
            final var configurator = new JoranConfigurator();
            configurator.setContext(context);
            context.reset();
            configurator.doConfigure(App.class.getResourceAsStream("/logback.xml"));
        }
        catch (JoranException je) {
            je.printStackTrace();
        }
    }
}
