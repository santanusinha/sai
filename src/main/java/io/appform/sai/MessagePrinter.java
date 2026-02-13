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
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageVisitor;
import com.phonepe.sentinelai.core.agentmessages.AgentRequest;
import com.phonepe.sentinelai.core.agentmessages.AgentRequestVisitor;
import com.phonepe.sentinelai.core.agentmessages.AgentResponse;
import com.phonepe.sentinelai.core.agentmessages.AgentResponseVisitor;
import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelUsageStats;

import io.appform.sai.Printer.Update;
import io.appform.sai.models.Actor;
import io.appform.sai.models.Severity;
import io.appform.sai.tools.ToolIO;

import java.util.ArrayList;
import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor(access = AccessLevel.PUBLIC)
@SuppressWarnings("unused")
public class MessagePrinter implements AgentMessageVisitor<List<Printer.Update>> {

    private static final String BASH_TOOL = "core_tool_box_bash";
    private static final XmlMapper xmlMapper = new XmlMapper();

    private final Printer printer;
    private final ObjectMapper mapper;
    private final boolean historical;

    static {
        xmlMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        xmlMapper.configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true);
        xmlMapper.configure(ToXmlGenerator.Feature.WRITE_XML_1_1, true);
    }

    @Override
    public List<Update> visit(AgentGenericMessage genericMessage) {
        return List.of();
    }

    @Override
    public List<Update> visit(AgentRequest request) {
        return request.accept(new AgentRequestVisitor<List<Update>>() {

            @Override
            public List<Update> visit(SystemPrompt systemPrompt) {
                return List.of(Printer.debug(Actor.SYSTEM, "System Prompt"),
                               Printer.raw(Printer.Colours.GRAY + prettyPrintXML(systemPrompt.getContent())
                                       + Printer.Colours.RESET).withDebug(true),
                               Printer.empty());
            }

            @Override
            @SneakyThrows
            public List<Update> visit(ToolCallResponse toolCallResponse) {
                final var messages = new ArrayList<Update>();
                switch (toolCallResponse.getToolName()) {
                    case BASH_TOOL -> {
                        final var response = mapper.readValue(toolCallResponse
                                .getResponse(), ToolIO.BashResponse.class);
                        final var statusCode = response.getStatusCode();
                        final var stdout = response.getStdout();
                        final var stderr = response.getStderr();
                        if (!Strings.isNullOrEmpty(stdout)) {
                            messages.add(Printer.systemMessage(stdout));
                        }
                        if (!Strings.isNullOrEmpty(stderr)) {
                            messages.add(Printer.systemMessage(
                                                               "Status: %s -> %s"
                                                                       .formatted(statusCode,
                                                                                  stderr))
                                    .withSeverity(Severity.ERROR));
                        }
                        if (messages.isEmpty()) {
                            messages.add(Printer.systemMessage(
                                                               "Status: %s -> Command executed successfully"
                                                                       .formatted(statusCode)));
                        }
                    }
                    case Agent.OUTPUT_GENERATOR_ID -> {
                        messages.add(Printer.debug(Actor.SYSTEM,
                                                   "Output Generator Tool call completed."
                                                           .formatted(toolCallResponse
                                                                   .getResponse())));
                    }
                    default -> {
                        if (toolCallResponse
                                .getErrorType() != null && toolCallResponse
                                        .getErrorType()
                                        .equals(ErrorType.SUCCESS)) {
                            messages.add(Printer.systemMessage(toolCallResponse
                                    .getResponse()));
                        }
                        else {
                            messages.add(Printer.systemMessage("Error: %s - %s"
                                    .formatted(toolCallResponse.getErrorType(),
                                               toolCallResponse.getResponse()))
                                    .withSeverity(Severity.ERROR));

                        }
                    }
                }
                messages.add(Printer.empty());
                return messages;
            }

            @Override
            public List<Update> visit(UserPrompt userPrompt) {
                final var messages = new ArrayList<Update>();
                messages.add(Printer.userMessage(" " + prompt(userPrompt.getContent()) + " "));
                messages.add(Printer.empty());
                if (!historical) {
                    messages.add(Printer.statusUpdate(" Processing run: " + Printer.Colours.GRAY
                            + "%s ... ".formatted(userPrompt.getRunId())));
                }
                return messages;
            }

        });
    }

    @Override
    public List<Update> visit(AgentResponse response) {
        return response.accept(new AgentResponseVisitor<List<Update>>() {

            @Override
            public List<Update> visit(StructuredOutput structuredOutput) {
                return handleResponse(structuredOutput.getContent(),
                                      structuredOutput.getStats(),
                                      structuredOutput.getElapsedTimeMs());
            }

            @Override
            public List<Update> visit(Text text) {
                return handleResponse(text.getContent(), text.getStats(), text.getElapsedTimeMs());
            }

            @Override
            @SneakyThrows
            public List<Update> visit(ToolCall toolCall) {
                final var messages = new ArrayList<Update>();
                switch (toolCall.getToolName()) {
                    case BASH_TOOL -> {
                        final var node = mapper.readTree(toolCall.getArguments());
                        final var fieldName = node.fieldNames().next();
                        // there is only one paramter in this node which is the request.
                        final var request = mapper.treeToValue(node.get(fieldName), ToolIO.BashRequest.class);
                        log.info("Received bash tool call with arguments: {}. Request: {}",
                                 toolCall.getArguments(),
                                 request);
                        messages.add(Printer.assistantMessage(request.getRequestReason()));
                        messages.add(Printer.assistantMessage(Printer.Colours.YELLOW + "$ " + Printer.Colours.WHITE
                                + request
                                        .getCommand() + Printer.Colours.GRAY + " (Timeout: " + request
                                                .getTimeoutSeconds() + " seconds)" + Printer.Colours.RESET));
                    }
                    case Agent.OUTPUT_GENERATOR_ID -> {
                        messages.add(Printer.debug(Actor.ASSISTANT, "Output Generator Tool called..."));
                    }
                    default -> {
                        messages.add(Printer.systemMessage((Printer.Colours.YELLOW + "%s" + Printer.Colours.RESET + "("
                                + Printer.Colours.CYAN + "%s" + Printer.Colours.RESET + ")")
                                .formatted(toolCall.getToolName(), toolCall.getArguments())));

                    }
                }
                messages.add(Printer.empty());
                return messages;
            }

            @SneakyThrows
            private List<Update> handleResponse(
                                                String content,
                                                ModelUsageStats stats,
                                                long elapsedTimeMs
            ) {
                final var messages = new ArrayList<Update>();
                if (historical) {
                    final var node = mapper.readTree(content);
                    final var output = node.get(Agent.OUTPUT_VARIABLE_NAME).asText();
                    messages.add(Printer.assistantMessage(output).withImportant(true));
                    var infoMessage = Printer.Colours.WHITE + "%s %s."
                            .formatted(Severity.SUCCESS.getEmoji(),
                                       ErrorType.SUCCESS.getMessage());
                    infoMessage += Printer.Colours.GRAY + " (Time taken: %.3f seconds, Tokens used: %d)"
                            .formatted(Utils.toMillis(elapsedTimeMs),
                                       stats.getTotalTokens());
                    messages.add(Printer.assistantMessage(infoMessage));
                }
                return messages;
            }

        });
    }

    @SneakyThrows
    private static String prompt(String xml) {
        return xmlMapper.readTree(xml).get("data").asText();
    }

    @SneakyThrows
    private static final String prettyPrintXML(String xml) {
        return xmlMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(xmlMapper.readTree(xml));
    }

}
