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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.StreamConsumer;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.models.ConfiguredModel;
import com.phonepe.sentinelai.models.wire.WireLoggingMode;

import io.appform.sai.config.ProviderEntry;
import io.appform.sai.config.SettingsConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

/**
 * Verifies that the wire logging mode flows from the {@link ModelFactory} into the built
 * {@link ConfiguredModel}: {@link WireLoggingMode#FRAMES} logs every raw SSE frame, the
 * default mode logs only the payloads, and {@link WireLoggingMode#OFF} logs nothing.
 */
@Slf4j
class WireLoggingModeWiringTest {

    private static final String WIRE_LOGGER_NAME = "com.phonepe.sentinelai.models.wire.WIRE";

    private static final class WireAgent extends Agent<String, String, WireAgent> {

        private WireAgent(final AgentSetup setup) {
            super(String.class, "answer the user", setup, List.of(), Map.of());
        }

        @Override
        public String name() {
            return "wire-agent";
        }
    }

    private final MockWebServer server = new MockWebServer();

    private Logger wireLogger;
    private ListAppender<ILoggingEvent> appender;
    private Level originalLevel;

    @Test
    void defaultModeLogsPayloadsButNoFrames() {
        final var output = runModel(model(null));
        assertEquals("hi", output);

        assertTrue(frames().isEmpty(), "no raw frames expected at the default mode");
        assertEquals(1, lines("Request to Model").size());
        assertEquals(1, lines("Response from Model").size());
    }

    @Test
    void framesModeLogsEveryStreamFrame() {
        final var output = runModel(model(WireLoggingMode.FRAMES));
        assertEquals("hi", output);

        assertEquals(4, frames().size(), "every raw SSE frame expected, got: " + messages());
        assertEquals(1, lines("Request to Model").size());
        assertEquals(1, lines("Response from Model").size());
    }

    @Test
    void nullWireLoggingFallsBackToDefaultMode() {
        final var model = model(null);
        assertEquals("test-model", model.getModelName());
    }

    @Test
    void offModeLogsNothing() {
        final var output = runModel(model(WireLoggingMode.OFF));
        assertEquals("hi", output);

        assertTrue(appender.list.isEmpty(), "no wire logs expected at OFF, got: " + messages());
    }

    @BeforeEach
    void start() throws Exception {
        server.start();
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(readFixture()));
        wireLogger = (Logger) LoggerFactory.getLogger(WIRE_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        wireLogger.addAppender(appender);
        wireLogger.setAdditive(false);
        originalLevel = wireLogger.getLevel();
        wireLogger.setLevel(Level.INFO);
    }

    @AfterEach
    void stop() throws Exception {
        wireLogger.detachAppender(appender);
        wireLogger.setLevel(originalLevel);
        wireLogger.setAdditive(true);
        server.shutdown();
    }

    private List<String> frames() {
        return lines("Stream Frame");
    }

    private List<String> lines(String prefix) {
        return messages().stream().filter(m -> m.startsWith(prefix)).toList();
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private ConfiguredModel model(WireLoggingMode wireLogging) {
        final var config = SettingsConfig.builder()
                .providers(Map.of("openai",
                                  ProviderEntry.builder()
                                          .type("openai")
                                          .endpoint(server.url("").toString())
                                          .apiKey("test-key")
                                          .build()))
                .build();
        final var factory = new ConfigurableProviderFactory("openai", new OkHttpClient(), config);
        return (ConfiguredModel) factory.model("test-model", "test-model", null, wireLogging);
    }

    private String readFixture() {
        try {
            return Files.readString(Path.of("src/test/resources/wire-logging-sse.txt"));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String runModel(Model model) {
        final var agent = new WireAgent(AgentSetup.builder()
                .mapper(new ObjectMapper())
                .model(model)
                .build());
        return agent.executeAsyncTextStreaming(AgentInput.<String>builder()
                .request("Hi")
                .requestMetadata(AgentRequestMetadata.builder()
                        .sessionId("s1")
                        .userId("test-user")
                        .build())
                .build(), StreamConsumer.NO_OP)
                .join()
                .getData();
    }
}
