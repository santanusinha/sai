/*
 * Copyright 2026 authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.appform.sai;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.knuddels.jtokkit.api.EncodingType;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.model.ModelAttributes;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.models.ChatCompletionServiceFactory;
import com.phonepe.sentinelai.models.DefaultChatCompletionServiceFactory;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;
import com.phonepe.sentinelai.models.SimpleOpenAIModelOptions;
import com.phonepe.sentinelai.models.TokenCountingConfig;

import io.appform.sai.CommandProcessor.CommandType;
import io.appform.sai.CommandProcessor.InputCommand;
import io.appform.sai.Printer.Update;
import io.appform.sai.models.Actor;
import io.appform.sai.models.Severity;
import io.github.cdimascio.dotenv.Dotenv;
import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.cleverclient.retry.RetryConfig;
import io.github.sashirestela.openai.SimpleOpenAIAzure;
import okhttp3.OkHttpClient;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;

import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;
import org.slf4j.LoggerFactory;

@Slf4j
public class App {
    @SneakyThrows
    public static void main(String[] args) {
        final var dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .ignoreIfMalformed()
                .systemProperties()
                .load();

        setupLogging();


        final var mapper = JsonUtils.createMapper();
        final var executorSerivce = Executors.newCachedThreadPool();

        final var okHttpClient = new OkHttpClient.Builder().readTimeout(Duration
                .ofSeconds(300))
                .callTimeout(Duration.ofSeconds(300))
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        final var modelProviderFactory = modelFactory(dotenv,
                                                      mapper,
                                                      okHttpClient);

        final var settings = Settings.builder()
                .debug(false)
                .headless(false)
                .build();
        final var eventBus = new EventBus(executorSerivce);

        final var agentSetup = AgentSetup.builder()
                .executorService(executorSerivce)
                .mapper(mapper)
                .eventBus(eventBus)
                .outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT)
                .modelSettings(ModelSettings.builder()
                        .parallelToolCalls(true)
                        .modelAttributes(ModelAttributes.builder()
                                .contextWindowSize(128_000)
                                .encodingType(EncodingType.O200K_BASE)
                                .build())
                        .build())
                .model(new SimpleOpenAIModel<>("gpt-5",
                                               modelProviderFactory,
                                               mapper,
                                               SimpleOpenAIModelOptions
                                                       .builder()
                                                       .tokenCountingConfig(TokenCountingConfig.DEFAULT)
                                                       .build()))
                .build();


        final var agent = new SaiAgent(agentSetup, List.of(), Map.of());


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
            printer.print(Update.builder()
                    .actor(Actor.SYSTEM)
                    .severity(Severity.INFO)
                    .colour(Printer.Colours.YELLOW)
                    .data("Welcome to SAI! Type 'exit' to quit....")
                    .build());
            final var eventPrinter = new EventPrinter(mapper, printer);
            eventBus.onEvent().connect(event -> event.accept(eventPrinter));

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

    private static ChatCompletionServiceFactory modelFactory(final Dotenv dotenv,
                                                             final ObjectMapper mapper,
                                                             final OkHttpClient okHttpClient) {
        final var gpt5 = SimpleOpenAIAzure.builder()
                .baseUrl(dotenv.get("AZURE_GPT5_ENDPOINT"))
                .apiKey(dotenv.get("AZURE_API_KEY"))
                .apiVersion("2024-10-21")
                .objectMapper(mapper)
                .clientAdapter(new OkHttpClientAdapter(okHttpClient))
                .retryConfig(RetryConfig.builder()
                        .maxAttempts(1) // disabling implicit retries by default for tests
                        .build())
                .build();

        final var gpt5Mini = SimpleOpenAIAzure.builder()
                .baseUrl(dotenv.get("AZURE_GPT5_MINI_ENDPOINT"))
                .apiKey(dotenv.get("AZURE_API_KEY"))
                .apiVersion("2024-10-21")
                .objectMapper(mapper)
                .clientAdapter(new OkHttpClientAdapter(okHttpClient))
                .retryConfig(RetryConfig.builder()
                        .maxAttempts(1) // disabling implicit retries by default for tests
                        .build())
                .build();
        return new DefaultChatCompletionServiceFactory()
                .registerDefaultProvider(gpt5)
                .registerProvider("gpt-5-mini", gpt5Mini);
    }

    private static void setupLogging() {
        try {
            final var context = (LoggerContext) LoggerFactory
                    .getILoggerFactory();
            final var configurator = new JoranConfigurator();
            configurator.setContext(context);
            context.reset();
            configurator.doConfigure(App.class.getResourceAsStream(
                                                                   "/logback.xml"));
        }
        catch (JoranException je) {
            je.printStackTrace();
        }
    }
}
