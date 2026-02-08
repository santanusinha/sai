
package io.appform.sai;

import java.awt.Color;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import com.knuddels.jtokkit.api.EncodingType;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.model.ModelAttributes;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.models.ChatCompletionServiceFactory;
import com.phonepe.sentinelai.models.DefaultChatCompletionServiceFactory;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;
import com.phonepe.sentinelai.models.SimpleOpenAIModelOptions;
import com.phonepe.sentinelai.models.TokenCountingConfig;

import io.appform.sai.models.Severity;
import io.github.cdimascio.dotenv.Dotenv;
import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.cleverclient.retry.RetryConfig;
import io.github.sashirestela.openai.SimpleOpenAIAzure;
import okhttp3.OkHttpClient;
import lombok.extern.slf4j.Slf4j;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import org.slf4j.LoggerFactory;

@Slf4j
public class App {
    public static void main(String[] args) {
        final var dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .ignoreIfMalformed()
                .systemProperties()
                .load();

        setupLogging();

        final var printer = Printer.builder().headless(false).build();
        final var mapper = JsonUtils.createMapper();
        final var execitorSerivce = Executors.newCachedThreadPool();

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
        final var eventBus = new EventBus(execitorSerivce);

        final var agentSetup = AgentSetup.builder()
                .executorService(execitorSerivce)
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

        eventBus.onEvent().connect(new DisplayMessageHandler(settings, printer, mapper)::handleEvent);

        final var agent = new SaiAgent(agentSetup, List.of(), Map.of());

        execitorSerivce.submit(() -> {
            final var elapsedTimeCoounter = Stopwatch.createStarted();
            try {
                final var responseF = agent.executeAsync(AgentInput
                        .<String>builder()
                        .requestMetadata(AgentRequestMetadata.builder()
                                .sessionId("session-1")
                                .userId(Objects.requireNonNullElse(System.getProperty("USER"), "user-1"))
                                .build())
                        .request("Provide some guidance and sample code to format markdown properly on a terminal in java")
                        .build());
                final var response = responseF.get();
                final var icon = response.getError().getErrorType().equals(ErrorType.SUCCESS) ? Severity.SUCCESS.getEmoji() : Severity.ERROR.getEmoji();
                printer.print(Printer.Colours.WHITE,
                        "%s %s.".formatted(icon, response.getError().getMessage()));
                printer.println(Printer.Colours.GRAY,
                        " (Time taken: %.3f seconds, Tokens used: %d)".formatted(
                            (float)elapsedTimeCoounter.elapsed().toMillis() / 1000.0,
                            response.getUsage().getTotalTokens()));
            }
            catch (Exception e) {
                final var message = AgentUtils.rootCause(e).getMessage();
                printer.print(Printer.Colours.RED, "Error sending request:" + message);
                printer.println(Printer.Colours.GRAY,
                        " (Time taken: %.3f seconds)".formatted(
                            (float)elapsedTimeCoounter.elapsed().toMillis() / 1000.0));
                 log.error("Error executing agent %s".formatted(message), e);
            }
        });
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
