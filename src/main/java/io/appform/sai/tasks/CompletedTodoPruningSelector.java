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
package io.appform.sai.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.GenericText;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.session.history.selectors.MessageSelector;

import io.appform.sai.tasks.TaskModels.TodoItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Message selector that prunes tool-call traffic for completed todos from the
 * history the model sees, replacing it with one concise summary line per pruned
 * todo.
 *
 * <p>This is a <em>view</em>-only transformation: selectors run on the read
 * paths (agent history assembly and compaction input), never on persistence,
 * so the full session record on disk is unaffected.
 *
 * <p>Attribution rules, in order of reliability:
 * <ol>
 * <li>{@code ToolCallResponse} payloads of task tools ({@code todo_add},
 * {@code todo_update}, ...) carry a {@code todoId} field.</li>
 * <li>{@code ToolCall} arguments of any tool that carries a {@code todoItemId}
 * field. File tools ({@code edit_file}, {@code write_file}) require it;
 * {@code bash} and {@code read_file} accept it optionally, purely for this
 * attribution (those tools never reject a call for omitting it).</li>
 * <li>Anything else has no attribution and is kept.</li>
 * </ol>
 *
 * <p>Only {@code DONE} todos are pruned. Todos that are incomplete, deferred
 * or abandoned keep their tool-call traffic so the model retains working
 * context; abandoned/deferred state is conveyed by the task-tool facts
 * re-injected each run.
 *
 * <p>For each todo whose traffic is actually pruned in this pass, exactly one
 * synthetic {@link GenericText} user message with a deterministic
 * ({@code todo-summary-<todoId>}) message id is injected in place of the first
 * pruned message, so repeated runs yield identical results and the summary
 * does not duplicate once the original messages are compacted away.
 */
@Slf4j
@RequiredArgsConstructor
public class CompletedTodoPruningSelector implements MessageSelector {

    private static final String SUMMARY_MESSAGE_ID_PREFIX = "todo-summary-";

    private final TaskStore store;
    private final ObjectMapper mapper;

    /**
     * Builds the synthetic summary message replacing a pruned todo's traffic.
     * The message id is deterministic per todo so repeated selector runs are
     * idempotent.
     */
    private static GenericText summaryMessage(final String sessionId,
                                              final com.phonepe.sentinelai.core.agentmessages.AgentMessage anchor,
                                              final TodoItem todo) {
        final var summary = "[%s] Completed: %s".formatted(todo.getId(), todo.getText());
        return GenericText.builder()
                .sessionId(sessionId)
                .runId(anchor.getRunId())
                .messageId(SUMMARY_MESSAGE_ID_PREFIX + todo.getId())
                .timestamp(anchor.getTimestamp())
                .role(AgentGenericMessage.Role.USER)
                .text(summary)
                .build();
    }

    private static String toolCallId(final com.phonepe.sentinelai.core.agentmessages.AgentMessage message) {
        if (message instanceof ToolCallResponse toolCallResponse) {
            return toolCallResponse.getToolCallId();
        }
        if (message instanceof ToolCall toolCall) {
            return toolCall.getToolCallId();
        }
        return null;
    }

    @Override
    public List<com.phonepe.sentinelai.core.agentmessages.AgentMessage> select(final String sessionId,
                                                                               final List<com.phonepe.sentinelai.core.agentmessages.AgentMessage> messages) {
        final var doneTodos = doneTodosById();
        if (doneTodos.isEmpty() || messages.isEmpty()) {
            return messages;
        }

        // First pass: attribute each tool call to a DONE todo (or leave it unattributed).
        final var attribution = new HashMap<String, String>(); // toolCallId -> todoId
        for (final var message : messages) {
            final var todoId = attribute(message);
            if (todoId != null && doneTodos.containsKey(todoId)) {
                final var toolCallId = toolCallId(message);
                if (toolCallId != null) {
                    attribution.put(toolCallId, todoId);
                }
            }
        }

        // Second pass: drop both halves of every attributed call pair, injecting
        // the summary line at the position of the first pruned message.
        final var prunedToolCallIds = new HashSet<String>();
        final var summarized = new HashSet<String>();
        final var output = new ArrayList<com.phonepe.sentinelai.core.agentmessages.AgentMessage>(messages.size());
        for (final var message : messages) {
            final var toolCallId = toolCallId(message);
            final var todoId = toolCallId == null ? null : attribution.get(toolCallId);
            if (todoId != null) {
                prunedToolCallIds.add(toolCallId);
                final var todo = doneTodos.get(todoId);
                if (todo != null && summarized.add(todoId)) {
                    output.add(summaryMessage(sessionId, message, todo));
                }
                continue;
            }
            output.add(message);
        }
        if (prunedToolCallIds.isEmpty()) {
            return messages;
        }
        log.debug("Pruned {} tool calls for {} completed todos in session {}",
                  prunedToolCallIds.size(),
                  summarized.size(),
                  sessionId);
        return output;
    }

    /**
     * Resolves the todo a message is attributed to, or null when the message is
     * not attributable or its todo is not DONE.
     */
    private String attribute(final com.phonepe.sentinelai.core.agentmessages.AgentMessage message) {
        if (message instanceof ToolCallResponse toolCallResponse) {
            final var todoId = extractTodoId(toolCallResponse.getResponse());
            return todoId;
        }
        if (message instanceof ToolCall toolCall) {
            return extractTodoItemId(toolCall.getArguments());
        }
        return null;
    }

    /**
     * Resolves the DONE todos of the store, keyed by id.
     */
    private Map<String, TodoItem> doneTodosById() {
        final var done = new HashMap<String, TodoItem>();
        for (final var todo : store.todosInState(TaskModels.TodoState.DONE)) {
            done.put(todo.getId(), todo);
        }
        return done;
    }

    private String extractStringField(final String json, final String field) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            final JsonNode node = mapper.readTree(json);
            final var value = node.get(field);
            return value == null || value.isNull() ? null : value.asText();
        }
        catch (Exception e) {
            return null; // malformed payload: not attributable
        }
    }

    /**
     * Extracts {@code todoId} from a task-tool response payload.
     */
    private String extractTodoId(final String response) {
        return extractStringField(response, "todoId");
    }

    /**
     * Extracts {@code todoItemId} from a file-tool call's arguments.
     */
    private String extractTodoItemId(final String arguments) {
        return extractStringField(arguments, "todoItemId");
    }
}
