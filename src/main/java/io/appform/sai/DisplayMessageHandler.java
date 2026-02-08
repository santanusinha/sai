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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.events.AgentEvent;
import com.phonepe.sentinelai.core.events.AgentEventVisitor;
import com.phonepe.sentinelai.core.events.InputReceivedAgentEvent;
import com.phonepe.sentinelai.core.events.MessageReceivedAgentEvent;
import com.phonepe.sentinelai.core.events.MessageSentAgentEvent;
import com.phonepe.sentinelai.core.events.OutputErrorAgentEvent;
import com.phonepe.sentinelai.core.events.OutputGeneratedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCallApprovalDeniedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCallCompletedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCalledAgentEvent;

import io.appform.sai.models.Actor;
import io.appform.sai.models.DisaplyMessage;
import io.appform.sai.models.DisaplyMessage.MessageType;
import io.appform.sai.models.Severity;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DisplayMessageHandler implements AutoCloseable {

    @AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    private static final class EventToMessageConverter implements AgentEventVisitor<Optional<DisaplyMessage>> {

        private final DisplayMessageHandler handler;

        @Override
        public Optional<DisaplyMessage> visit(MessageReceivedAgentEvent messageReceived) {
            return Optional.empty();
        }

        @Override
        public Optional<DisaplyMessage> visit(MessageSentAgentEvent messageSent) {
            return Optional.empty();
        }

        @Override
        @SneakyThrows
        public Optional<DisaplyMessage> visit(OutputGeneratedAgentEvent outputGeneratedAgentEvent) {
            return Optional.empty();
        }

        @Override
        public Optional<DisaplyMessage> visit(ToolCallApprovalDeniedAgentEvent toolCallApprovalDenied) {
            return Optional.of(DisaplyMessage.builder()
                                            .type(MessageType.TOOL_OUTPUT)
                                            .sessionId(toolCallApprovalDenied.getSessionId())
                                            .ruinId(toolCallApprovalDenied.getRunId())
                                            .actor(Actor.SYSTEM)
                                            .severity(Severity.ERROR)
                                            .content(String.format("Tool call to %s was denied approval", toolCallApprovalDenied.getToolCallName()))
                                            .build());
        }

        @Override
        public Optional<DisaplyMessage> visit(ToolCallCompletedAgentEvent toolCallCompleted) {
            return Optional.of(DisaplyMessage.toolOutput(
                                                           toolCallCompleted.getSessionId(),
                                                           toolCallCompleted.getRunId(),
                                                           toolCallCompleted.getErrorMessage()));
        }

        @Override
        public Optional<DisaplyMessage> visit(ToolCalledAgentEvent toolCalled) {
            final var content = "Tool %s (id: %s) called with arguments: %s"
                    .formatted(toolCalled.getToolCallName(),
                               toolCalled.getToolCallId(),
                               toolCalled.getArguments());
            return Optional.of(DisaplyMessage.toolCall(toolCalled
                    .getSessionId(), toolCalled.getRunId(), content));
        }

        @Override
        @SneakyThrows
        public Optional<DisaplyMessage> visit(InputReceivedAgentEvent inputReceived) {
            return Optional.of(DisaplyMessage.userMessage(
                                                           inputReceived.getSessionId(),
                                                           inputReceived.getRunId(),
                                                           handler.mapper.readTree(inputReceived.getContent()).asText()));
        }

        @Override
        public Optional<DisaplyMessage> visit(OutputErrorAgentEvent outputErrorAgentEvent) {
            return Optional.of(DisaplyMessage.modelError(
                                                           outputErrorAgentEvent.getSessionId(),
                                                           outputErrorAgentEvent.getRunId(),
                                                           outputErrorAgentEvent.getContent()));
        }
    }

    private final Settings settings;
    private final Printer printer;
    private final ObjectMapper mapper;
    private final ExecutorService executorService;
    private final LinkedBlockingDeque<List<DisaplyMessage>> messages = new LinkedBlockingDeque<>();
    private final EventToMessageConverter eventToMessageConverter = new EventToMessageConverter(this);
    private Future<?> queueReaderFuture = null;    

    public DisplayMessageHandler(Settings settings,
                                 ObjectMapper mapper,
                                 Printer printer,
                                 ExecutorService executorService) {
        this.settings = settings;
        this.printer = printer;
        this.mapper = mapper;
        this.executorService = executorService;

    }

    public DisplayMessageHandler start() {
        queueReaderFuture = executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    final var messagesToPrint = messages.take();
                    messagesToPrint.forEach(this::printMessage);
                }
                catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("Message printer shut down");
                }
            }
        });
        return this;
    }

    @Override
    public void close() throws Exception {
        if(null == queueReaderFuture) {
            queueReaderFuture.cancel(true);
        }
    }

    public void handleEvent(final AgentEvent event) {
        try {
            log.info("Received event: {} of type: {}", event.getEventId(), event);
            event.accept(eventToMessageConverter)
                    .ifPresent(this::handle); 
        }
        catch(Exception e) {
            log.error("Error handling event: {}", event.getEventId(), e);
        }
    }

    public void handle(final DisaplyMessage message) {
        handle(List.of(message));
    }

    public void handle(final List<DisaplyMessage> messages) {
        try {
            this.messages.put(messages);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Message printer shut down");
        }
    }

    public void printMessage(DisaplyMessage message) {
/*         final var colour = switch (message.getSeverity()) {
            case INFO -> Printer.Colours.BLUE;
            case WARNING -> Printer.Colours.YELLOW;
            case ERROR -> Printer.Colours.RED;
            case SUCCESS -> Printer.Colours.GREEN;
            case DEBUG -> Printer.Colours.GRAY;
        }; */
        final var colour = switch(message.getType()) {
            case EVENT -> Printer.Colours.GRAY;
            case MODEL_RESPONSE -> Printer.Colours.WHITE;
            case MODEL_ERROR -> Printer.Colours.RED;
            case TOOL_CALL -> Printer.Colours.YELLOW;
            case TOOL_OUTPUT -> Printer.Colours.GRAY;
            case USER_MESSAGE -> Printer.Colours.BOLD_WHITE_ON_BLACK_BACKGROUND;
            case INFO -> Printer.Colours.RESET;
        };
        if (shouldDisplay(message)) {
            if (settings.isHeadless()) {
                printer.println(message.getContent());
            }
            else {
                printer.println(colour, formatMessage(message));
            }
        }
    }

    private static String formatMessage(final DisaplyMessage message) {
        return String.format("%s: %s", message.getActor().getEmoji(), message.getContent());
    }

    /**
     * Decide whether to display the message based on the settings and message severity/type.
     * In debug mode, we want to display all messages. In headless mode, we only want to display ERROR and
     * MODEL_RESPONSE messages. In normal mode, we want to display all messages except DEBUG messages.
     */
    private boolean shouldDisplay(DisaplyMessage message) {
        if(settings.isDebug()) {
            return true;
        }

        final var headlessMode = settings.isHeadless();

        return !headlessMode
            || message.getSeverity().equals(Severity.ERROR)
            || message.getType().equals(MessageType.MODEL_RESPONSE);
    }

}
