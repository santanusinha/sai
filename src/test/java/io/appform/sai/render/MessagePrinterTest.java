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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.AudioFormat;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.ImageDetail;
import com.phonepe.sentinelai.core.agentmessages.ReasoningContent;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelUsageStats;

import io.appform.sai.term.Printer;

import org.junit.jupiter.api.Test;


/**
 * Unit tests for {@link MessagePrinter}: media user-prompt rendering, compact
 * read_image rendering, reasoning rendering on session restore, and todo
 * callouts. Bulk data and raw tool JSON are never printed.
 */
class MessagePrinterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE64_100_BYTES = "A".repeat(136);

    private static String join(Iterable<Printer.Update> updates, String separator) {
        final var sb = new StringBuilder();
        for (final var update : updates) {
            if (update.getData() != null) {
                if (!sb.isEmpty()) {
                    sb.append(separator);
                }
                sb.append(update.getData());
            }
        }
        return sb.toString();
    }

    // ===== Media user-prompt rendering =====

    private static String mediaPromptOutput(UserPrompt userPrompt) {
        final var printer = new MessagePrinter(null, MAPPER, false);
        return join(printer.visit(userPrompt), "");
    }

    private static ReasoningContent reasoning(String text) {
        return ReasoningContent.builder()
                .content(text)
                .build();
    }

    private static String renderReadImageToolCall(String arguments) {
        final var printer = new MessagePrinter(null, MAPPER, false);
        final var toolCall = ToolCall.builder()
                .sessionId("session-1")
                .runId("run-1")
                .toolCallId("call-1")
                .toolName("core_tool_box_read_image")
                .arguments(arguments)
                .build();
        return join(printer.visit(toolCall), "");
    }

    private static String renderReadImageToolResponse(String jsonResponse) {
        final var printer = new MessagePrinter(null, MAPPER, false);
        final var toolResponse = ToolCallResponse.builder()
                .sessionId("session-1")
                .runId("run-1")
                .toolCallId("call-1")
                .toolName("core_tool_box_read_image")
                .errorType(ErrorType.SUCCESS)
                .response(jsonResponse)
                .build();
        return join(printer.visit(toolResponse), "");
    }

    private static String renderTodoCallout(String toolName, String jsonResponse) {
        final var printer = new MessagePrinter(null, MAPPER, false);
        final var toolResponse = ToolCallResponse.builder()
                .sessionId("session-1")
                .runId("run-1")
                .toolCallId("call-1")
                .toolName(toolName)
                .errorType(ErrorType.SUCCESS)
                .response(jsonResponse)
                .build();
        return join(printer.visit(toolResponse), "\n");
    }

    // ===== Compact read_image rendering =====

    @Test
    void audioPromptPrintsSizeSummaryNotBase64() {
        final var userPrompt = UserPrompt.audio("session-1",
                                                "run-1",
                                                BASE64_100_BYTES,
                                                AudioFormat.WAV,
                                                null);
        final var output = mediaPromptOutput(userPrompt);
        assertTrue(output.contains("audio data"), "summary should name the media kind");
        assertFalse(output.contains(BASE64_100_BYTES), "base64 payload must not be printed");
    }

    @Test
    void deferredTodosRendersCalloutList() {
        final var rendered = renderTodoCallout("task_tools_extension_deferred_todos",
                                               "{\"filtered\":true,\"todos\":["
                                                       + "{\"id\":\"todo-0005\",\"text\":\"Parked work\",\"state\":\"DEFERRED\"}]}");
        assertTrue(rendered.contains("Deferred todos"), "list has the deferred title");
        assertTrue(rendered.contains("todo-0005"), "deferred todo is listed");
        assertTrue(rendered.contains("Parked work"), "deferred todo text is listed");
    }

    @Test
    void imagePromptPrintsSizeSummaryNotBase64() {
        final var userPrompt = UserPrompt.imageData("session-1",
                                                    "run-1",
                                                    "data:image/png;base64," + BASE64_100_BYTES,
                                                    ImageDetail.AUTO,
                                                    null);
        final var output = mediaPromptOutput(userPrompt);
        assertTrue(output.contains("image data"), "summary should name the media kind");
        assertTrue(output.contains("102 B"), "summary should contain the decoded size");
        assertFalse(output.contains(BASE64_100_BYTES), "base64 payload must not be printed");
    }

    @Test
    void imageUrlPromptPrintsTheUrl() throws Exception {
        final var userPrompt = UserPrompt.imageURL("session-1",
                                                   "run-1",
                                                   java.net.URI.create("https://example.com/a.png").toURL(),
                                                   ImageDetail.AUTO,
                                                   null);
        final var output = mediaPromptOutput(userPrompt);
        assertTrue(output.contains("https://example.com/a.png"), "url prompt should print the url");
    }

    @Test
    void liveModeNeverRendersReasoning() {
        final var printer = new MessagePrinter(null, MAPPER, false);

        final var text = Text.builder()
                .sessionId("session-1")
                .runId("run-1")
                .content("The answer is 42.")
                .stats(new ModelUsageStats())
                .elapsedTimeMs(1000L)
                .reasoning(reasoning("I need to compute the answer."))
                .build();

        final var updates = printer.visit(text);
        assertEquals(0, updates.size(), "live mode renders nothing for responses");
    }

    // ===== Reasoning rendering (historical mode) =====

    @Test
    void noteReadErrorSurfacedAsSystemMessage() {
        final var rendered = renderTodoCallout("task_tools_extension_note_read",
                                               "{\"archived\":true,\"error\":\"No archived note for task: task-0009\"}");
        assertTrue(rendered.contains("No archived note for task"), "error is surfaced");
    }

    @Test
    void noteReadRendersArchivedNoteMarkdown() {
        final var rendered = renderTodoCallout("task_tools_extension_note_read",
                                               "{\"content\":\"# Task: Earlier work\\n\\nDone.\",\"archived\":true}");
        assertTrue(rendered.contains("Note"), "note header is shown");
        assertTrue(rendered.contains("Earlier work"), "archived note content is rendered");
    }

    @Test
    void noteWriteRendersNoteMarkdownInsteadOfRawJson() {
        final var rendered = renderTodoCallout("task_tools_extension_note_write",
                                               "{\"content\":\"# Task: Fix the bug\\n\\n## Where I am\\n\\nInvestigating.\",\"archived\":false}");
        assertTrue(rendered.contains("Note updated"), "note header is shown");
        assertTrue(rendered.contains("Task: Fix the bug"), "note content is rendered as markdown");
        assertTrue(rendered.contains("Where I am"), "note sections are rendered");
        assertTrue(!rendered.contains("\"content\":"), "raw tool JSON is not printed");
    }

    @Test
    void readImageToolCallPrintsOneLineSummaryWithFilePathAndDetail() {
        final var rendered = renderReadImageToolCall("{\"filePath\":\"/tmp/a.png\",\"imageDetail\":\"HIGH\"}");
        assertTrue(rendered.contains("/tmp/a.png"), "summary should contain the file path");
        assertTrue(rendered.contains("HIGH"), "summary should contain the detail level");
        assertTrue(rendered.contains("Read image"), "summary should name the action");
    }

    @Test
    void readImageToolResponseErrorIsSurfaced() {
        final var rendered = renderReadImageToolResponse("{\"attached\":false,\"filePath\":\"/tmp/a.png\","
                + "\"format\":\"png\",\"imageDetail\":\"AUTO\",\"bytes\":0,\"error\":\"file too large\"}");
        assertTrue(rendered.contains("Error reading image"), "error should be surfaced");
        assertTrue(rendered.contains("file too large"), "error text should be surfaced");
    }

    @Test
    void readImageToolResponsePrintsFilePathAndSizeNotPayload() {
        final var rendered = renderReadImageToolResponse("{\"attached\":true,\"filePath\":\"/tmp/a.png\","
                + "\"format\":\"png\",\"imageDetail\":\"AUTO\",\"bytes\":2048,\"error\":null}");
        assertTrue(rendered.contains("/tmp/a.png"), "summary should contain the file path");
        assertTrue(rendered.contains("2.0 KB"), "summary should contain the size");
        assertFalse(rendered.contains("2048,"), "raw response JSON should not be printed");
    }

    // ===== Todo callout rendering =====

    @Test
    void taskCompleteBlockedRendersIncompleteTodos() {
        final var rendered = renderTodoCallout("task_tools_extension_mark_task_complete",
                                               "{\"taskId\":\"task-0001\",\"incomplete\":[{\"id\":\"todo-0009\","
                                                       + "\"text\":\"Still pending\",\"state\":\"INCOMPLETE\"}]}");
        assertTrue(rendered.contains("cannot be completed"), "blocked completion is called out");
        assertTrue(rendered.contains("todo-0009"), "incomplete todo is listed");
        assertTrue(rendered.contains("Still pending"), "incomplete todo text is listed");
    }

    @Test
    void taskCompleteRendersTaskCompletedCallout() {
        final var rendered = renderTodoCallout("task_tools_extension_mark_task_complete",
                                               "{\"taskId\":\"task-0001\",\"compactionTriggered\":true}");
        assertTrue(rendered.contains("task-0001"), "callout includes the task id");
        assertTrue(rendered.contains("Task completed"), "callout announces the task completion");
        assertTrue(rendered.contains("Compaction triggered"), "callout mentions compaction");
    }

    @Test
    void taskStartRendersTaskStartedCallout() {
        final var rendered = renderTodoCallout("task_tools_extension_task_start",
                                               "{\"taskId\":\"task-0001\"}");
        assertTrue(rendered.contains("task-0001"), "callout includes the task id");
        assertTrue(rendered.contains("Task started"), "callout announces the task start");
    }

    @Test
    void textPromptStillPrintsContent() {
        final var content = "<?xml version='1.1' encoding='UTF-8'?><user_input><data>hello world</data></user_input>";
        final var userPrompt = UserPrompt.text("session-1", "run-1", content, null);
        final var output = mediaPromptOutput(userPrompt);
        assertTrue(output.contains("hello world"), "text prompt should print its content");
    }

    @Test
    void textResponseWithBlankReasoningRendersNoReasoningBlock() {
        final var printer = new MessagePrinter(null, MAPPER, true);

        final var text = Text.builder()
                .sessionId("session-1")
                .runId("run-1")
                .content("The answer is 42.")
                .stats(new ModelUsageStats())
                .elapsedTimeMs(1000L)
                .reasoning(reasoning(""))
                .build();

        final var joined = join(printer.visit(text), "\n");
        assertFalse(joined.contains("Thinking"), "blank reasoning renders no thinking markers");
        assertFalse(joined.contains("Reasoning"), "blank reasoning renders nothing");
    }

    @Test
    void textResponseWithReasoningRendersReasoningAfterContent() {
        final var printer = new MessagePrinter(null, MAPPER, true);

        final var text = Text.builder()
                .sessionId("session-1")
                .runId("run-1")
                .content("The answer is 42.")
                .stats(new ModelUsageStats())
                .elapsedTimeMs(1000L)
                .reasoning(reasoning("I need to compute the answer."))
                .build();

        final var joined = join(printer.visit(text), "\n");

        assertTrue(joined.contains("The answer is 42."), "content is rendered");
        assertTrue(joined.contains("I need to compute the answer."), "reasoning is rendered");
        assertTrue(joined.contains("Thinking"), "thinking marker is rendered");
        assertTrue(joined.contains("Thinking completed"), "thinking completed marker is rendered");

        final var contentIndex = joined.indexOf("The answer is 42.");
        final var reasoningIndex = joined.indexOf("I need to compute the answer.");
        assertTrue(contentIndex < reasoningIndex, "reasoning comes after the content");
        final var markerIndex = joined.indexOf("Thinking");
        final var completedIndex = joined.indexOf("Thinking completed");
        assertTrue(markerIndex < reasoningIndex, "thinking marker comes before the reasoning");
        assertTrue(reasoningIndex < completedIndex, "thinking completed marker comes after the reasoning");
    }

    @Test
    void textResponseWithoutReasoningRendersNoReasoningBlock() {
        final var printer = new MessagePrinter(null, MAPPER, true);

        final var text = Text.builder()
                .sessionId("session-1")
                .runId("run-1")
                .content("The answer is 42.")
                .stats(new ModelUsageStats())
                .elapsedTimeMs(1000L)
                .build();

        final var joined = join(printer.visit(text), "\n");

        assertFalse(joined.contains("Reasoning"), "no reasoning header without reasoning");
        assertTrue(joined.contains("The answer is 42."), "content is still rendered");
    }

    @Test
    void todoAddAllErrorSurfacedAsSystemMessage() {
        final var rendered = renderTodoCallout("task_tools_extension_todo_add_all",
                                               "{\"error\":\"No active task. Start one with task_start first.\"}");
        assertTrue(rendered.contains("No active task"), "error is surfaced");
    }

    @Test
    void todoAddAllRendersCalloutPerTodoInOrder() {
        final var rendered = renderTodoCallout("task_tools_extension_todo_add_all",
                                               "{\"todoIds\":[\"todo-0001\",\"todo-0002\"],\"todos\":["
                                                       + "{\"id\":\"todo-0001\",\"taskId\":\"task-0001\",\"text\":\"First\",\"state\":\"INCOMPLETE\"},"
                                                       + "{\"id\":\"todo-0002\",\"taskId\":\"task-0001\",\"text\":\"Second\",\"state\":\"INCOMPLETE\"}]}");
        assertTrue(rendered.contains("+ todo-0001"), "first todo is listed with a + marker");
        assertTrue(rendered.contains("+ todo-0002"), "second todo is listed with a + marker");
        assertTrue(rendered.indexOf("todo-0001") < rendered.indexOf("todo-0002"), "todos are listed in input order");
        assertTrue(rendered.contains("First"), "first todo text is listed");
        assertTrue(rendered.contains("Second"), "second todo text is listed");
        assertTrue(rendered.contains("(new todo)"), "lines are marked as new todos");
        assertTrue(!rendered.contains("\"todos\":"), "raw tool JSON is not printed");
    }

    @Test
    void todoAddRendersNewTodoCallout() {
        final var rendered = renderTodoCallout("task_tools_extension_todo_add",
                                               "{\"todoId\":\"todo-0003\",\"newState\":\"INCOMPLETE\",\"text\":\"New work\"}");
        assertTrue(rendered.contains("todo-0003"), "callout includes the new todo id");
        assertTrue(rendered.contains("New work"), "callout includes the new todo text");
        assertTrue(rendered.contains("(new todo)"), "callout is marked as a new todo");
    }

    @Test
    void todoListErrorSurfacedAsSystemMessage() {
        final var rendered = renderTodoCallout("task_tools_extension_todo_list",
                                               "{\"filtered\":true,\"todos\":[],\"error\":\"No active task.\"}");
        assertTrue(rendered.contains("No active task."), "error is surfaced");
    }

    @Test
    void todoListRendersCalloutListInsteadOfRawJson() {
        final var rendered = renderTodoCallout("task_tools_extension_todo_list",
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
        final var rendered = renderTodoCallout("task_tools_extension_todo_update",
                                               "{\"todoId\":\"todo-0001\",\"newState\":\"DONE\",\"text\":\"Fix the bug\"}");
        assertTrue(rendered.contains("todo-0001"), "callout includes the todo id");
        assertTrue(rendered.contains("Fix the bug"), "callout includes the todo text");
        assertTrue(rendered.contains(Printer.Colours.STRIKETHROUGH), "done todo is struck through");
        assertTrue(rendered.contains(Printer.Colours.GRAY), "done todo is dim grey");
    }

    @Test
    void todoUpdateIncompleteRendersWhiteWithoutStrikethrough() {
        final var rendered = renderTodoCallout("task_tools_extension_todo_update",
                                               "{\"todoId\":\"todo-0002\",\"newState\":\"INCOMPLETE\",\"text\":\"Reopened item\"}");
        assertTrue(rendered.contains("todo-0002"), "callout includes the todo id");
        assertTrue(rendered.contains("Reopened item"), "callout includes the todo text");
        assertTrue(rendered.contains(Printer.Colours.WHITE), "reopened todo is white");
        final var afterId = rendered.substring(rendered.indexOf("todo-0002"));
        assertTrue(!afterId.contains(Printer.Colours.STRIKETHROUGH), "reopened todo is not struck through");
    }

    @Test
    void toolCallWithReasoningRendersReasoningBeforeToolSummary() {
        final var printer = new MessagePrinter(null, MAPPER, true);

        final var toolCall = ToolCall.builder()
                .sessionId("session-1")
                .runId("run-1")
                .toolCallId("call-1")
                .toolName("core_tool_box_read_file")
                .arguments("{\"requestReason\":\"test\",\"filePath\":\"/tmp/x\",\"knownChecksum\":\"\"}")
                .reasoning(reasoning("I must read the file first."))
                .build();

        final var joined = join(printer.visit(toolCall), "\n");

        assertTrue(joined.contains("I must read the file first."), "tool-call reasoning is rendered");
        assertTrue(joined.contains("Read: "), "tool summary is rendered");
        assertTrue(joined.contains("/tmp/x"), "tool target file is rendered");
    }
}
