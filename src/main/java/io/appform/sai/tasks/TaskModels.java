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

import java.time.LocalDateTime;
import java.util.List;

import javax.annotation.Nullable;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Data models for the task-scoped compaction toolset.
 *
 * <p>A {@link Task} is the unit of work that gates compaction: it completes only
 * when none of its todos are in the {@code incomplete} state. Todos move through a
 * state machine ({@code incomplete -> done | abandoned | deferred}, with
 * {@code deferred -> incomplete} resurrection into a new task). The current work
 * note is a single markdown file that is archived (frozen) when the task completes.
 */
public final class TaskModels {

    /**
     * An edit attribution record: one file edit or write attributed to a todo.
     */
    @Value
    @Builder
    @Jacksonized
    public static class EditRecord {
        String file;
        String op;
        String ts;
    }

    /**
     * One append-only journal event. The journal is the single source of truth:
     * the current todo list and task index are derived by folding the events.
     */
    @Value
    @Builder
    @Jacksonized
    public static class JournalEvent {
        String ts;
        /**
         * Event type: created | transition | task_started | task_completed | compaction_triggered.
         */
        String event;
        @Nullable
        String todoId;
        @Nullable
        String taskId;
        @Nullable
        String text;
        @Nullable
        String from;
        @Nullable
        String to;
        @Nullable
        String reason;
        @Nullable
        String resolvedBy;
        @Nullable
        String noteFile;
        @Nullable
        String trigger;
        @Nullable
        Integer contextUsagePercent;
        @Nullable
        List<EditRecord> edits;
    }

    /**
     * A task. Only one task is active (in progress) at a time.
     */
    @Value
    @Builder(toBuilder = true)
    @Jacksonized
    public static class Task {
        String id;
        String title;
        TaskState state;
        LocalDateTime createdAt;
        @Nullable
        LocalDateTime completedAt;
        @Nullable
        String noteFile;
    }

    /**
     * Lifecycle states for a task.
     */
    public enum TaskState {
        /** Created but no todo items added yet. */
        PLANNED,
        /** Has todos and is under active work. */
        IN_PROGRESS,
        /** All todos resolved; note archived; frozen. */
        COMPLETED
    }

    /**
     * A todo item. The current state is derived by folding the journal.
     */
    @Value
    @Builder(toBuilder = true)
    @Jacksonized
    public static class TodoItem {
        String id;
        String taskId;
        String text;
        TodoState state;
        LocalDateTime createdAt;
        @Nullable
        LocalDateTime resolvedAt;
        @Nullable
        String resolvedBy;
        @Nullable
        String reason;
    }

    /**
     * Lifecycle states for a todo item.
     *
     * <p>{@code done}, {@code abandoned} and {@code deferred} all count as
     * "resolved" for the task-completion gate; only {@code incomplete} blocks
     * task completion.
     */
    public enum TodoState {
        /** Active work item; blocks task completion. */
        INCOMPLETE,
        /** Finished normally. Terminal. */
        DONE,
        /** Dropped with a reason. Terminal. */
        ABANDONED,
        /** Parked for a later task. Resurrectable. */
        DEFERRED
    }

    private TaskModels() {
        // Utility class: model holders only
    }
}
