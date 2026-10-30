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
package io.appform.sai.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.errors.ErrorType;

import io.appform.sai.term.Printer;

import org.junit.jupiter.api.Test;

import java.util.Objects;

/**
 * Unit tests for the todo callouts rendered by {@link MessagePrinter} when task
 * tool calls complete: done todos are dim grey with strikethrough, incomplete
 * ones white, and state changes get an emoji marker.
 */
class MessagePrinterTodoCalloutTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String render(String toolName, String jsonResponse) {
        final var printer = new MessagePrinter(null, MAPPER, false);
        final var toolResponse = ToolCallResponse.builder()
                .sessionId("session-1")
                .runId("run-1")
                .toolCallId("call-1")
                .toolName(toolName)
                .errorType(ErrorType.SUCCESS)
                .response(jsonResponse)
                .build();
        return printer.visit(toolResponse)
                .stream()
                .map(Printer.Update::getData)
                .filter(Objects::nonNull)
                .reduce("", (a, b) -> a + "\n" + b);
    }

    @Test
    void deferredTodosRendersCalloutList() {
        final var rendered = render("task_tools_extension_deferred_todos",
                                    "{\"filtered\":true,\"todos\":["
                                            + "{\"id\":\"todo-0005\",\"text\":\"Parked work\",\"state\":\"DEFERRED\"}]}");
        assertTrue(rendered.contains("Deferred todos"), "list has the deferred title");
        assertTrue(rendered.contains("todo-0005"), "deferred todo is listed");
        assertTrue(rendered.contains("Parked work"), "deferred todo text is listed");
    }

    @Test
    void noteReadErrorSurfacedAsSystemMessage() {
        final var rendered = render("task_tools_extension_note_read",
                                    "{\"archived\":true,\"error\":\"No archived note for task: task-0009\"}");
        assertTrue(rendered.contains("No archived note for task"), "error is surfaced");
    }

    @Test
    void noteReadRendersArchivedNoteMarkdown() {
        final var rendered = render("task_tools_extension_note_read",
                                    "{\"content\":\"# Task: Earlier work\\n\\nDone.\",\"archived\":true}");
        assertTrue(rendered.contains("Note"), "note header is shown");
        assertTrue(rendered.contains("Earlier work"), "archived note content is rendered");
    }

    @Test
    void noteWriteRendersNoteMarkdownInsteadOfRawJson() {
        final var rendered = render("task_tools_extension_note_write",
                                    "{\"content\":\"# Task: Fix the bug\\n\\n## Where I am\\n\\nInvestigating.\",\"archived\":false}");
        assertTrue(rendered.contains("Note updated"), "note header is shown");
        assertTrue(rendered.contains("Task: Fix the bug"), "note content is rendered as markdown");
        assertTrue(rendered.contains("Where I am"), "note sections are rendered");
        assertTrue(!rendered.contains("\"content\":"), "raw tool JSON is not printed");
    }

    @Test
    void taskCompleteBlockedRendersIncompleteTodos() {
        final var rendered = render("task_tools_extension_mark_task_complete",
                                    "{\"taskId\":\"task-0001\",\"incomplete\":[{\"id\":\"todo-0009\","
                                            + "\"text\":\"Still pending\",\"state\":\"INCOMPLETE\"}]}");
        assertTrue(rendered.contains("cannot be completed"), "blocked completion is called out");
        assertTrue(rendered.contains("todo-0009"), "incomplete todo is listed");
        assertTrue(rendered.contains("Still pending"), "incomplete todo text is listed");
    }

    @Test
    void taskCompleteRendersTaskCompletedCallout() {
        final var rendered = render("task_tools_extension_mark_task_complete",
                                    "{\"taskId\":\"task-0001\",\"compactionTriggered\":true}");
        assertTrue(rendered.contains("task-0001"), "callout includes the task id");
        assertTrue(rendered.contains("Task completed"), "callout announces the task completion");
        assertTrue(rendered.contains("Compaction triggered"), "callout mentions compaction");
    }

    @Test
    void taskStartRendersTaskStartedCallout() {
        final var rendered = render("task_tools_extension_task_start",
                                    "{\"taskId\":\"task-0001\"}");
        assertTrue(rendered.contains("task-0001"), "callout includes the task id");
        assertTrue(rendered.contains("Task started"), "callout announces the task start");
    }

    @Test
    void todoAddRendersNewTodoCallout() {
        final var rendered = render("task_tools_extension_todo_add",
                                    "{\"todoId\":\"todo-0003\",\"newState\":\"INCOMPLETE\",\"text\":\"New work\"}");
        assertTrue(rendered.contains("todo-0003"), "callout includes the new todo id");
        assertTrue(rendered.contains("New work"), "callout includes the new todo text");
        assertTrue(rendered.contains("(new todo)"), "callout is marked as a new todo");
    }

    @Test
    void todoListErrorSurfacedAsSystemMessage() {
        final var rendered = render("task_tools_extension_todo_list",
                                    "{\"filtered\":true,\"todos\":[],\"error\":\"No active task.\"}");
        assertTrue(rendered.contains("No active task."), "error is surfaced");
    }

    @Test
    void todoListRendersCalloutListInsteadOfRawJson() {
        final var rendered = render("task_tools_extension_todo_list",
                                    "{\"taskId\":\"task-0001\",\"filtered\":false,\"todos\":["
                                            + "{\"id\":\"todo-0001\",\"text\":\"Pending work\",\"state\":\"INCOMPLETE\"},"
                                            + "{\"id\":\"todo-0002\",\"text\":\"Finished work\",\"state\":\"DONE\"}]}");
        assertTrue(rendered.contains("Todos"), "list has a title");
        assertTrue(rendered.contains("todo-0001"), "todo ids are listed");
        assertTrue(rendered.contains("Pending work"), "incomplete todo text is listed");
        assertTrue(rendered.contains("Finished work"), "done todo text is listed");
        assertTrue(!rendered.contains("\"todos\":"), "raw tool JSON is not printed");
    }

    @Test
    void todoUpdateDoneRendersStrikethroughCallout() {
        final var rendered = render("task_tools_extension_todo_update",
                                    "{\"todoId\":\"todo-0001\",\"newState\":\"DONE\",\"text\":\"Fix the bug\"}");
        assertTrue(rendered.contains("todo-0001"), "callout includes the todo id");
        assertTrue(rendered.contains("Fix the bug"), "callout includes the todo text");
        assertTrue(rendered.contains(Printer.Colours.STRIKETHROUGH), "done todo is struck through");
        assertTrue(rendered.contains(Printer.Colours.GRAY), "done todo is dim grey");
    }

    @Test
    void todoUpdateIncompleteRendersWhiteWithoutStrikethrough() {
        final var rendered = render("task_tools_extension_todo_update",
                                    "{\"todoId\":\"todo-0002\",\"newState\":\"INCOMPLETE\",\"text\":\"Reopened item\"}");
        assertTrue(rendered.contains("todo-0002"), "callout includes the todo id");
        assertTrue(rendered.contains("Reopened item"), "callout includes the todo text");
        assertTrue(rendered.contains(Printer.Colours.WHITE), "reopened todo is white");
        final var afterId = rendered.substring(rendered.indexOf("todo-0002"));
        assertTrue(!afterId.contains(Printer.Colours.STRIKETHROUGH), "reopened todo is not struck through");
    }
}
