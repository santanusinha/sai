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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.appform.sai.tasks.TaskModels.Task;
import io.appform.sai.tasks.TaskModels.TodoItem;
import io.appform.sai.tasks.TaskModels.TodoState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Tests for {@link TaskStore}: journal folding, the todo state machine, the
 * completion gate, note archive and resurrection of deferred todos. Uses real
 * files in a temp directory.
 */
class TaskStoreTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private TaskStore store;

    @TempDir
    Path root;

    @Test
    void abandonmentWithReasonIsRecorded() {
        store.startTask("task-0001", "write tests");
        appendTodo("task-0001", "todo-0001", "add coverage");
        store.transition("todo-0001", TodoState.ABANDONED, "out of scope", "agent");
        final var todo = store.resolveTodo("todo-0001").orElseThrow();
        assertThat(todo.getState()).isEqualTo(TodoState.ABANDONED);
        assertThat(todo.getReason()).isEqualTo("out of scope");
        assertThat(todo.getResolvedAt()).isNotNull();
    }

    @Test
    void abandonmentWithoutReasonIsRejected() {
        store.startTask("task-0001", "write tests");
        appendTodo("task-0001", "todo-0001", "add coverage");
        assertThatThrownBy(() -> store.transition("todo-0001", TodoState.ABANDONED, null, "agent"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(store.resolveTodo("todo-0001").orElseThrow().getState()).isEqualTo(TodoState.INCOMPLETE);
    }

    @Test
    void compactionIsRecordedForAudit() {
        store.startTask("task-0001", "write tests");
        store.recordCompaction("task-0001", "task_complete", 42);

        final var events = store.journal();
        assertThat(events).anySatisfy(event -> {
            assertThat(event.getEvent()).isEqualTo("compaction_triggered");
            assertThat(event.getContextUsagePercent()).isEqualTo(42);
        });
    }

    @Test
    void completedTasksListOnlyCompletedTasks() {
        store.startTask("task-0001", "first");
        appendTodo("task-0001", "todo-0001", "only item");
        store.transition("todo-0001", TodoState.DONE, null, "agent");
        store.completeTask("task-0001", "task-0001.md");
        store.startTask("task-0002", "second");

        final var completed = store.completedTasks();
        assertThat(completed).extracting(Task::getId).containsExactly("task-0001");
    }

    @Test
    void deferredTodoCanBeResurrected() {
        store.startTask("task-0001", "first");
        appendTodo("task-0001", "todo-0001", "park me");
        store.transition("todo-0001", TodoState.DEFERRED, "later", "agent");
        assertThat(store.todosInState(TodoState.DEFERRED)).hasSize(1);

        // Resurrection: deferred -> incomplete
        store.transition("todo-0001", TodoState.INCOMPLETE, null, "agent");
        final var todo = store.resolveTodo("todo-0001").orElseThrow();
        assertThat(todo.getState()).isEqualTo(TodoState.INCOMPLETE);
        assertThat(todo.getResolvedAt()).isNull();
    }

    @Test
    void doneTodoCanBeReopenedOrMoved() {
        store.startTask("task-0001", "write tests");
        appendTodo("task-0001", "todo-0001", "add coverage");
        store.transition("todo-0001", TodoState.DONE, null, "agent");

        // Lenient transitions: done can move on (defer, reopen) so work can be redone.
        store.transition("todo-0001", TodoState.DEFERRED, "revisit later", "agent");
        assertThat(store.resolveTodo("todo-0001").orElseThrow().getState()).isEqualTo(TodoState.DEFERRED);

        store.transition("todo-0001", TodoState.INCOMPLETE, null, "agent");
        assertThat(store.resolveTodo("todo-0001").orElseThrow().getState()).isEqualTo(TodoState.INCOMPLETE);
        assertThat(store.resolveTodo("todo-0001").orElseThrow().getResolvedAt()).isNull();
    }

    @Test
    void editAttributionIsJournaled() {
        store.startTask("task-0001", "write tests");
        appendTodo("task-0001", "todo-0001", "add coverage");
        store.recordEdit("todo-0001", "task-0001", "/tmp/SomeFile.java", "edit");

        final var events = store.journal();
        assertThat(events).anySatisfy(event -> {
            assertThat(event.getEvent()).isEqualTo("edit");
            assertThat(event.getTodoId()).isEqualTo("todo-0001");
            assertThat(event.getEdits()).hasSize(1);
            assertThat(event.getEdits().get(0).getFile()).isEqualTo("/tmp/SomeFile.java");
        });
    }

    @Test
    void emptyNoteArchiveReturnsNull() {
        store.startTask("task-0001", "write tests");
        store.writeNote("   ");

        assertThat(store.archiveNote("task-0001")).isNull();
        assertThat(store.readArchivedNote("task-0001")).isEmpty();
    }

    @Test
    void malformedJournalLinesAreSkipped() throws Exception {
        store.startTask("task-0001", "write tests");
        appendTodo("task-0001", "todo-0001", "add coverage");
        final var journalFile = root.resolve("todo-journal.jsonl");
        final var lines = new java.util.ArrayList<>(Files.readAllLines(journalFile));
        lines.add("{not json");
        Files.write(journalFile, lines);
        // The valid events still fold correctly; the malformed line is ignored.
        assertThat(store.activeTask()).isPresent();
        assertThat(store.resolveTodo("todo-0001")).isPresent();
    }

    @Test
    void missingArchivedNoteIsEmpty() {
        assertThat(store.readArchivedNote("task-9999")).isEmpty();
    }

    @Test
    void newStoreHasNoActiveTask() {
        assertThat(store.activeTask()).isEmpty();
        assertThat(store.readNote()).isEmpty();
    }

    @Test
    void noteIsArchivedAndResetOnCompletion() {
        store.startTask("task-0001", "write tests");
        appendTodo("task-0001", "todo-0001", "add coverage");
        store.writeNote("# my note");

        final var archiveFile = store.archiveNote("task-0001");
        assertThat(archiveFile).isEqualTo("task-0001.md");
        assertThat(store.readNote()).isEmpty();
        assertThat(store.readArchivedNote("task-0001")).hasValue("# my note");
    }

    @Test
    void noteWriteOverwritesPreviousContent() {
        store.writeNote("first version");
        store.writeNote("second version");
        assertThat(store.readNote()).isEqualTo("second version");
    }

    @Test
    void sameStateTransitionIsANoOp() {
        store.startTask("task-0001", "write tests");
        appendTodo("task-0001", "todo-0001", "add coverage");
        store.transition("todo-0001", TodoState.DONE, null, "agent");
        final var eventsBefore = store.journal().size();

        store.transition("todo-0001", TodoState.DONE, null, "agent");
        assertThat(store.journal()).hasSize(eventsBefore);
        assertThat(store.resolveTodo("todo-0001").orElseThrow().getState()).isEqualTo(TodoState.DONE);
    }

    @BeforeEach
    void setUp() {
        store = new TaskStore(root, mapper);
    }

    @Test
    void taskCompletionRequiresResolvedTodos() {
        store.startTask("task-0001", "write tests");
        appendTodo("task-0001", "todo-0001", "add coverage");
        appendTodo("task-0001", "todo-0002", "update docs");

        assertThat(store.allTodosResolved("task-0001")).isFalse();

        store.transition("todo-0001", TodoState.DONE, null, "agent");
        assertThat(store.allTodosResolved("task-0001")).isFalse();

        store.transition("todo-0002", TodoState.DEFERRED, "docs blocked upstream", "agent");
        assertThat(store.allTodosResolved("task-0001")).isTrue();
    }

    @Test
    void taskMovesFromPlannedToInProgressOnFirstTodo() {
        store.startTask("task-0001", "write tests");
        assertThat(store.tasks().get("task-0001").getState()).isEqualTo(TaskModels.TaskState.PLANNED);

        appendTodo("task-0001", "todo-0001", "add coverage");
        assertThat(store.tasks().get("task-0001").getState()).isEqualTo(TaskModels.TaskState.IN_PROGRESS);
    }

    @Test
    void todosForTaskAreScoped() {
        store.startTask("task-0001", "first");
        appendTodo("task-0001", "todo-0001", "a");
        store.completeTask("task-0001", null);
        store.startTask("task-0002", "second");
        appendTodo("task-0002", "todo-0002", "b");

        assertThat(store.todosForTask("task-0001")).hasSize(1);
        assertThat(store.todosForTask("task-0002")).hasSize(1);
        assertThat(store.activeTaskTodos()).extracting(TodoItem::getId).containsExactly("todo-0002");
    }

    @Test
    void unknownTodoTransitionIsRejected() {
        store.startTask("task-0001", "write tests");
        appendTodo("task-0001", "todo-0001", "add coverage");
        assertThatThrownBy(() -> store.transition("todo-9999", TodoState.DONE, null, "agent"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void appendTodo(final String taskId, final String todoId, final String text) {
        store.append(TaskModels.JournalEvent.builder()
                .ts(LocalDateTime.now().toString())
                .event("created")
                .todoId(todoId)
                .taskId(taskId)
                .text(text)
                .build());
    }
}
