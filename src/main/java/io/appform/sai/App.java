
package io.appform.sai;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knuddels.jtokkit.api.EncodingType;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.model.ModelAttributes;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.models.ChatCompletionServiceFactory;
import com.phonepe.sentinelai.models.DefaultChatCompletionServiceFactory;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;
import com.phonepe.sentinelai.models.SimpleOpenAIModelOptions;
import com.phonepe.sentinelai.models.TokenCountingConfig;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.cleverclient.retry.RetryConfig;
import io.github.sashirestela.openai.SimpleOpenAIAzure;
import okhttp3.OkHttpClient;

public class App {
    public static void main(String[] args) {
        final var printer = Printer.builder().headless(false).build();
        final var mapper = JsonUtils.createMapper();
        final var execitorSerivce = Executors.newCachedThreadPool();
        final var dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .ignoreIfMalformed()
                .load();
        final var okHttpClient = new OkHttpClient.Builder()
                .readTimeout(Duration.ofSeconds(300))
                .callTimeout(Duration.ofSeconds(300))
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        final var modelProviderFactory = modelFactory(dotenv, mapper, okHttpClient);

        final var agentSetup = AgentSetup.builder()
                .executorService(execitorSerivce)
                .mapper(mapper)
                .outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT)
                .modelSettings(ModelSettings.builder()
                        .parallelToolCalls(true)
                        .modelAttributes(ModelAttributes.builder()
                                .contextWindowSize(128_000)
                                .encodingType(EncodingType.O200K_BASE)
                                .build())
                        .build())
                .model(new SimpleOpenAIModel<>(
                        "gpt-5",
                        modelProviderFactory,
                        mapper,
                        SimpleOpenAIModelOptions.builder()
                            .tokenCountingConfig(TokenCountingConfig.DEFAULT)
                            .build()))
                 .build();

        final var agent = new SaiAgent(agentSetup, List.of(), Map.of());

        execitorSerivce.submit(() -> {
            try {
                final var response = agent.executeAsync(AgentInput
                        .<String>builder()
                        .request("What is the capital of France?")
                        .build()).get();
                printer.print("Response: " + response);
            }
            catch (Exception e) {
                e.printStackTrace();
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


}
