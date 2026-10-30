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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Response types for the task tools. Kept as simple flat records with
 * descriptions so the tool schemas are self-documenting for the model.
 */
public final class TaskIO {

    @Value
    @Builder
    @Jacksonized
    public static class NoteResponse {
        @JsonPropertyDescription("The note content, or the current note when no taskId was given.")
        String content;
        @JsonPropertyDescription("True when this is an archived note (read-only).")
        boolean archived;
        @JsonPropertyDescription("Error message if any.")
        String error;
    }

    @Value
    @Builder
    @Jacksonized
    public static class TaskCompleteResponse {
        @JsonPropertyDescription("The id of the completed task.")
        String taskId;
        @JsonPropertyDescription("Todos that are still incomplete and blocked completion. Empty on success.")
        List<TodoItemView> incomplete;
        @JsonPropertyDescription("The archived note file for the task, if any.")
        String noteFile;
        @JsonPropertyDescription("Whether compaction was triggered for the session.")
        boolean compactionTriggered;
        @JsonPropertyDescription("Reason compaction was skipped, if it was.")
        String compactionSkippedReason;
        @JsonPropertyDescription("Error message if the task could not be completed.")
        String error;
    }

    @Value
    @Builder
    @Jacksonized
    public static class TaskStartResponse {
        @JsonPropertyDescription("The id of the started task.")
        String taskId;
        @JsonPropertyDescription("Error message if the task could not be started (a task is already in progress).")
        String error;
    }

    @Value
    @Builder
    @Jacksonized
    public static class TodoItemView {
        @JsonPropertyDescription("Todo id, e.g. todo-0003. Use this id to attribute file edits.")
        String id;
        @JsonPropertyDescription("The task this todo belongs to.")
        String taskId;
        @JsonPropertyDescription("Todo text.")
        String text;
        @JsonPropertyDescription("State: INCOMPLETE, DONE, ABANDONED or DEFERRED.")
        String state;
        @JsonPropertyDescription("Reason for abandoning or deferring, if any.")
        String reason;
    }

    @Value
    @Builder
    @Jacksonized
    public static class TodoListResponse {
        @JsonPropertyDescription("The task these todos belong to, if a task is active.")
        String taskId;
        @JsonPropertyDescription("True when the request was filtered by status.")
        boolean filtered;
        @JsonPropertyDescription("The todo items.")
        List<TodoItemView> todos;
        @JsonPropertyDescription("Error message if any.")
        String error;
    }

    @Value
    @Builder
    @Jacksonized
    public static class TodoUpdateResponse {
        @JsonPropertyDescription("The id of the updated todo.")
        String todoId;
        @JsonPropertyDescription("The new state.")
        String newState;
        @JsonPropertyDescription("The todo text, for display.")
        String text;
        @JsonPropertyDescription("Error message if the transition was invalid.")
        String error;
    }

    private TaskIO() {
        // Utility class
    }
}
