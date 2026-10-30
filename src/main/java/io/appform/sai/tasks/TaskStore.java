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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import io.appform.sai.tasks.TaskModels.EditRecord;
import io.appform.sai.tasks.TaskModels.JournalEvent;
import io.appform.sai.tasks.TaskModels.Task;
import io.appform.sai.tasks.TaskModels.TodoItem;
import io.appform.sai.tasks.TaskModels.TodoState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import lombok.extern.slf4j.Slf4j;

/**
 * Session-scoped, journal-backed store for tasks, todos and work notes.
 *
 * <p>All state lives in the session scratch directory:
 * <pre>
 * &lt;scratch&gt;/task-todo/
 * todo-journal.jsonl append-only journal; single source of truth
 * notes/current.md live work note (one per session)
 * notes/task-XXXX.md notes frozen at task completion
 * </pre>
 *
 * <p>The current todo list and task index are derived by folding the journal on
 * each read, so there is no separate mutable state that can drift. Writes are
 * serialized with a write lock; the journal is only ever appended to.
 */
@Slf4j
public class TaskStore {

    private static final String JOURNAL_FILE = "todo-journal.jsonl";
    private static final String NOTES_DIR = "notes";
    private static final String CURRENT_NOTE = "current.md";

    private final Path root;
    private final Path journalFile;
    private final Path notesDir;
    private final Path currentNoteFile;
    private final ObjectMapper mapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Creates the store rooted at the given directory, creating it if needed.
     *
     * @param root   the task-todo root directory for this session
     * @param mapper JSON mapper for journal serialization
     */
    public TaskStore(final Path root, final ObjectMapper mapper) {
        this.root = root;
        this.journalFile = root.resolve(JOURNAL_FILE);
        this.notesDir = root.resolve(NOTES_DIR);
        this.currentNoteFile = notesDir.resolve(CURRENT_NOTE);
        this.mapper = mapper;
        try {
            Files.createDirectories(notesDir);
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not create task store directories: " + root, e);
        }
    }

    /**
     * Appends an event to the journal.
     */
    void append(final JournalEvent event) {
        lock.writeLock().lock();
        try {
            final var line = mapper.writeValueAsString(event);
            Files.writeString(journalFile,
                              line + System.lineSeparator(),
                              StandardCharsets.UTF_8,
                              StandardOpenOption.CREATE,
                              StandardOpenOption.APPEND);
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not append to task journal: " + journalFile, e);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Reads the entire journal in order. Tolerates a missing journal (empty list).
     */
    List<JournalEvent> journal() {
        lock.readLock().lock();
        try {
            if (!Files.exists(journalFile)) {
                return List.of();
            }
            return Files.readAllLines(journalFile, StandardCharsets.UTF_8)
                    .stream()
                    .filter(line -> !Strings.isNullOrEmpty(line.trim()))
                    .map(line -> {
                        try {
                            return mapper.readValue(line, JournalEvent.class);
                        }
                        catch (IOException e) {
                            log.warn("Skipping malformed journal line: {}", line);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();
        }
        catch (IOException e) {
            log.error("Could not read task journal: {}", journalFile, e);
            return List.of();
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Folds the journal into the current todo list (all tasks, current state).
     */
    public Map<String, TodoItem> todos() {
        final var todos = new LinkedHashMap<String, TodoItem>();
        for (final var event : journal()) {
            switch (event.getEvent()) {
                case "created" -> todos.put(event.getTodoId(),
                                            TodoItem.builder()
                                                    .id(event.getTodoId())
                                                    .taskId(event.getTaskId())
                                                    .text(event.getText())
                                                    .state(TodoState.INCOMPLETE)
                                                    .createdAt(LocalDateTime.parse(event.getTs()))
                                                    .build());
                case "transition" -> {
                    final var todo = todos.get(event.getTodoId());
                    if (todo != null && event.getTo() != null) {
                        final var toState = TodoState.valueOf(event.getTo());
                        todos.put(event.getTodoId(),
                                  todo.toBuilder()
                                          .state(toState)
                                          .resolvedAt(isResolved(toState) ? LocalDateTime.parse(event.getTs()) : null)
                                          .resolvedBy(isResolved(toState) ? event.getResolvedBy() : null)
                                          .reason(event.getReason())
                                          .build());
                    }
                }
                default -> {
                    // task lifecycle events; handled by tasks()
                }
            }
        }
        return todos;
    }

    /**
     * Folds the journal into the task index.
     */
    public Map<String, Task> tasks() {
        final var tasks = new LinkedHashMap<String, Task>();
        for (final var event : journal()) {
            switch (event.getEvent()) {
                case "task_started" -> tasks.put(event.getTaskId(),
                                                 Task.builder()
                                                         .id(event.getTaskId())
                                                         .title(event.getText())
                                                         .state(TaskModels.TaskState.PLANNED)
                                                         .createdAt(LocalDateTime.parse(event.getTs()))
                                                         .build());
                case "created" -> {
                    final var task = tasks.get(event.getTaskId());
                    if (task != null && task.getState() == TaskModels.TaskState.PLANNED) {
                        tasks.put(event.getTaskId(),
                                  task.toBuilder()
                                          .state(TaskModels.TaskState.IN_PROGRESS)
                                          .build());
                    }
                }
                case "task_completed" -> {
                    final var task = tasks.get(event.getTaskId());
                    if (task != null) {
                        tasks.put(event.getTaskId(),
                                  task.toBuilder()
                                          .state(TaskModels.TaskState.COMPLETED)
                                          .completedAt(LocalDateTime.parse(event.getTs()))
                                          .noteFile(event.getNoteFile())
                                          .build());
                    }
                }
                default -> {
                    // todo events; handled by todos()
                }
            }
        }
        return tasks;
    }

    /**
     * Returns the single task that is not completed, if any.
     */
    public Optional<Task> activeTask() {
        return tasks().values()
                .stream()
                .filter(task -> task.getState() != TaskModels.TaskState.COMPLETED)
                .reduce((first, second) -> second); // latest if more than one (should not happen)
    }

    /**
     * Todos for the given task.
     */
    public List<TodoItem> todosForTask(final String taskId) {
        return todos().values()
                .stream()
                .filter(todo -> taskId.equals(todo.getTaskId()))
                .collect(Collectors.toList());
    }

    /**
     * Todos for the currently active task.
     */
    public List<TodoItem> activeTaskTodos() {
        return activeTask().map(task -> todosForTask(task.getId())).orElse(List.of());
    }

    /**
     * All todos across all tasks that are currently in the given state.
     */
    public List<TodoItem> todosInState(final TodoState state) {
        return todos().values()
                .stream()
                .filter(todo -> todo.getState() == state)
                .collect(Collectors.toList());
    }

    /**
     * The completion gate: true when the given task has no incomplete todos.
     */
    public boolean allTodosResolved(final String taskId) {
        return todosForTask(taskId)
                .stream()
                .noneMatch(todo -> todo.getState() == TodoState.INCOMPLETE);
    }

    /**
     * Validates a todo reference for edit attribution: the todo must exist in the
     * current state. It does not have to belong to the active task and does not
     * have to be incomplete; it just has to be real.
     *
     * @return the resolved todo, or empty if unknown
     */
    public Optional<TodoItem> resolveTodo(final String todoId) {
        return Optional.ofNullable(todos().get(todoId));
    }

    /**
     * Writes the live work note (overwrites freely).
     */
    public void writeNote(final String content) {
        lock.writeLock().lock();
        try {
            Files.writeString(currentNoteFile, content == null ? "" : content, StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not write current note: " + currentNoteFile, e);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Reads the live work note; empty string if none exists yet.
     */
    public String readNote() {
        lock.readLock().lock();
        try {
            if (!Files.exists(currentNoteFile)) {
                return "";
            }
            return Files.readString(currentNoteFile, StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            log.error("Could not read current note: {}", currentNoteFile, e);
            return "";
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Archives the current note for the given task: copies the note to
     * {@code notes/<taskId>.md} and resets the live note to empty.
     *
     * @return the archive file name, or null if there was nothing to archive
     */
    @Nullable
    public String archiveNote(final String taskId) {
        final var content = readNote();
        lock.writeLock().lock();
        try {
            final var archiveFile = taskId + ".md";
            if (!Strings.isNullOrEmpty(content.trim())) {
                Files.writeString(notesDir.resolve(archiveFile), content, StandardCharsets.UTF_8);
            }
            Files.writeString(currentNoteFile, "", StandardCharsets.UTF_8);
            return Strings.isNullOrEmpty(content.trim()) ? null : archiveFile;
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not archive note for task: " + taskId, e);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Reads an archived note for a completed task.
     */
    public Optional<String> readArchivedNote(final String taskId) {
        final var file = notesDir.resolve(taskId + ".md");
        lock.readLock().lock();
        try {
            if (!Files.exists(file)) {
                return Optional.empty();
            }
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        }
        catch (IOException e) {
            log.error("Could not read archived note: {}", file, e);
            return Optional.empty();
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Records a file edit attribution into the journal (audit trail).
     */
    public void recordEdit(final String todoId, final String taskId, final String file, final String op) {
        final var edits = List.of(EditRecord.builder()
                .file(file)
                .op(op)
                .ts(now())
                .build());
        append(JournalEvent.builder()
                .ts(now())
                .event("edit")
                .todoId(todoId)
                .taskId(taskId)
                .edits(edits)
                .build());
    }

    /**
     * Appends a state transition event with validation of the state machine.
     */
    public void transition(final String todoId,
                           final TodoState to,
                           @Nullable final String reason,
                           @Nullable final String resolvedBy) {
        final var todo = todos().get(todoId);
        Preconditions.checkArgument(todo != null, "Unknown todo id: %s", todoId);
        final var from = todo.getState();
        switch (to) {
            case DONE -> Preconditions.checkArgument(from == TodoState.INCOMPLETE,
                                                     "Only incomplete todos can be marked done (todo %s is %s)",
                                                     todoId,
                                                     from);
            case ABANDONED, DEFERRED -> {
                Preconditions.checkArgument(from == TodoState.INCOMPLETE,
                                            "Only incomplete todos can be %s (todo %s is %s)",
                                            to,
                                            todoId,
                                            from);
                Preconditions.checkArgument(!Strings.isNullOrEmpty(reason),
                                            "A reason is required to %s todo %s",
                                            to.toString().toLowerCase(),
                                            todoId);
            }
            case INCOMPLETE -> Preconditions.checkArgument(from == TodoState.DEFERRED,
                                                           "Only deferred todos can be resurrected (todo %s is %s)",
                                                           todoId,
                                                           from);
        }
        append(JournalEvent.builder()
                .ts(now())
                .event("transition")
                .todoId(todoId)
                .taskId(todo.getTaskId())
                .from(from.name())
                .to(to.name())
                .reason(reason)
                .resolvedBy(isResolved(to) ? Objects.requireNonNullElse(resolvedBy, "agent") : null)
                .build());
    }

    /**
     * Marks a task started in the journal.
     */
    public void startTask(final String taskId, final String title) {
        append(JournalEvent.builder()
                .ts(now())
                .event("task_started")
                .taskId(taskId)
                .text(title)
                .build());
    }

    /**
     * Marks a task completed in the journal with its archived note file.
     */
    public void completeTask(final String taskId, @Nullable final String noteFile) {
        append(JournalEvent.builder()
                .ts(now())
                .event("task_completed")
                .taskId(taskId)
                .noteFile(noteFile)
                .build());
    }

    /**
     * Records that compaction was triggered, for audit.
     */
    public void recordCompaction(final String taskId, final String trigger, @Nullable final Integer usagePercent) {
        append(JournalEvent.builder()
                .ts(now())
                .event("compaction_triggered")
                .taskId(taskId)
                .trigger(trigger)
                .contextUsagePercent(usagePercent)
                .build());
    }

    /**
     * Lists completed tasks (for archived-note reads).
     */
    public List<Task> completedTasks() {
        return tasks().values()
                .stream()
                .filter(task -> task.getState() == TaskModels.TaskState.COMPLETED)
                .collect(Collectors.toList());
    }

    private static boolean isResolved(final TodoState state) {
        return state != TodoState.INCOMPLETE;
    }

    private static String now() {
        return LocalDateTime.now().toString();
    }

    /**
     * The root directory of this store (exposed for tests).
     */
    Path rootDir() {
        return root;
    }

    /**
     * All todos as a list ordered by creation (exposed for tests and rendering).
     */
    public List<TodoItem> allTodos() {
        return new ArrayList<>(todos().values());
    }
}
