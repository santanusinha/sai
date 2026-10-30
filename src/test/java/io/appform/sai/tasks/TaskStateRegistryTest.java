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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

/**
 * Tests for {@link TaskStateRegistry} validation of the {@code todoItemId}
 * parameter on file edits/writes, plus end-to-end validation through
 * {@code CoreToolBox} write/edit calls with a bound store.
 */
class TaskStateRegistryTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private TaskStateRegistry registry;
    private TaskStore store;

    @TempDir
    Path root;

    @TempDir
    Path workDir;

    @Test
    void blankTodoRefIsRejectedWhenStoreIsBound() {
        assertThat(registry.validateTodoRef(null)).isNotNull();
        assertThat(registry.validateTodoRef("")).isNotNull();
        assertThat(registry.validateTodoRef("   ")).isNotNull();
    }

    @Test
    void blankTodoRefPassesWhenNoStoreIsBound() {
        registry.bind(null);
        assertThat(registry.validateTodoRef(null)).isNull();
        assertThat(registry.validateTodoRef("  ")).isNull();
    }

    @Test
    void knownTodoRefPasses() {
        store.startTask("task-0001", "work");
        appendTodo("task-0001", "todo-0001", "edit files");
        assertThat(registry.validateTodoRef("todo-0001")).isNull();
        assertThat(registry.validateTodoRef("  todo-0001  ")).isNull();
    }

    @Test
    void recordEditIgnoresUnknownTodo() {
        store.startTask("task-0001", "work");
        // Should not throw.
        registry.recordEdit("todo-9999", "/tmp/SomeFile.java", "write");
        assertThat(store.journal()).noneMatch(event -> "edit".equals(event.getEvent()));
    }

    @Test
    void recordEditIsNoopWithoutStore() {
        registry.bind(null);
        registry.recordEdit("todo-0001", "/tmp/SomeFile.java", "write");
    }

    @Test
    void recordEditWritesAttributionToJournal() {
        store.startTask("task-0001", "work");
        appendTodo("task-0001", "todo-0001", "edit files");
        registry.recordEdit("todo-0001", "/tmp/SomeFile.java", "write");

        final var events = store.journal();
        assertThat(events).anySatisfy(event -> {
            assertThat(event.getEvent()).isEqualTo("edit");
            assertThat(event.getEdits().get(0).getOp()).isEqualTo("write");
        });
    }

    @Test
    void resolvedTodosStillValidate() {
        store.startTask("task-0001", "work");
        appendTodo("task-0001", "todo-0001", "edit files");
        store.transition("todo-0001", TaskModels.TodoState.DONE, null, "agent");
        // A done todo is still a real todo; edits attributed to it are audit records.
        assertThat(registry.validateTodoRef("todo-0001")).isNull();
    }

    @BeforeEach
    void setUp() {
        registry = TaskStateRegistry.getInstance();
        store = new TaskStore(root, mapper);
        registry.bind(store);
    }

    @Test
    void unknownTodoRefIsRejected() {
        store.startTask("task-0001", "work");
        appendTodo("task-0001", "todo-0001", "edit files");
        final var error = registry.validateTodoRef("todo-9999");
        assertThat(error).isNotNull().contains("Unknown todoItemId");
    }

    private void appendTodo(final String taskId, final String todoId, final String text) {
        store.append(TaskModels.JournalEvent.builder()
                .ts(java.time.LocalDateTime.now().toString())
                .event("created")
                .todoId(todoId)
                .taskId(taskId)
                .text(text)
                .build());
    }
}
