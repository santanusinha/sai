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
package io.appform.sai.cli.slash.commands;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.filesystem.session.FileSystemSessionStore;
import com.phonepe.sentinelai.session.QueryDirection;
import com.phonepe.sentinelai.session.SessionSummary;

import io.appform.sai.Settings;
import io.appform.sai.cli.slash.SlashRootCommand;
import io.appform.sai.render.TodoCalloutRenderer;
import io.appform.sai.tasks.TaskIO.TodoItemView;
import io.appform.sai.tasks.TaskModels.Task;
import io.appform.sai.tasks.TaskModels.TodoItem;
import io.appform.sai.tasks.TaskModels.TodoState;
import io.appform.sai.tasks.TaskStore;
import io.appform.sai.term.Printer;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * {@code /info} — pretty-print a summary of the current interactive session, including metadata,
 * keywords, per-type message counts, and aggregate token usage (input/output/cached tokens and
 * cache hit rate). This is the interactive counterpart of the {@code session-summary} CLI
 * subcommand, scoped to the current session ID from the active {@link io.appform.sai.Settings}.
 */
@Slf4j
@Command(name = "info", description = "Show a summary of the current session")
public class InfoCommand implements Runnable {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    /**
     * Maximum number of work-note lines shown in the /info output.
     */
    private static final int NOTE_PREVIEW_LINES = 8;

    @ParentCommand
    private SlashRootCommand parent;

    /**
     * Extracts usage stats from a stored message when it is a response type that carries them
     * ({@link Text} or {@link StructuredOutput}); returns {@code null} otherwise.
     */
    private static ModelUsageStats statsOf(AgentMessage message) {
        if (message instanceof Text text) {
            return text.getStats();
        }
        if (message instanceof StructuredOutput structuredOutput) {
            return structuredOutput.getStats();
        }
        return null;
    }

    @Override
    public void run() {
        final var context = parent.getContext();
        final var printer = context.getPrinter();
        final var settings = context.getSettings();
        final var sessionId = settings.getSessionId();

        final var dataDirPath = Paths.get(settings.getDataDir(), "sessions");
        if (!Files.exists(dataDirPath)) {
            printer.print(Printer.systemMessage(
                                                Printer.Colours.YELLOW
                                                        + "No session data found for session: " + sessionId
                                                        + Printer.Colours.RESET));
            return;
        }

        final var mapper = JsonUtils.createMapper();
        final var sessionStore = FileSystemSessionStore.builder()
                .baseDir(dataDirPath.toAbsolutePath().normalize().toString())
                .mapper(mapper)
                .cacheSize(1)
                .build();

        final var sessionSummaryOpt = sessionStore.session(sessionId);
        if (sessionSummaryOpt.isEmpty()) {
            printer.print(Printer.systemMessage(
                                                Printer.Colours.YELLOW
                                                        + "Session not found: " + sessionId
                                                        + Printer.Colours.RESET));
            return;
        }

        final var summary = sessionSummaryOpt.get();
        final var sb = new StringBuilder();
        appendSessionMetadata(sb, summary);
        appendTaskState(sb, settings);
        appendMessageStats(sb, sessionStore, summary.getSessionId());
        sb.append(Printer.Colours.GRAY).append("-".repeat(80)).append(Printer.Colours.RESET);
        printer.print(Printer.raw(sb.toString()));
    }

    private void appendMessageStats(StringBuilder sb, FileSystemSessionStore sessionStore, String sessionId) {
        final var messagesScrollable = sessionStore.readMessages(sessionId,
                                                                 Integer.MAX_VALUE,
                                                                 false,
                                                                 null,
                                                                 QueryDirection.OLDER);
        final var messages = messagesScrollable.getItems();
        if (messages == null || messages.isEmpty()) {
            return;
        }

        final var G = Printer.Colours.GRAY;
        final var W = Printer.Colours.WHITE;
        final var Y = Printer.Colours.YELLOW;
        final var R = Printer.Colours.RESET;

        sb.append('\n');
        sb.append(Y).append("Message Stats:").append(R).append('\n');
        long userPrompts = countByType(messages, AgentMessageType.USER_PROMPT_REQUEST_MESSAGE);
        long systemPrompts = countByType(messages, AgentMessageType.SYSTEM_PROMPT_REQUEST_MESSAGE);
        long toolCallReqs = countByType(messages, AgentMessageType.TOOL_CALL_REQUEST_MESSAGE);
        long toolCallResps = countByType(messages, AgentMessageType.TOOL_CALL_RESPONSE_MESSAGE);
        long textResps = countByType(messages, AgentMessageType.TEXT_RESPONSE_MESSAGE);

        sb.append(G).append("  Total Messages:        ").append(R).append(W).append(messages.size()).append(R)
                .append('\n');
        sb.append(G).append("  User Prompts:          ").append(R).append(W).append(userPrompts).append(R)
                .append('\n');
        sb.append(G).append("  System Prompts:        ").append(R).append(W).append(systemPrompts).append(R)
                .append('\n');
        sb.append(G).append("  Tool Call Requests:    ").append(R).append(W).append(toolCallReqs).append(R)
                .append('\n');
        sb.append(G).append("  Tool Call Responses:   ").append(R).append(W).append(toolCallResps).append(R)
                .append('\n');
        sb.append(G).append("  Text Responses:        ").append(R).append(W).append(textResps).append(R).append('\n');

        appendTokenUsage(sb, messages);
    }

    private void appendSessionMetadata(StringBuilder sb, SessionSummary summary) {
        final var C = Printer.Colours.CYAN;
        final var G = Printer.Colours.GRAY;
        final var W = Printer.Colours.WHITE;
        final var R = Printer.Colours.RESET;

        sb.append(C).append("Session Summary for: ").append(W).append(summary.getSessionId()).append(R).append('\n');
        sb.append(G).append("-".repeat(80)).append(R).append('\n');
        sb.append(G).append("Title:    ").append(R).append(W)
                .append(summary.getTitle() != null ? summary.getTitle() : "N/A").append(R).append('\n');
        sb.append('\n');
        sb.append(G).append("Summary:  ").append(R).append(W)
                .append(summary.getSummary() != null ? summary.getSummary() : "N/A").append(R).append('\n');
        sb.append('\n');

        sb.append(G).append("Keywords:").append(R).append('\n');
        if (summary.getKeywords() != null && !summary.getKeywords().isEmpty()) {
            for (String keyword : summary.getKeywords()) {
                sb.append(G).append("  - ").append(W).append(keyword).append(R).append('\n');
            }
        }
        else {
            sb.append(G).append("  N/A").append(R).append('\n');
        }
        sb.append('\n');

        final var updatedAt = summary.getUpdatedAt() > 0
                ? DATE_FORMATTER.format(Instant.ofEpochMilli(summary.getUpdatedAt() / 1000))
                : "N/A";
        sb.append(G).append("Last Summarized Message: ").append(R).append(W)
                .append(summary.getLastSummarizedMessageId() != null ? summary.getLastSummarizedMessageId() : "N/A")
                .append(R).append('\n');
        sb.append(G).append("Updated At:              ").append(R).append(W).append(updatedAt).append(R).append('\n');
    }

    /**
     * Appends the current task, its todo list, and the work note state.
     * Reads a fresh {@link TaskStore} from the session scratch dir so the
     * output always reflects the journal on disk. Quietly skips the section
     * when task tooling data is unavailable.
     */
    private void appendTaskState(final StringBuilder sb, final Settings settings) {
        final TaskStore store;
        try {
            store = new TaskStore(Paths.get(settings.scratchDir(), "task-todo"),
                                  JsonUtils.createMapper());
        }
        catch (Exception e) {
            log.warn("Could not open task store for /info: {}", e.getMessage());
            return;
        }

        final var G = Printer.Colours.GRAY;
        final var W = Printer.Colours.WHITE;
        final var Y = Printer.Colours.YELLOW;
        final var R = Printer.Colours.RESET;

        sb.append('\n');
        final var activeTaskOpt = store.activeTask();
        if (activeTaskOpt.isEmpty()) {
            sb.append(Y).append("Active Task:").append(R).append('\n')
                    .append(G).append("  none").append(R).append('\n');
            appendTaskSummaryLine(sb, store);
            return;
        }

        final Task task = activeTaskOpt.get();
        sb.append(Y).append("Active Task:").append(R).append('\n')
                .append(G).append("  ").append(task.getId()).append("  ").append(R)
                .append(W).append(task.getTitle()).append(R).append('\n');

        final var todos = store.activeTaskTodos().stream().map(this::view).toList();
        sb.append(TodoCalloutRenderer.renderTodoList("  Todos:", todos)).append('\n');

        final var note = store.readNote();
        sb.append(G).append("  Work Note:      ").append(R);
        if (note == null || note.isBlank()) {
            sb.append(G).append("none").append(R).append('\n');
        }
        else {
            final var lines = note.lines().toList();
            sb.append(W).append(lines.size()).append(" lines").append(R).append('\n');
            lines.stream().limit(NOTE_PREVIEW_LINES).forEach(line -> sb
                    .append(G).append("    ").append(R).append(W).append(line).append(R).append('\n'));
            if (lines.size() > NOTE_PREVIEW_LINES) {
                sb.append(G).append("    ...").append(R).append('\n');
            }
        }
        appendTaskSummaryLine(sb, store);
    }

    /**
     * Appends completed-task and deferred-todo counts.
     */
    private void appendTaskSummaryLine(final StringBuilder sb, final TaskStore store) {
        final var G = Printer.Colours.GRAY;
        final var W = Printer.Colours.WHITE;
        final var R = Printer.Colours.RESET;
        sb.append(G).append("  Completed Tasks: ").append(R).append(W)
                .append(store.completedTasks().size()).append(R);
        sb.append(G).append("   Deferred Todos: ").append(R).append(W)
                .append(store.todosInState(TodoState.DEFERRED).size()).append(R).append('\n');
    }

    private void appendTokenUsage(StringBuilder sb, List<AgentMessage> messages) {
        long requestTokens = 0;
        long responseTokens = 0;
        long cachedTokens = 0;
        for (var message : messages) {
            final var stats = statsOf(message);
            if (stats == null) {
                continue;
            }
            requestTokens += stats.getRequestTokens();
            responseTokens += stats.getResponseTokens();
            cachedTokens += stats.getRequestTokenDetails().getCachedTokens();
        }
        if (requestTokens <= 0 && responseTokens <= 0) {
            return;
        }

        final var G = Printer.Colours.GRAY;
        final var W = Printer.Colours.WHITE;
        final var Y = Printer.Colours.YELLOW;
        final var R = Printer.Colours.RESET;

        final double hitRate = requestTokens > 0 ? (cachedTokens * 100.0 / requestTokens) : 0.0;
        sb.append('\n');
        sb.append(Y).append("Token Usage:").append(R).append('\n');
        sb.append(G).append("  Input Tokens:          ").append(R).append(W).append(requestTokens).append(R)
                .append('\n');
        sb.append(G).append("  Output Tokens:         ").append(R).append(W).append(responseTokens).append(R)
                .append('\n');
        sb.append(G).append("  Cached Tokens:         ").append(R).append(W).append(cachedTokens).append(R).append(
                                                                                                                   '\n');
        sb.append(G).append("  Cache Hit Rate:        ").append(R).append(W)
                .append(String.format("%.1f%%", hitRate)).append(R).append('\n');
    }

    private long countByType(List<AgentMessage> messages, AgentMessageType type) {
        return messages.stream().filter(m -> m.getMessageType() == type).count();
    }

    /**
     * Maps a {@link TodoItem} to the view used by {@link TodoCalloutRenderer}.
     */
    private TodoItemView view(final TodoItem todo) {
        return TodoItemView.builder()
                .id(todo.getId())
                .taskId(todo.getTaskId())
                .text(todo.getText())
                .state(todo.getState() == null ? "" : todo.getState().name())
                .reason(todo.getReason())
                .build();
    }
}
