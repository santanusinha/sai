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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.events.AgentEventVisitor;
import com.phonepe.sentinelai.core.events.InputReceivedAgentEvent;
import com.phonepe.sentinelai.core.events.MessageReceivedAgentEvent;
import com.phonepe.sentinelai.core.events.MessageSentAgentEvent;
import com.phonepe.sentinelai.core.events.OutputErrorAgentEvent;
import com.phonepe.sentinelai.core.events.OutputGeneratedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCallApprovalDeniedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCallCompletedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCalledAgentEvent;

import io.appform.sai.Printer.Update;
import io.appform.sai.models.Actor;
import io.appform.sai.models.Severity;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

@AllArgsConstructor
public class EventPrinter implements AgentEventVisitor<Void> {

        private final ObjectMapper mapper;
        private final Printer printer;

        @Override
        public Void visit(final MessageReceivedAgentEvent messageReceived) {
            return null;
        }

        @Override
        public Void visit(final MessageSentAgentEvent messageSent) {
            return null;
        }

        @Override
        @SneakyThrows
        public Void visit(final OutputGeneratedAgentEvent outputGeneratedAgentEvent) {
            return null;
        }

        @Override
        public Void visit(final ToolCallApprovalDeniedAgentEvent toolCallApprovalDenied) {
            // Note:: this is a no-op for now, there is no approval deny here #YOLO
            printer.print(Update.builder()
                    .actor(Actor.SYSTEM)
                    .severity(Severity.ERROR)
                    .colour(Printer.Colours.RED)
                    .data("Tool call deined by user")
                    .build());
            return null;
        }

        @Override
        public Void visit(final ToolCallCompletedAgentEvent toolCallCompleted) {
/*             final var content = toolCallCompleted.getErrorType().equals(ErrorType.SUCCESS)
                    ? "%s%s%s".formatted(Printer.Colours.WHITE_ON_DARK_GRAY_BACKGROUND,
                                         toolCallCompleted.getContent(),
                                         Printer.Colours.RESET)
                            : "%sError: %s - %s%s".formatted(Printer.Colours.RED,
                                                     toolCallCompleted.getErrorType(),
                                                     toolCallCompleted.getContent(),
                                                     Printer.Colours.RESET);
            printer.print(Update.builder()
                    .actor(Actor.SYSTEM)
                    .severity(Severity.NORMAL)
                    .colour(Printer.Colours.RESET)
                    .data(content)
                    .build());
  */           return null;
        }

        @Override
        public Void visit(final ToolCalledAgentEvent toolCalled) {
/*             final var content = "Tool %s%s (id: %s)%s called with arguments: %s%s%s"
                    .formatted(Printer.Colours.CYAN,
                            toolCalled.getToolCallName(),
                               toolCalled.getToolCallId(),
                               Printer.Colours.RESET,
                               Printer.Colours.PURPLE,
                               toolCalled.getArguments(),
                               Printer.Colours.RESET);
            printer.print(Update.builder()
                    .actor(Actor.ASSISTANT)
                    .severity(Severity.NORMAL)
                    .colour(Printer.Colours.RESET)
                    .data(content)
                    .build());
 */            return null;
        }

        @Override
        @SneakyThrows
        public Void visit(final InputReceivedAgentEvent inputReceived) {
           return null;
        }

        @Override
        public Void visit(final OutputErrorAgentEvent outputErrorAgentEvent) {
            return null;
        }
    }
