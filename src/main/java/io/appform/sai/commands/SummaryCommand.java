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
package io.appform.sai.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.filesystem.session.FileSystemSessionStore;
import com.phonepe.sentinelai.session.QueryDirection;

import io.appform.sai.SaiCommand;
import io.appform.sai.Settings;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.Callable;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Slf4j
@Command(name = "summary", description = "Show detailed summary of a specific session")
@SuppressWarnings("java:S106")
public class SummaryCommand implements Callable<Integer> {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @ParentCommand
    private SaiCommand parent;

    @Parameters(index = "0", description = "The session ID to view the summary for")
    private String sessionId;

    @Override
    @SneakyThrows
    public Integer call() {
        if (Strings.isNullOrEmpty(sessionId)) {
            System.err.println("Session ID is required.");
            return 1;
        }

        final var settingsBuilder = Settings.builder();
        if (!Strings.isNullOrEmpty(parent.getDataDir())) {
            settingsBuilder.dataDir(parent.getDataDir());
        }
        final var settings = settingsBuilder.build();

        final var dataDirPath = Paths.get(settings.getDataDir(), "sessions");
        if (!Files.exists(dataDirPath)) {
            System.err.println("No sessions found.");
            return 1;
        }

        final var mapper = JsonUtils.createMapper();
        final var sessionStore = new FileSystemSessionStore(dataDirPath.toAbsolutePath().normalize().toString(),
                                                            mapper,
                                                            1);

        final var sessionSummaryOpt = sessionStore.session(sessionId);

        if (sessionSummaryOpt.isEmpty()) {
            System.err.println("Session not found: " + sessionId);
            return 1;
        }

        final var summary = sessionSummaryOpt.get();
        final var updatedAt = DATE_FORMATTER.format(Instant.ofEpochMilli(summary.getUpdatedAt() / 1000));

        System.out.println("Session Summary for: " + summary.getSessionId());
        System.out.println("-".repeat(80));
        System.out.println("Title:    " + (summary.getTitle() != null ? summary.getTitle() : "N/A"));
        System.out.println();
        System.out.println("Summary:  " + (summary.getSummary() != null ? wrapText(summary.getSummary(), 80, 10)
                : "N/A"));
        System.out.println();

        System.out.println("Keywords:");
        if (summary.getKeywords() != null && !summary.getKeywords().isEmpty()) {
            for (String keyword : summary.getKeywords()) {
                System.out.println("  - " + keyword);
            }
        }
        else {
            System.out.println("  N/A");
        }
        System.out.println();

        System.out.println("Last Summarized Message: " + (summary.getLastSummarizedMessageId() != null ? summary
                .getLastSummarizedMessageId() : "N/A"));
        System.out.println("Updated At:              " + updatedAt);
        System.out.println();

        System.out.println("Raw Data:");
        if (summary.getRaw() != null && !summary.getRaw().isEmpty()) {
            try {
                // Prettify JSON if it's parsable
                Map<String, Object> rawData = mapper.readValue(summary.getRaw(),
                                                               new TypeReference<Map<String, Object>>() {
                                                               });
                System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rawData));
            }
            catch (JsonProcessingException e) {
                // Print as is if not valid JSON map
                System.out.println(summary.getRaw());
            }
        }
        else {
            System.out.println("N/A");
        }

        // Fetch messages to get counts
        final var messagesScrollable = sessionStore.readMessages(summary.getSessionId(),
                                                                 Integer.MAX_VALUE,
                                                                 false,
                                                                 null,
                                                                 QueryDirection.OLDER);
        final var messages = messagesScrollable.getItems();
        if (messages != null && !messages.isEmpty()) {
            System.out.println();
            System.out.println("Message Stats:");
            long userPrompts = messages.stream().filter(m -> m.getMessageType()
                    == com.phonepe.sentinelai.core.agentmessages.AgentMessageType.USER_PROMPT_REQUEST_MESSAGE).count();
            long systemPrompts = messages.stream().filter(m -> m.getMessageType()
                    == com.phonepe.sentinelai.core.agentmessages.AgentMessageType.SYSTEM_PROMPT_REQUEST_MESSAGE)
                    .count();
            long toolCallReqs = messages.stream().filter(m -> m.getMessageType()
                    == com.phonepe.sentinelai.core.agentmessages.AgentMessageType.TOOL_CALL_REQUEST_MESSAGE).count();
            long toolCallResps = messages.stream().filter(m -> m.getMessageType()
                    == com.phonepe.sentinelai.core.agentmessages.AgentMessageType.TOOL_CALL_RESPONSE_MESSAGE).count();
            long textResps = messages.stream().filter(m -> m.getMessageType()
                    == com.phonepe.sentinelai.core.agentmessages.AgentMessageType.TEXT_RESPONSE_MESSAGE).count();

            System.out.println("  Total Messages:        " + messages.size());
            System.out.println("  User Prompts:          " + userPrompts);
            System.out.println("  System Prompts:        " + systemPrompts);
            System.out.println("  Tool Call Requests:    " + toolCallReqs);
            System.out.println("  Tool Call Responses:   " + toolCallResps);
            System.out.println("  Text Responses:        " + textResps);
        }

        System.out.println("-".repeat(80));

        return 0;
    }

    private String wrapText(String text, int wrapLength, int indent) {
        if (text == null) return null;
        String[] words = text.split(" ");
        StringBuilder wrappedLine = new StringBuilder();
        StringBuilder currentLine = new StringBuilder();
        String indentStr = " ".repeat(indent);

        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > wrapLength - indent) {
                wrappedLine.append(currentLine.toString().trim()).append("\n").append(indentStr);
                currentLine.setLength(0);
            }
            currentLine.append(word).append(" ");
        }
        wrappedLine.append(currentLine.toString().trim());
        return wrappedLine.toString();
    }
}
