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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.filesystem.session.FileSystemSessionStore;
import com.phonepe.sentinelai.session.QueryDirection;
import com.phonepe.sentinelai.session.SessionStore;
import com.phonepe.sentinelai.session.SessionSummary;

import io.appform.sai.SaiCommand;
import io.appform.sai.Settings;

import org.commonmark.node.Document;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.renderer.markdown.MarkdownRenderer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.concurrent.Callable;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Slf4j
@Command(name = "export-session", description = "Export a session to a markdown file")
public class ExportSessionCommand implements Callable<Integer> {

    @ParentCommand
    private SaiCommand parent;

    @Parameters(index = "0", description = "The session ID to view the summary for")
    private String sessionId;

    @Parameters(index = "1", description = "The output file path to export the session to", arity = "0..1")
    private String filePath;

    private static Node bold(Node root, String text) {
        final var strongEmphasis = new StrongEmphasis();
        strongEmphasis.appendChild(new Text(text));
        root.appendChild(strongEmphasis);
        return root;
    }

    private static Node code(Node root, String text) {
        final var code = new FencedCodeBlock();
        code.setInfo("json");
        code.setLiteral(text);
        root.appendChild(code);
        return root;
    }

    private static Node h1(Node root, String text) {
        final var heading = new Heading();
        heading.setLevel(1);
        heading.appendChild(new Text(text));
        root.appendChild(heading);
        return root;
    }


    private static Node lineBreak(Node root) {
        root.appendChild(new SoftLineBreak());
        return root;
    }

    private static Node paragraph(Node root) {
        final var paragraph = new Paragraph();
        root.appendChild(paragraph);
        return paragraph;
    }

    @SneakyThrows
    private static void printMessage(Document document, AgentMessage message, ObjectMapper mapper) {
        final var messageNode = paragraph(document);
        bold(messageNode, "Type:");
        text(messageNode, " " + message.getMessageType());
        lineBreak(messageNode);
        bold(messageNode, "Timestamp:");
        text(messageNode,
             " " + DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss.SSS")
                     .withZone(ZoneId.systemDefault())
                     .format(new Date(message.getTimestamp() / 1000)
                             .toInstant()));
        lineBreak(messageNode);
        code(messageNode, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(message));
    }

    private static Node text(Node root, String text) {
        root.appendChild(new Text(text));
        return root;
    }

    @Override
    @SuppressWarnings("java:S106")
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
        final var sessionPath = dataDirPath.toAbsolutePath().normalize().toString();
        final var sessionStore = new FileSystemSessionStore(
                                                            sessionPath,
                                                            mapper,
                                                            1);

        final var sessionSummaryOpt = sessionStore.session(sessionId);

        if (sessionSummaryOpt.isEmpty()) {
            System.err.println("Session not found: " + sessionId);
            return 1;
        }

        final var summary = sessionSummaryOpt.get();
        final var document = new Document();
        addSessionDetails(document, summary);
        addMessages(document, sessionId, sessionStore, mapper);
        final var renderer = MarkdownRenderer.builder()
                .build();

        final var output = renderer.render(document);
        if (Strings.isNullOrEmpty(filePath)) {
            System.out.println(output);
        }
        else {
            try {
                Files.writeString(Path.of(filePath), output);
            }
            catch (Exception e) {
                final var rootCause = AgentUtils.rootCause(e);
                System.err.println("Failed to export session: to file %s. Reason: %s"
                        .formatted(filePath, rootCause.getMessage()));
                return 1;
            }
        }
        return 0;
    }

    private void addMessages(Document document, String sessionId, SessionStore sessionStore, ObjectMapper mapper) {
        sessionStore.readMessages(sessionId, Integer.MAX_VALUE, false, null, QueryDirection.NEWER)
                .getItems()
                .forEach(message -> printMessage(document, message, mapper));
    }

    private void addSessionDetails(Document document, SessionSummary summary) {
        h1(document, summary.getTitle());
        final var summaryText = paragraph(document);
        bold(summaryText, "Session ID:");
        text(summaryText, " " + summary.getSessionId());
        lineBreak(summaryText);
        bold(summaryText, "Updated At:");
        text(summaryText,
             " " + DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                     .withZone(ZoneId.systemDefault())
                     .format(new Date(summary.getUpdatedAt() / 1000).toInstant()));
        lineBreak(summaryText);
        bold(summaryText, "Summary:");
        text(summaryText, " " + summary.getSummary());
        document.appendChild(summaryText);
    }
}
