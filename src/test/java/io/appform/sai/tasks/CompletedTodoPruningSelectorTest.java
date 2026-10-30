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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.GenericText;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tests for {@link CompletedTodoPruningSelector}: DONE-todo tool-call pruning
 * with pair integrity, deterministic summary injection, and no-ops for
 * incomplete/unattributed traffic.
 */
class CompletedTodoPruningSelectorTest {

    private static final String SESSION = "test-session";

    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path root;

    private TaskStore store;
    private CompletedTodoPruningSelector selector;

    private static ToolCall toolCall(final String toolCallId, final String toolName, final String arguments) {
        return ToolCall.builder()
                .sessionId(SESSION)
                .runId("run-1")
                .toolCallId(toolCallId)
                .toolName(toolName)
                .arguments(arguments)
                .build();
    }

    private static String toolCallIdOf(final AgentMessage message) {
        if (message instanceof ToolCall toolCall) {
            return toolCall.getToolCallId();
        }
        if (message instanceof ToolCallResponse toolCallResponse) {
            return toolCallResponse.getToolCallId();
        }
        return null;
    }

    private static ToolCallResponse toolCallResponse(final String toolCallId,
                                                     final String toolName,
                                                     final String response) {
        return ToolCallResponse.builder()
                .sessionId(SESSION)
                .runId("run-1")
                .toolCallId(toolCallId)
                .toolName(toolName)
                .response(response)
                .build();
    }

    private static UserPrompt userPrompt(final String messageId, final String content) {
        return UserPrompt.builder()
                .sessionId(SESSION)
                .runId("run-1")
                .messageId(messageId)
                .content(content)
                .build();
    }

    @Test
    void emptyMessagesPassThrough() {
        assertThat(selector.select(SESSION, List.of())).isEmpty();
    }

    @Test
    void keepsToolCallsForIncompleteTodosAndUnattributedCalls() {
        final var doneTodo = addTodo("done item");
        markDone(doneTodo);
        final var pendingTodo = addTodo("pending item");

        final var messages = List.<AgentMessage>of(
                                                   toolCall("call-1",
                                                            "task_tools_extension_todo_update",
                                                            "{\"todoId\":\"%s\",\"newState\":\"done\"}".formatted(
                                                                                                                  doneTodo)),
                                                   toolCallResponse("call-1",
                                                                    "task_tools_extension_todo_update",
                                                                    "{\"todoId\":\"%s\",\"newState\":\"DONE\"}"
                                                                            .formatted(doneTodo)),
                                                   toolCall("call-2",
                                                            "core_tool_box_edit_file",
                                                            "{\"filePath\":\"/tmp/Bar.java\",\"todoItemId\":\"%s\"}"
                                                                    .formatted(pendingTodo)),
                                                   toolCallResponse("call-2",
                                                                    "core_tool_box_edit_file",
                                                                    "{\"newChecksum\":\"def\"}"),
                                                   toolCall("call-3", "core_tool_box_bash", "{\"command\":\"ls -la\"}"),
                                                   toolCallResponse("call-3",
                                                                    "core_tool_box_bash",
                                                                    "{\"statusCode\":0}"));

        final var selected = selector.select(SESSION, messages);

        // The done todo's pair is pruned; the incomplete todo's and bash calls survive.
        assertThat(selected).hasSize(5);
        assertThat(selected).noneMatch(message -> "call-1".equals(toolCallIdOf(message)));
        assertThat(selected).anyMatch(message -> "call-2".equals(toolCallIdOf(message)));
        assertThat(selected).anyMatch(message -> "call-3".equals(toolCallIdOf(message)));
        assertThat(selected).anyMatch(GenericText.class::isInstance);
    }

    @Test
    void keepsTrafficWhenTodoMentionedButNotDone() {
        final var todo = addTodo("still working on it");

        final var messages = List.<AgentMessage>of(
                                                   toolCall("call-1",
                                                            "task_tools_extension_todo_update",
                                                            "{\"todoId\":\"%s\",\"newState\":\"done\"}".formatted(
                                                                                                                  todo)),
                                                   toolCallResponse("call-1",
                                                                    "task_tools_extension_todo_update",
                                                                    "{\"todoId\":\"%s\",\"newState\":\"DONE\"}"
                                                                            .formatted(todo)));

        // The payloads mention the todo, but it is not DONE: nothing is pruned.
        assertThat(selector.select(SESSION, messages)).isEqualTo(messages);
    }

    @Test
    void malformedPayloadsAreKept() {
        final var todo = addTodo("malformed item");
        markDone(todo);

        final var messages = List.<AgentMessage>of(
                                                   toolCall("call-1", "core_tool_box_bash", "not json at all"),
                                                   toolCallResponse("call-1", "core_tool_box_bash", "{broken"));

        // Unparseable payloads are not attributable, so the pair is kept.
        assertThat(selector.select(SESSION, messages)).isEqualTo(messages);
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    @Test
    void noOpWithoutAttributableTraffic() {
        final var messages = List.<AgentMessage>of(
                                                   userPrompt("user-1", "hello"),
                                                   userPrompt("user-2", "goodbye"));

        assertThat(selector.select(SESSION, messages)).isEqualTo(messages);
    }

    @Test
    void prunesBashAndReadCallsAttributedToDoneTodos() {
        final var todo = addTodo("run the build");
        markDone(todo);

        final var messages = List.<AgentMessage>of(
                                                   toolCall("call-1",
                                                            "core_tool_box_bash",
                                                            "{\"command\":\"mvn test\",\"todoItemId\":\"%s\"}"
                                                                    .formatted(todo)),
                                                   toolCallResponse("call-1",
                                                                    "core_tool_box_bash",
                                                                    "{\"statusCode\":0}"),
                                                   toolCall("call-2",
                                                            "core_tool_box_read_file",
                                                            "{\"filePath\":\"/tmp/Foo.java\",\"todoItemId\":\"%s\"}"
                                                                    .formatted(todo)),
                                                   toolCallResponse("call-2",
                                                                    "core_tool_box_read_file",
                                                                    "{\"checksum\":\"abc\"}"),
                                                   // Same tools, no todoItemId: kept.
                                                   toolCall("call-3", "core_tool_box_bash", "{\"command\":\"ls\"}"),
                                                   toolCallResponse("call-3",
                                                                    "core_tool_box_bash",
                                                                    "{\"statusCode\":0}"));

        final var selected = selector.select(SESSION, messages);

        // Optional attribution on bash/read_file prunes the DONE todo's pairs.
        assertThat(selected).hasSize(3);
        assertThat(selected).noneMatch(message -> "call-1".equals(toolCallIdOf(message)));
        assertThat(selected).noneMatch(message -> "call-2".equals(toolCallIdOf(message)));
        assertThat(selected).anyMatch(message -> "call-3".equals(toolCallIdOf(message)));
        final var summary = selected.stream()
                .filter(GenericText.class::isInstance)
                .map(GenericText.class::cast)
                .toList();
        assertThat(summary).hasSize(1);
        assertThat(summary.get(0).getMessageId()).isEqualTo("todo-summary-" + todo);
    }

    @Test
    void prunesDoneTodoToolCallPairsAndInjectsSummary() {
        final var todo = addTodo("fix the bug");
        markDone(todo);

        final var messages = List.<AgentMessage>of(
                                                   userPrompt("user-1", "please fix the bug"),
                                                   toolCall("call-1",
                                                            "core_tool_box_edit_file",
                                                            "{\"filePath\":\"/tmp/Foo.java\",\"todoItemId\":\"%s\"}"
                                                                    .formatted(todo)),
                                                   toolCallResponse("call-1",
                                                                    "core_tool_box_edit_file",
                                                                    "{\"newChecksum\":\"abc\"}"),
                                                   toolCall("call-2",
                                                            "task_tools_extension_todo_update",
                                                            "{\"todoId\":\"%s\",\"newState\":\"done\"}".formatted(
                                                                                                                  todo)),
                                                   toolCallResponse("call-2",
                                                                    "task_tools_extension_todo_update",
                                                                    "{\"todoId\":\"%s\",\"newState\":\"DONE\"}"
                                                                            .formatted(todo)),
                                                   userPrompt("user-2", "thanks"));

        final var selected = selector.select(SESSION, messages);

        // Both halves of both attributed pairs are gone; one summary replaces them.
        assertThat(selected).hasSize(3);
        final var summary = selected.stream()
                .filter(GenericText.class::isInstance)
                .map(GenericText.class::cast)
                .toList();
        assertThat(summary).hasSize(1);
        assertThat(summary.get(0).getMessageId()).isEqualTo("todo-summary-" + todo);
        assertThat(summary.get(0).getText()).contains(todo).contains("fix the bug");
        assertThat(summary.get(0).getRole()).isEqualTo(GenericText.Role.USER);
        // The summary is injected at the position of the first pruned message.
        assertThat(selected.get(1)).isEqualTo(summary.get(0));
        // Non-tool messages survive.
        assertThat(selected).anyMatch(message -> message.getMessageId().equals("user-1"));
        assertThat(selected).anyMatch(message -> message.getMessageId().equals("user-2"));
    }

    @Test
    void selectionIsDeterministicAcrossRuns() {
        final var todo = addTodo("deterministic item");
        markDone(todo);
        final var messages = List.<AgentMessage>of(
                                                   toolCall("call-1",
                                                            "task_tools_extension_todo_update",
                                                            "{\"todoId\":\"%s\",\"newState\":\"done\"}".formatted(
                                                                                                                  todo)),
                                                   toolCallResponse("call-1",
                                                                    "task_tools_extension_todo_update",
                                                                    "{\"todoId\":\"%s\",\"newState\":\"DONE\"}"
                                                                            .formatted(todo)));

        final var first = selector.select(SESSION, messages);
        final var second = selector.select(SESSION, messages);

        assertThat(first).isEqualTo(second);
        assertThat(first.stream()
                .filter(GenericText.class::isInstance)
                .map(GenericText.class::cast)
                .findFirst()
                .orElseThrow()
                .getMessageId()).isEqualTo("todo-summary-" + todo);
    }

    @BeforeEach
    void setUp() {
        store = new TaskStore(root, mapper);
        store.startTask("task-0001", "test task");
        selector = new CompletedTodoPruningSelector(store, mapper);
    }

    private String addTodo(final String text) {
        final var todoId = "todo-%04d".formatted(store.allTodos().size() + 1);
        store.append(TaskModels.JournalEvent.builder()
                .ts(LocalDateTime.now().toString())
                .event("created")
                .todoId(todoId)
                .taskId("task-0001")
                .text(text)
                .build());
        return todoId;
    }

    private void markDone(final String todoId) {
        store.transition(todoId, TaskModels.TodoState.DONE, null, "agent");
    }
}
