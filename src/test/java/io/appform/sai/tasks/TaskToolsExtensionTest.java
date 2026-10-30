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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.stream.Collectors;


/**
 * Tests for the tool surface of {@link TaskToolsExtension}: task lifecycle,
 * todo state machine enforcement, note round-trip, archive listing, and the
 * completion gate. Compaction paths are exercised only in their skipped or
 * no-session-extension modes (no agent required).
 */
class TaskToolsExtensionTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path root;

    @Test
    void countersResumeFromExistingJournal() {
        final var tools = extension();
        assertThat(tools.taskStart("first task").getTaskId()).isEqualTo("task-0001");
        final var firstTodo = tools.todoAdd("first todo");
        assertThat(firstTodo.getTodoId()).isEqualTo("todo-0001");

        // Complete the first task so a resumed extension can start a second one.
        tools.todoUpdate(firstTodo.getTodoId(), "done", null);
        tools.markTaskComplete();
        final var resumed = extension();
        final var start = resumed.taskStart("second task");
        assertThat(start.getTaskId()).isEqualTo("task-0002");
        assertThat(resumed.todoAdd("second todo").getTodoId()).isEqualTo("todo-0002");
    }

    @Test
    void deferredTodosAreListedAcrossTasks() {
        final var tools = extension();
        tools.taskStart("first");
        final var todo = tools.todoAdd("park me");
        tools.todoUpdate(todo.getTodoId(), "deferred", "later");
        tools.markTaskComplete();

        final var deferred = tools.deferredTodos();
        assertThat(deferred.getTodos()).hasSize(1);
        assertThat(deferred.getTodos().get(0).getId()).isEqualTo(todo.getTodoId());
    }

    @Test
    void factsIncludeActiveTaskAndTodos() {
        final var tools = extension();
        tools.taskStart("my task");
        final var todo = tools.todoAdd("work item");
        tools.noteWrite("the note");

        final var facts = tools.facts("request", null, null);
        final var factText = facts.stream()
                .flatMap(list -> list.getFact().stream())
                .collect(Collectors.toList());
        assertThat(factText).anySatisfy(fact -> {
            assertThat(fact.getName()).isEqualTo("active_task");
            assertThat(fact.getContent()).contains("my task");
        });
        assertThat(factText).anySatisfy(fact -> {
            assertThat(fact.getName()).isEqualTo(todo.getTodoId());
            assertThat(fact.getContent()).contains("work item");
        });
        assertThat(factText).anySatisfy(fact -> {
            assertThat(fact.getName()).isEqualTo("current_work_note");
            assertThat(fact.getContent()).isEqualTo("the note");
        });
    }

    @Test
    void factsWhenNoTaskShowsHint() {
        final var tools = extension();
        final var facts = tools.facts("request", null, null);
        assertThat(facts).hasSize(1);
        assertThat(facts.get(0).getFact()).hasSize(1);
    }

    @Test
    void markTaskCompleteBlocksOnIncompleteTodos() {
        final var tools = extension();
        tools.taskStart("blocked task");
        final var first = tools.todoAdd("must finish");
        tools.todoAdd("also must finish");
        tools.todoUpdate(first.getTodoId(), "done", null);

        final var response = tools.markTaskComplete();
        assertThat(response.getError()).isNotNull().contains("incomplete");
        assertThat(response.getIncomplete()).hasSize(1);
        assertThat(response.isCompactionTriggered()).isFalse();
        assertThat(tools.getStore().activeTask()).isPresent();
    }

    @Test
    void markTaskCompleteSucceedsAndArchivesNote() {
        final var tools = extension();
        tools.taskStart("clean task");
        final var todo = tools.todoAdd("only item");
        tools.noteWrite("# my note");
        tools.todoUpdate(todo.getTodoId(), "done", null);

        final var response = tools.markTaskComplete();
        assertThat(response.getError()).isNull();
        assertThat(response.getNoteFile()).isEqualTo("task-0001.md");
        assertThat(response.isCompactionTriggered()).isFalse();
        // Usage floor check: no session store bound, so usage reads 100, but the
        // session extension is null which disables compaction; the response says skipped.
        assertThat(response.getCompactionSkippedReason()).isNotNull();

        final var archived = tools.noteRead("task-0001");
        assertThat(archived.isArchived()).isTrue();
        assertThat(archived.getContent()).contains("my note");
        assertThat(tools.noteRead(null).getContent()).isEmpty();
        final var completedIds = tools.archivedNotes().getTodos()
                .stream().map(todoView -> todoView.getId()).collect(Collectors.toList());
        assertThat(completedIds).containsExactly("task-0001");
    }

    @Test
    void markTaskCompleteWithNoActiveTaskErrors() {
        final var tools = extension();
        final var response = tools.markTaskComplete();
        assertThat(response.getError()).isNotNull();
    }

    @Test
    void markTaskCompleteWithoutTodosSucceeds() {
        final var tools = extension();
        tools.taskStart("empty task");

        final var response = tools.markTaskComplete();
        assertThat(response.getError()).isNull();
        // task_start seeds an initial note, so a note file is archived even with no todos.
        assertThat(response.getNoteFile()).isEqualTo("task-0001.md");
    }

    @Test
    void nameIsTaskTools() {
        assertThat(extension().name()).isEqualTo("task-tools");
    }

    @Test
    void noteReadUnknownArchiveErrors() {
        final var tools = extension();
        final var response = tools.noteRead("task-9999");
        assertThat(response.getError()).isNotNull();
        assertThat(response.isArchived()).isTrue();
    }

    @Test
    void onlyOneActiveTaskAtATime() {
        final var tools = extension();
        tools.taskStart("first");
        final var response = tools.taskStart("second");
        assertThat(response.getError()).isNotNull().contains("already in progress");
        assertThat(response.getTaskId()).isNull();
    }

    @Test
    void sessionWithoutExtensionCompactsNothing() {
        final var tools = extension();
        tools.taskStart("task");
        final var todo = tools.todoAdd("item");
        tools.todoUpdate(todo.getTodoId(), "done", null);
        final var response = tools.markTaskComplete();
        // No session extension: compaction unavailable, state safely archived.
        assertThat(response.isCompactionTriggered()).isFalse();
        assertThat(response.getCompactionSkippedReason()).isNotNull();
    }

    @Test
    void taskStartCreatesInitialNote() {
        final var tools = extension();
        tools.taskStart("my task");
        assertThat(tools.noteRead(null).getContent()).contains("# Task: my task");
    }

    @Test
    void todoAddWithoutActiveTaskErrors() {
        final var tools = extension();
        final var response = tools.todoAdd("orphan");
        assertThat(response.getError()).isNotNull();
    }

    @Test
    void todoListFiltersByState() {
        final var tools = extension();
        tools.taskStart("task");
        final var first = tools.todoAdd("keep");
        final var second = tools.todoAdd("drop");
        tools.todoUpdate(first.getTodoId(), "done", null);
        tools.todoUpdate(second.getTodoId(), "abandoned", "not needed");

        final var done = tools.todoList("done");
        assertThat(done.isFiltered()).isTrue();
        assertThat(done.getTodos()).extracting(view -> view.getId()).containsExactly(first.getTodoId());
        final var abandoned = tools.todoList("abandoned");
        assertThat(abandoned.getTodos()).extracting(view -> view.getId()).containsExactly(second.getTodoId());
        assertThat(abandoned.getTodos().get(0).getReason()).isEqualTo("not needed");
    }

    @Test
    void todoListInvalidStatusErrors() {
        final var tools = extension();
        final var response = tools.todoList("banana");
        assertThat(response.getError()).isNotNull();
        assertThat(response.getTodos()).isEmpty();
    }

    @Test
    void todoListWithoutActiveTaskErrors() {
        final var tools = extension();
        final var response = tools.todoList(null);
        assertThat(response.getError()).isNotNull();
    }

    @Test
    void todoUpdateAcceptsStateAliases() {
        final var tools = extension();
        tools.taskStart("task");
        final var todo = tools.todoAdd("work item");

        // the alias that motivated this: "in_progress" must mean incomplete, not error
        assertThat(tools.todoUpdate(todo.getTodoId(), "in_progress", null).getError()).isNull();
        assertThat(tools.todoList("in_progress").getTodos()).extracting(view -> view.getId())
                .containsExactly(todo.getTodoId());
        assertThat(tools.todoUpdate(todo.getTodoId(), "pending", null).getError()).isNull();
        assertThat(tools.todoUpdate(todo.getTodoId(), "completed", null).getError()).isNull();
        assertThat(tools.todoUpdate(todo.getTodoId(), "reopened", null).getError()).isNull();
        // spaces and case are tolerated
        assertThat(tools.todoUpdate(todo.getTodoId(), " IN PROGRESS ", null).getError()).isNull();
        assertThat(tools.todoUpdate(todo.getTodoId(), "In-Progress", null).getError()).isNull();
    }

    @Test
    void todoUpdateAllowsRedoOfDoneTodo() {
        final var tools = extension();
        tools.taskStart("task");
        final var todo = tools.todoAdd("work item");
        tools.todoUpdate(todo.getTodoId(), "done", null);

        // work turns out to be unfinished: reopen and redo
        final var reopened = tools.todoUpdate(todo.getTodoId(), "incomplete", null);
        assertThat(reopened.getError()).isNull();
        assertThat(tools.todoList("incomplete").getTodos()).extracting(view -> view.getId())
                .containsExactly(todo.getTodoId());

        // same-state transition is a harmless no-op
        assertThat(tools.todoUpdate(todo.getTodoId(), "incomplete", null).getError()).isNull();

        // done again; deferred -> abandoned also allowed now (state machine is lenient)
        tools.todoUpdate(todo.getTodoId(), "deferred", "park it");
        final var abandoned = tools.todoUpdate(todo.getTodoId(), "abandoned", "changed my mind");
        assertThat(abandoned.getError()).isNull();
        assertThat(abandoned.getNewState()).isEqualTo("ABANDONED");
    }

    @Test
    void todoUpdateEnforcesBasicRules() {
        final var tools = extension();
        tools.taskStart("task");
        final var todo = tools.todoAdd("work item");

        // invalid target state
        assertThat(tools.todoUpdate(todo.getTodoId(), "banana", null).getError()).isNotNull();
        // abandon without reason
        assertThat(tools.todoUpdate(todo.getTodoId(), "abandoned", null).getError()).isNotNull();
        // done works
        assertThat(tools.todoUpdate(todo.getTodoId(), "done", null).getError()).isNull();
        // unknown todo
        assertThat(tools.todoUpdate("todo-9999", "done", null).getError()).isNotNull();
    }

    @Test
    void todoUpdateResurrectsDeferredTodo() {
        final var tools = extension();
        tools.taskStart("task");
        final var todo = tools.todoAdd("park me");
        tools.todoUpdate(todo.getTodoId(), "deferred", "later");
        tools.markTaskComplete();

        tools.taskStart("second task");
        final var response = tools.todoUpdate(todo.getTodoId(), "incomplete", null);
        assertThat(response.getError()).isNull();
        // resurrected todo belongs to the original task but shows in the active-task list via state filter
        final var incomplete = tools.todoList("incomplete");
        assertThat(incomplete.getTodos()).extracting(view -> view.getId()).containsExactly(todo.getTodoId());
    }

    private TaskToolsExtension extension() {
        return new TaskToolsExtension(root, mapper, "test-session", null, null, null);
    }
}
