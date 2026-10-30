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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.Fact;
import com.phonepe.sentinelai.core.agent.FactList;
import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ProcessingMode;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.compaction.CompactionPrompts;
import com.phonepe.sentinelai.core.model.ModelAttributes;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.session.AgentSessionExtension;
import com.phonepe.sentinelai.session.BiScrollable;
import com.phonepe.sentinelai.session.QueryDirection;
import com.phonepe.sentinelai.session.SessionStore;

import io.appform.sai.SaiAgent;
import io.appform.sai.tasks.TaskIO.TodoItemView;
import io.appform.sai.tasks.TaskModels.JournalEvent;
import io.appform.sai.tasks.TaskModels.TodoItem;
import io.appform.sai.tasks.TaskModels.TodoState;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Agent extension exposing the task-scoped compaction toolset.
 *
 * <p>Tools: {@code task_start}, {@code todo_add}, {@code todo_list},
 * {@code todo_update}, {@code note_write}, {@code note_read},
 * {@code mark_task_complete}, {@code deferred_todos}, {@code archived_notes}.
 *
 * <p>The extension re-injects the current note and todo list as facts on every
 * run, so state survives compaction (and session resume) without living in the
 * message history.
 *
 * <p>{@code mark_task_complete} validates that no todos are incomplete, archives
 * the note, and records the task completed. (Forced compaction on completion is
 * currently disabled — see {@code markTaskComplete()} — auto compaction still
 * applies.)
 */
@Slf4j
public class TaskToolsExtension implements AgentExtension<String, String, SaiAgent> {

    /**
     * Compaction only fires on task completion when the context usage estimate is
     * at or above this percentage of the context window. Compacting an almost
     * empty context is a wasted LLM call.
     */
    public static final int DEFAULT_COMPACTION_USAGE_FLOOR_PERCENT = 30;

    @Getter
    private final TaskStore store;

    private final String sessionId;
    private final ObjectMapper mapper;
    private final AgentSessionExtension<String, String, SaiAgent> sessionExtension;
    private final SessionStore sessionStore;
    private final Supplier<SaiAgent> agentSupplier;
    private final int compactionUsageFloorPercent;
    private final AtomicInteger todoCounter = new AtomicInteger(0);
    private final AtomicInteger taskCounter = new AtomicInteger(0);

    /**
     * Builds the extension with the default compaction usage floor.
     *
     * @param storeRoot        directory for the task store (session scratch dir)
     * @param mapper           JSON mapper
     * @param sessionId        the session id compaction is forced on
     * @param sessionExtension session extension used to force compaction; may be null (compaction disabled)
     * @param sessionStore     session store used to estimate context usage; may be null (floor check disabled, compact)
     * @param agentSupplier    supplies the live agent (for token estimation); may be null
     */
    public TaskToolsExtension(final Path storeRoot,
                              final ObjectMapper mapper,
                              final String sessionId,
                              @Nullable final AgentSessionExtension<String, String, SaiAgent> sessionExtension,
                              @Nullable final SessionStore sessionStore,
                              @Nullable final Supplier<SaiAgent> agentSupplier) {
        this(storeRoot,
             mapper,
             sessionId,
             sessionExtension,
             sessionStore,
             agentSupplier,
             DEFAULT_COMPACTION_USAGE_FLOOR_PERCENT);
    }

    /**
     * Builds the extension with an explicit compaction usage floor.
     */
    public TaskToolsExtension(final Path storeRoot,
                              final ObjectMapper mapper,
                              final String sessionId,
                              @Nullable final AgentSessionExtension<String, String, SaiAgent> sessionExtension,
                              @Nullable final SessionStore sessionStore,
                              @Nullable final Supplier<SaiAgent> agentSupplier,
                              final int compactionUsageFloorPercent) {
        this.store = new TaskStore(storeRoot, mapper);
        this.sessionId = sessionId;
        this.mapper = mapper;
        this.sessionExtension = sessionExtension;
        this.sessionStore = sessionStore;
        this.agentSupplier = agentSupplier;
        this.compactionUsageFloorPercent = compactionUsageFloorPercent;
        resumeCounters();
    }

    /**
     * Parses a user- or model-supplied state name into a {@link TodoState}. Accepts the
     * canonical enum names case-insensitively plus common aliases (in_progress, pending,
     * reopened, completed, cancelled, ...), so a reasonable guess such as "in_progress"
     * works instead of failing with "Unknown state".
     *
     * @return the state, or null if the name is not recognized
     */
    @Nullable
    static TodoState parseTodoState(final String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        final var normalized = raw.trim().toLowerCase().replace(' ', '_').replace('-', '_');
        return switch (normalized) {
            case "incomplete", "in_progress", "pending", "todo", "open", "active", "reopened", "reopen", "not_done",
                    "not_done_yet" ->
                TodoState.INCOMPLETE;
            case "done", "complete", "completed", "complete_d", "finished", "resolved" -> TodoState.DONE;
            case "abandoned", "abandon", "cancel", "cancelled", "canceled", "dropped", "kill", "killed" ->
                TodoState.ABANDONED;
            case "deferred", "defer", "park", "parked", "blocked", "postponed", "postpone" -> TodoState.DEFERRED;
            default -> null;
        };
    }

    private static void bumpCounter(final AtomicInteger counter, final String id) {
        final var parts = id.split("-");
        if (parts.length != 2) {
            return;
        }
        try {
            counter.accumulateAndGet(Integer.parseInt(parts[1]), Math::max);
        }
        catch (NumberFormatException e) {
            // non-numeric id from an external writer; ignore
        }
    }

    @Override
    public ExtensionPromptSchema additionalSystemPrompts(final String request,
                                                         final AgentRunContext<String> context,
                                                         final SaiAgent agent,
                                                         final ProcessingMode processingMode) {
        return new ExtensionPromptSchema(List.of());
    }

    @Tool("List completed tasks and their archived note files (read a note with note_read + taskId).")
    public TaskIO.TodoListResponse archivedNotes() {
        return TaskIO.TodoListResponse.builder()
                .filtered(true)
                .todos(store.completedTasks()
                        .stream()
                        .map(task -> TodoItemView.builder()
                                .id(task.getId())
                                .taskId(task.getId())
                                .text(task.getTitle())
                                .state("COMPLETED")
                                .reason(task.getNoteFile())
                                .build())
                        .toList())
                .build();
    }

    // ------------------------------------------------------------------
    // Tools
    // ------------------------------------------------------------------

    @Tool("""
            List todos that were deferred from earlier tasks. These are candidates to pull into a
            new task via todo_update (deferred -> incomplete after starting a task that will own them).
            """)
    public TaskIO.TodoListResponse deferredTodos() {
        return TaskIO.TodoListResponse.builder()
                .filtered(true)
                .todos(store.todosInState(TodoState.DEFERRED).stream().map(this::view).toList())
                .build();
    }

    @Override
    public List<FactList> facts(final String request,
                                final AgentRunContext<String> context,
                                final SaiAgent agent) {
        final var facts = new ArrayList<Fact>();
        final var task = store.activeTask().orElse(null);
        if (task == null) {
            facts.add(new Fact("active_task",
                               "No task in progress. Start one with task_start when beginning multi-step work."));
        }
        else {
            facts.add(new Fact("active_task", "%s (%s)".formatted(task.getTitle(), task.getId())));
            store.todosForTask(task.getId())
                    .forEach(todo -> facts.add(new Fact(todo.getId(), renderTodo(todo))));
            final var note = store.readNote();
            if (!note.isBlank()) {
                facts.add(new Fact("current_work_note", note));
            }
        }
        final var deferred = store.todosInState(TodoState.DEFERRED);
        if (!deferred.isEmpty()) {
            facts.add(new Fact("deferred_todos",
                               deferred.stream().map(this::renderTodo).reduce((a, b) -> a + "; " + b).orElse("")));
        }
        return List.of(new FactList("Current task state (durable across compaction)", facts));
    }

    @Tool("""
            Mark the active task complete. Requires that no todo of the task is in the incomplete
            state (resolve them with todo_update: done, abandoned or deferred first). Archives the
            current work note and completes the task. Session compaction is handled separately by
            auto compaction.
            """)
    public TaskIO.TaskCompleteResponse markTaskComplete() {
        final var task = store.activeTask().orElse(null);
        if (task == null) {
            return TaskIO.TaskCompleteResponse.builder()
                    .error("No active task to complete.")
                    .build();
        }
        final var incomplete = store.todosForTask(task.getId())
                .stream()
                .filter(todo -> todo.getState() == TodoState.INCOMPLETE)
                .map(this::view)
                .toList();
        if (!incomplete.isEmpty()) {
            return TaskIO.TaskCompleteResponse.builder()
                    .taskId(task.getId())
                    .incomplete(incomplete)
                    .error("Task has incomplete todos. Resolve them (done/abandoned/deferred) before completing the task.")
                    .build();
        }
        final var noteFile = store.archiveNote(task.getId());
        store.completeTask(task.getId(), noteFile);

        // Forced compaction on task completion is disabled for now. With the
        // completed-todo tool-call pruning selector keeping the history lean
        // (see CompletedTodoPruningSelector), the trigger-happy forced compaction
        // is likely unnecessary; auto compaction still derisks context overflow.
        // Revisit once pruning behaviour has been observed in real sessions.
        // final var usagePercent = contextUsagePercent();
        // if (usagePercent < compactionUsageFloorPercent) {
        //     store.recordCompaction(task.getId(), "task_complete_skipped_below_floor", usagePercent);
        //     return TaskIO.TaskCompleteResponse.builder()
        //             .taskId(task.getId())
        //             .noteFile(noteFile)
        //             .compactionTriggered(false)
        //             .compactionSkippedReason("Context usage (%d%%) is below the %d%% floor; compaction skipped."
        //                     .formatted(usagePercent, compactionUsageFloorPercent))
        //             .build();
        // }
        // final var compacted = triggerCompaction(task.getId(), noteFile);
        // return TaskIO.TaskCompleteResponse.builder()
        //         .taskId(task.getId())
        //         .noteFile(noteFile)
        //         .compactionTriggered(compacted)
        //         .compactionSkippedReason(compacted
        //                 ? null
        //                 : "Compaction was requested but did not produce a summary (or is unavailable). State is safely archived.")
        //         .build();
        return TaskIO.TaskCompleteResponse.builder()
                .taskId(task.getId())
                .noteFile(noteFile)
                .compactionTriggered(false)
                .compactionSkippedReason("Forced compaction on task completion is currently disabled; "
                        + "auto compaction remains active.")
                .build();
    }

    @Override
    public String name() {
        return "task-tools";
    }

    @Tool("Read the current work note, or the archived note of a completed task when taskId is given.")
    public TaskIO.NoteResponse noteRead(
                                        @JsonPropertyDescription("Optional task id to read an archived note. Omit to read the current note.") String taskId) {
        if (taskId != null && !taskId.isBlank()) {
            return store.readArchivedNote(taskId.trim())
                    .map(content -> TaskIO.NoteResponse.builder().content(content).archived(true).build())
                    .orElseGet(() -> TaskIO.NoteResponse.builder()
                            .archived(true)
                            .error("No archived note for task: " + taskId)
                            .build());
        }
        return TaskIO.NoteResponse.builder().content(store.readNote()).build();
    }

    @Tool("""
            Write the current work note. The note is the durable record of ongoing work: what you
            are doing, decisions made, and where you are. It survives compaction and is injected
            into the compaction summary, so keep it current — update it whenever your in-flight
            state changes. The note is overwritten on each call, so send the full note, not a delta.
            """)
    public TaskIO.NoteResponse noteWrite(
                                         @JsonPropertyDescription("Full markdown content of the note.") String content) {
        store.writeNote(content);
        return TaskIO.NoteResponse.builder().content(content).build();
    }

    @Override
    public Optional<ModelOutputDefinition> outputSchema(final ProcessingMode processingMode) {
        return Optional.empty();
    }

    @Tool("""
            Start a new task. A task is a unit of work that owns a todo list and a work note.
            Only one task can be in progress at a time; complete the current task before starting
            another. After starting a task, add todo items with todo_add, then work through them.
            """)
    public TaskIO.TaskStartResponse taskStart(
                                              @JsonPropertyDescription("Short title of the task.") String title) {
        final var existing = store.activeTask();
        if (existing.isPresent()) {
            return TaskIO.TaskStartResponse.builder()
                    .error("A task is already in progress: %s (%s). Complete it with mark_task_complete first."
                            .formatted(existing.get().getTitle(), existing.get().getId()))
                    .build();
        }
        final var taskId = nextTaskId();
        store.startTask(taskId, title);
        store.writeNote("# Task: " + title + "\n\n## Where I am\n\n");
        return TaskIO.TaskStartResponse.builder().taskId(taskId).build();
    }

    @Tool("""
            Add a todo item to the active task. Returns the new todo id; use that id to attribute file edits via editFile/writeFile.
            """)
    public TaskIO.TodoUpdateResponse todoAdd(
                                             @JsonPropertyDescription("Description of the todo item.") String text) {
        final var task = store.activeTask().orElse(null);
        if (task == null) {
            return TaskIO.TodoUpdateResponse.builder()
                    .error("No active task. Start one with task_start first.")
                    .build();
        }
        final var todoId = nextTodoId();
        store.append(JournalEvent.builder()
                .ts(LocalDateTime.now().toString())
                .event("created")
                .todoId(todoId)
                .taskId(task.getId())
                .text(text)
                .build());
        return TaskIO.TodoUpdateResponse.builder()
                .todoId(todoId)
                .newState("INCOMPLETE")
                .text(text)
                .build();
    }

    // ------------------------------------------------------------------
    // AgentExtension
    // ------------------------------------------------------------------

    @Tool("""
            List todos. By default lists the todos of the active task. Set status to list todos
            in a specific state across all tasks (e.g. status=DEFERRED to see deferred work you
            can pull into a new task).
            """)
    public TaskIO.TodoListResponse todoList(
                                            @JsonPropertyDescription("Optional filter: INCOMPLETE, DONE, ABANDONED or DEFERRED. Omit to list the active task's todos.") String status) {
        if (status != null && !status.isBlank()) {
            final TodoState state = parseTodoState(status);
            if (state == null) {
                return TaskIO.TodoListResponse.builder()
                        .error("Unknown status: " + status
                                + ". Use INCOMPLETE, DONE, ABANDONED or DEFERRED.")
                        .filtered(true)
                        .todos(List.of())
                        .build();
            }
            return TaskIO.TodoListResponse.builder()
                    .filtered(true)
                    .todos(store.todosInState(state).stream().map(this::view).toList())
                    .build();
        }
        final var task = store.activeTask().orElse(null);
        final var todos = task == null
                ? List.<TodoItemView>of()
                : store.todosForTask(task.getId()).stream().map(this::view).toList();
        return TaskIO.TodoListResponse.builder()
                .taskId(task == null ? null : task.getId())
                .filtered(false)
                .todos(todos)
                .error(task == null ? "No active task. Start one with task_start." : null)
                .build();
    }

    @Tool("""
            Update the state of a todo item. Any todo can move to any state, including reopening
            (e.g. done -> incomplete to redo the work). A reason is required when abandoning or
            deferring; optional otherwise.
            """)
    public TaskIO.TodoUpdateResponse todoUpdate(
                                                @JsonPropertyDescription("The todo id, e.g. todo-0003.") String todoId,
                                                @JsonPropertyDescription("New state: done, abandoned, deferred or incomplete. Common aliases (in_progress, pending, reopened, completed, ...) are accepted.") String newState,
                                                @JsonPropertyDescription("Required reason when abandoning or deferring; optional otherwise.") String reason) {
        final TodoState to = parseTodoState(newState);
        if (to == null) {
            return TaskIO.TodoUpdateResponse.builder()
                    .todoId(todoId)
                    .error("Unknown state: " + newState
                            + ". Use done, abandoned, deferred or incomplete (aliases such as in_progress are also accepted).")
                    .build();
        }
        try {
            store.transition(todoId, to, reason, "agent");
        }
        catch (Exception e) {
            return TaskIO.TodoUpdateResponse.builder()
                    .todoId(todoId)
                    .error(e.getMessage())
                    .build();
        }
        final var updated = store.resolveTodo(todoId).orElse(null);
        return TaskIO.TodoUpdateResponse.builder()
                .todoId(todoId)
                .newState(to.name())
                .text(updated == null ? null : updated.getText())
                .build();
    }

    /**
     * Estimates current context usage as a percentage of the model context window
     * by token-counting the session history. Returns 100 (compact) when
     * estimation is not possible: skipping compaction is always safe — state is
     * on disk — while compacting early is merely a wasted call, so bias to compact.
     */
    private int contextUsagePercent() {
        final var agent = agentSupplier == null ? null : agentSupplier.get();
        if (agent == null || sessionStore == null) {
            return 100;
        }
        try {
            final var setup = agent.getSetup();
            final var contextWindow = AgentUtils.getIfNotNull(setup.getModelSettings(),
                                                              ModelSettings::getModelAttributes,
                                                              ModelAttributes.DEFAULT_MODEL_ATTRIBUTES)
                    .getContextWindowSize();
            if (contextWindow <= 0) {
                return 100;
            }
            final BiScrollable<AgentMessage> page = sessionStore.readMessages(sessionId,
                                                                              Integer.MAX_VALUE,
                                                                              true,
                                                                              null,
                                                                              QueryDirection.OLDER);
            final var messages = page.getItems();
            if (messages.isEmpty()) {
                return 0;
            }
            final var tokens = setup.getModel().estimateTokenCount(messages, setup);
            if (tokens < 0) { // TOKEN_COUNT_UNKNOWN
                return 100;
            }
            return (int) Math.min(100, (tokens * 100L) / contextWindow);
        }
        catch (Exception e) {
            log.warn("Context usage estimation failed: {}", e.getMessage());
            return 100;
        }
    }

    private String nextTaskId() {
        return "task-%04d".formatted(taskCounter.incrementAndGet());
    }

    private String nextTodoId() {
        return "todo-%04d".formatted(todoCounter.incrementAndGet());
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private String renderTodo(final TodoItem todo) {
        final var base = "%s [%s] %s".formatted(todo.getId(), todo.getState(), todo.getText());
        return todo.getReason() == null ? base : base + " (reason: " + todo.getReason() + ")";
    }

    private void resumeCounters() {
        store.tasks().keySet().forEach(id -> bumpCounter(taskCounter, id));
        store.todos().keySet().forEach(id -> bumpCounter(todoCounter, id));
    }

    /**
     * Forces compaction seeded with the archived note and final todo states. The
     * compaction run uses a derived {@link AgentSetup} whose summarization user
     * prompt appends the archived state, so the summarizer works from the
     * curated record instead of reconstructing it from raw history.
     */
    private boolean triggerCompaction(final String taskId, @Nullable final String noteFile) {
        if (sessionExtension == null) {
            return false;
        }
        try {
            final var agent = agentSupplier == null ? null : agentSupplier.get();
            final var seededNote = Optional.ofNullable(noteFile)
                    .flatMap(store::readArchivedNote)
                    .orElseGet(store::readNote);
            final var todos = store.todosForTask(taskId);
            final var state = new StringBuilder()
                    .append("Archived work note for the just-completed task ")
                    .append(taskId)
                    .append(":\n")
                    .append(seededNote)
                    .append("\n\nFinal todo states for the task:\n");
            todos.forEach(todo -> state.append(renderTodo(todo)).append('\n'));
            final var agentSetup = agent == null
                    ? null
                    : agent.getSetup()
                            .withAutoCompactionSetup(agent.getSetup()
                                    .getAutoCompactionSetup()
                                    .withPrompts(new CompactionPrompts(
                                                                       CompactionPrompts.DEFAULT_SUMMARIZATION_SYSTEM_PROMPT,
                                                                       CompactionPrompts.DEFAULT_SUMMARIZATION_USER_PROMPT
                                                                               + "\n\nAdditional durable context for the summary "
                                                                               + "(use it as the authoritative record of ongoing work):\n"
                                                                               + state,
                                                                       CompactionPrompts.DEFAULT_PROMPT_SCHEMA)));
            final var result = agentSetup == null
                    ? sessionExtension.forceCompaction(sessionId).join()
                    : sessionExtension.forceCompaction(sessionId, agentSetup).join();
            store.recordCompaction(taskId, "task_complete", contextUsagePercent());
            return result.isPresent();
        }
        catch (Exception e) {
            log.error("Compaction after task completion failed: {}", e.getMessage(), e);
            store.recordCompaction(taskId, "task_complete_failed", null);
            return false;
        }
    }

    private TodoItemView view(final TodoItem todo) {
        return TodoItemView.builder()
                .id(todo.getId())
                .taskId(todo.getTaskId())
                .text(todo.getText())
                .state(todo.getState().name())
                .reason(todo.getReason())
                .build();
    }
}
