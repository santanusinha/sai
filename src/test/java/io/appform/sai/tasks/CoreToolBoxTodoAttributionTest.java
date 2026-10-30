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

import io.appform.sai.term.Printer;
import io.appform.sai.tools.CoreToolBox;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests that {@code CoreToolBox} file edits/writes accept an optional
 * {@code todoItemId} attribution parameter: blank passes unattributed,
 * unknown is rejected, and valid ids are recorded to the journal.
 */
class CoreToolBoxTodoAttributionTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private TaskStore store;
    private CoreToolBox toolBox;

    @TempDir
    Path root;

    @TempDir
    Path workDir;

    @Test
    void editFileWithoutTodoIdSucceedsUnattributed() throws Exception {
        final var file = workDir.resolve("out.txt");
        Files.writeString(file, "line1\nline2\n");
        final var expectedChecksum = io.appform.sai.files.FileIO.calculateChecksum(
                                                                                   Files.readAllBytes(file));

        final var response = toolBox.editFile(file.toString(),
                                              java.util.List.of(),
                                              "test",
                                              null,
                                              expectedChecksum);

        assertThat(response.getError()).isNull();
        assertThat(store.journal()).noneMatch(event -> "edit".equals(event.getEvent()));
    }

    @BeforeEach
    void setUp() {
        store = new TaskStore(root, mapper);
        TaskStateRegistry.getInstance().bind(store);
        toolBox = new CoreToolBox((Printer) null);
    }

    @AfterEach
    void tearDown() {
        // Leave the global registry unbound so other tests run in backwards-compat mode.
        TaskStateRegistry.getInstance().bind(null);
    }

    @Test
    void writeBlankTodoIdSucceedsUnattributed() throws Exception {
        final var file = workDir.resolve("out.txt");

        final var response = toolBox.writeFile(file.toString(), "content", "test", "   ", "");

        assertThat(response.getError()).isNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(Files.readString(file)).isEqualTo("content");
        assertThat(store.journal()).noneMatch(event -> "edit".equals(event.getEvent()));
    }

    @Test
    void writeFileWithoutTodoIdSucceedsUnattributed() throws Exception {
        final var file = workDir.resolve("out.txt");

        final var response = toolBox.writeFile(file.toString(), "content", "test", null, "");

        assertThat(response.getError()).isNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(Files.readString(file)).isEqualTo("content");
        assertThat(store.journal()).noneMatch(event -> "edit".equals(event.getEvent()));
    }

    @Test
    void writeWithUnknownTodoIdIsRejected() throws Exception {
        final var file = workDir.resolve("out.txt");

        final var response = toolBox.writeFile(file.toString(), "content", "test", "todo-9999", "");

        assertThat(response.getError()).isNotNull().contains("Unknown todoItemId");
        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void writeWithValidTodoIdSucceedsAndIsJournaled() throws Exception {
        startTaskWithTodo("todo-0001");
        final var file = workDir.resolve("out.txt");

        final var response = toolBox.writeFile(file.toString(), "content", "test", "todo-0001", "");

        assertThat(response.getError()).isNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(Files.readString(file)).isEqualTo("content");
        assertThat(store.journal()).anySatisfy(event -> {
            assertThat(event.getEvent()).isEqualTo("edit");
            assertThat(event.getTodoId()).isEqualTo("todo-0001");
            assertThat(event.getEdits().get(0).getFile()).isEqualTo(file.toString());
            assertThat(event.getEdits().get(0).getOp()).isEqualTo("write");
        });
    }

    private void startTaskWithTodo(final String todoId) {
        store.startTask("task-0001", "work");
        store.append(TaskModels.JournalEvent.builder()
                .ts(java.time.LocalDateTime.now().toString())
                .event("created")
                .todoId(todoId)
                .taskId("task-0001")
                .text("edit files")
                .build());
    }
}
