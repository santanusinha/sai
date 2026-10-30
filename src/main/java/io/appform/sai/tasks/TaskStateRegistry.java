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

import java.util.Optional;

import javax.annotation.Nullable;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Shared registry of task-tool state for the current session.
 *
 * <p>Holds the {@link TaskStore} so {@code CoreToolBox} can validate the
 * {@code todoItemId} parameter on file edits/writes.
 *
 * <p>This is the seam between the toolbox (created before the agent) and the
 * extension (registered with the agent): the toolbox validates against the
 * store; the extension owns the tools.
 */
@Slf4j
public class TaskStateRegistry {

    @Getter
    private static final TaskStateRegistry instance = new TaskStateRegistry();

    @Nullable
    private TaskStore store;

    private TaskStateRegistry() {
        // Singleton
    }

    /**
     * Binds a store for this session. Called once at startup.
     */
    public synchronized void bind(final TaskStore store) {
        this.store = store;
    }

    /**
     * Records an attributed edit to the journal (audit trail).
     */
    public synchronized void recordEdit(final String todoId, final String file, final String op) {
        if (store == null || todoId == null || todoId.isBlank()) {
            // No store bound or no attribution requested: nothing to journal.
            return;
        }
        final var todo = store.resolveTodo(todoId.trim()).orElse(null);
        if (todo == null) {
            return;
        }
        try {
            store.recordEdit(todo.getId(), todo.getTaskId(), file, op);
        }
        catch (Exception e) {
            log.warn("Could not record edit attribution for todo {}: {}", todoId, e.getMessage());
        }
    }

    /**
     * Resolves the store, or empty when task tooling is not active.
     */
    public synchronized Optional<TaskStore> store() {
        return Optional.ofNullable(store);
    }

    /**
     * Validates a todo reference for file edit/write attribution.
     *
     * <p>A blank id is allowed: the edit proceeds unattributed. This keeps
     * exploratory edits possible when no todo applies.
     *
     * @return null when valid or blank, or an error message when the id is unknown
     */
    public synchronized String validateTodoRef(final String todoId) {
        if (store == null || todoId == null || todoId.isBlank()) {
            // Task tooling not bound or no attribution requested: edits pass unattributed.
            return null;
        }
        return store.resolveTodo(todoId.trim()).isPresent()
                ? null
                : "Unknown todoItemId: " + todoId
                        + ". List todos with todo_list and pass a real id.";
    }
}
