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
package io.appform.sai.render;

import io.appform.sai.tasks.TaskIO.TodoItemView;
import io.appform.sai.term.Printer;

import java.util.List;

/**
 * Renders todo callouts and lists for the task tooling.
 *
 * <p>Resolved todos (done, abandoned, deferred) are rendered dim grey with
 * strikethrough; incomplete todos are rendered white so pending work stands
 * out. Callouts highlight state-changing events (a todo added or marked done,
 * a task completed) so the user can see progress at a glance without reading
 * the raw tool-call JSON.
 */
public final class TodoCalloutRenderer {

    private static final String LABEL_COLOUR = Printer.Colours.YELLOW;
    private static final String RESET = Printer.Colours.RESET;

    private TodoCalloutRenderer() {
        // Utility class
    }

    /**
     * Renders a single todo line. Resolved todos are dim grey with strikethrough;
     * incomplete todos are white.
     */
    public static String renderTodoLine(final TodoItemView todo) {
        if (isResolved(todo.getState())) {
            return Printer.Colours.GRAY + Printer.Colours.STRIKETHROUGH
                    + "\u25CB " + todo.getId() + "  " + todo.getText() + RESET;
        }
        return Printer.Colours.WHITE + "\u25CB " + todo.getId() + "  " + todo.getText() + RESET;
    }

    /**
     * Renders a multi-line todo list with a header line. Incomplete todos come
     * first (white, most relevant), then resolved ones (grey, struck through).
     */
    public static String renderTodoList(final String title, final List<TodoItemView> todos) {
        final var sb = new StringBuilder();
        sb.append(LABEL_COLOUR).append(title).append(RESET);
        if (todos == null || todos.isEmpty()) {
            sb.append("\n").append(Printer.Colours.GRAY).append("  (no todos)").append(RESET);
            return sb.toString();
        }
        todos.stream()
                .filter(todo -> !isResolved(todo.getState()))
                .forEach(todo -> sb.append("\n  ").append(renderTodoLine(todo)));
        todos.stream()
                .filter(todo -> isResolved(todo.getState()))
                .forEach(todo -> sb.append("\n  ").append(renderTodoLine(todo)));
        return sb.toString();
    }

    /**
     * Renders a callout for a todo that just transitioned state, prefixed with a
     * state-appropriate emoji marker.
     */
    public static String renderTodoStateChange(final TodoItemView todo) {
        final var marker = switch (todo.getState() == null ? "" : todo.getState()) {
            case "DONE" -> "\u2705";        // check mark button
            case "ABANDONED" -> "\u274C";    // cross mark
            case "DEFERRED" -> "\u23F3";    // hourglass
            case "INCOMPLETE" -> "\u21BB";  // reopened arrow
            default -> "\u25CB";            // fallback circle
        };
        return marker + " " + renderTodoLine(todo);
    }

    /**
     * True when the state string marks a resolved todo (done, abandoned or deferred).
     */
    private static boolean isResolved(final String state) {
        return "DONE".equals(state) || "ABANDONED".equals(state) || "DEFERRED".equals(state);
    }
}
