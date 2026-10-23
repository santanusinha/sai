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
package io.appform.sai.repl;


import io.appform.sai.render.MarkdownRenderer;
import io.appform.sai.term.Printer;

import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * Buffers streaming LLM output (delivered as {@code String} chunks) and
 * dispatches complete, render-ready segments to the {@link Printer}.
 *
 * <h3>Buffering strategy</h3>
 * <ul>
 * <li><b>NORMAL mode</b> – each fully-received line is rendered and printed
 * immediately via {@link MarkdownRenderer#toAnsi(String)}.</li>
 * <li><b>CODE mode</b> – lines are accumulated from the opening fence
 * ({@code ```} or {@code ~~~}) through the matching closing fence, then
 * the whole block is rendered as one unit so that
 * {@link MarkdownRenderer} sees a valid fenced-code-block.</li>
 * <li><b>TABLE mode</b> – lines that start <em>and</em> end with {@code |}
 * are accumulated together, then flushed as one unit when the first
 * non-table line is encountered (or on {@link #markDone()}), so that the
 * GFM table parser receives a complete table including the mandatory
 * header-separator row.</li>
 * </ul>
 *
 * <h3>Residual / carry-over</h3>
 * The incoming stream may be split mid-line across successive
 * {@link #accept(String)} calls. The last element produced by
 * {@code split("\\R", -1)} is always either an empty string (if the chunk
 * ended with a newline) or a partial line. This residual is always saved in
 * {@code buffer} regardless of mode, so that {@code buffer + content} at the
 * start of the next {@link #accept} call always reassembles the partial line
 * before {@link #detectMode} is invoked.
 */
@Slf4j
class BufferedOutputPrinter {

    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("^\\s*(?:\\d+[.)]|[-*+])(?:\\s|$)");

    enum Mode {
        /** Print each complete line immediately. */
        NORMAL,
        /** Accumulate from opening fence to closing fence, then render. */
        CODE,
        /** Accumulate all {@code |…|} rows, flush on first non-table line. */
        TABLE,
    }

    private final Printer printer;
    /** Carry-over: partial last line from the previous {@link #accept} call (all modes). */
    private String buffer = "";
    /** Accumulated content for the current CODE or TABLE block. */
    private String blockBuffer = "";
    private Mode currentMode = Mode.NORMAL;
    /**
     * Sentence segmentation for the NORMAL-mode residual. Replaces the previous
     * hand-rolled boundary scan: SRX rules (segment.srx) know about
     * abbreviations, decimals, URLs and list markers.
     */
    private final SrxSentenceSplitter splitter = new SrxSentenceSplitter();

    BufferedOutputPrinter(Printer printer) {
        this.printer = printer;
    }

    private static boolean hasOpenInlineCode(String line) {
        return line.chars().filter(c -> c == '`').count() % 2 != 0;
    }

    /**
     * Return {@code true} when {@code line} starts a list item, such as {@code "1. text"}, {@code "- text"},
     * {@code "* text"} or {@code "+ text"}. Splitting such a line at a sentence boundary breaks the list item
     * into separate output lines.
     */
    private static boolean isListItemLine(String line) {
        return LIST_ITEM_PATTERN.matcher(line).find();
    }

    public void accept(final String content) {
        if (content == null || content.isEmpty()) {
            return;
        }
        // Prepend any carry-over from the previous call.
        final var currentContent = buffer + content;
        // split("\\R", -1) keeps trailing empty strings so we always get
        // [complete lines…, residual] where residual is "" when the chunk
        // ended on a newline boundary.
        final var lines = currentContent.split("\\R", -1);
        // All elements except the last are complete lines (terminated by a
        // line-separator). The last element is the residual.
        final var completeLineCount = lines.length - 1;

        for (int i = 0; i < completeLineCount; i++) {
            final var line = lines[i];
            final var oldMode = currentMode;
            currentMode = detectMode(line);
            log.debug("Old Mode: {} Current Mode: {} Line: {}", oldMode, currentMode, line);

            if (currentMode == Mode.NORMAL) {
                if (oldMode == Mode.CODE) {
                    // Closing fence: it is part of the block so the renderer
                    // sees a valid fenced-code-block. blockBuffer already ends
                    // with '\n', so no extra separator is needed.
                    printBlock(blockBuffer + line);
                    blockBuffer = "";
                }
                else if (oldMode == Mode.TABLE) {
                    // The first non-table line terminates the table.
                    // It does NOT belong to the table, so print it separately.
                    printBlock(blockBuffer);
                    blockBuffer = "";
                    printLine(line);
                }
                else {
                    // Plain NORMAL line.
                    printLine(line);
                }
            }
            else {
                // Entering or continuing a block.
                if (oldMode == Mode.NORMAL) {
                    // First line of a new block — also clears the carry-over buffer
                    // because it was already folded into currentContent above.
                    blockBuffer = line + "\n";
                    buffer = "";
                }
                else if (oldMode != currentMode) {
                    // Block-to-block transition (e.g. TABLE → CODE): flush the
                    // old block, then start the new one.
                    printBlock(blockBuffer);
                    blockBuffer = line + "\n";
                }
                else {
                    // Continuing inside the same block type.
                    blockBuffer += line + "\n";
                }
            }
        }

        buffer = lines[lines.length - 1];

        // In NORMAL mode, flush any sentence-complete prefix from the residual
        // immediately so the user sees text as sentences arrive, not only when
        // a full newline-terminated line is received.
        if (currentMode == Mode.NORMAL) {
            flushSentencesFromBuffer();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Flush any buffered content that has not yet been printed. Should be
     * called exactly once after the LLM stream is exhausted.
     */
    public void markDone() {
        if (!blockBuffer.isEmpty()) {
            printBlock(blockBuffer);
            blockBuffer = "";
        }
        if (!buffer.isEmpty()) {
            printLine(buffer);
            buffer = "";
        }
    }


    /**
     * Classify {@code line} into the appropriate {@link Mode}.
     *
     * <p>Rules:
     * <ul>
     * <li>A line that both starts and ends with {@code |} is a TABLE line.
     * Trailing whitespace is trimmed before matching so that lines with
     * a trailing space do not escape detection.</li>
     * <li>A line that starts with {@code ```} or {@code ~~~} toggles CODE
     * mode on/off.</li>
     * <li>While already in CODE mode every other line stays CODE.</li>
     * <li>Everything else is NORMAL.</li>
     * </ul>
     */
    Mode detectMode(String line) {
        final var trimmed = line.stripTrailing();
        if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
            return Mode.TABLE;
        }
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            // Closing fence when already in CODE mode → back to NORMAL.
            return currentMode == Mode.CODE ? Mode.NORMAL : Mode.CODE;
        }
        // Any other line: stay CODE if already in a code block, otherwise NORMAL.
        return currentMode == Mode.CODE ? Mode.CODE : Mode.NORMAL;
    }

    /**
     * In NORMAL mode the residual {@code buffer} may already contain one or
     * more complete sentences. Ask the {@link SrxSentenceSplitter} which
     * prefix is safe to emit (every sentence whose boundary lies at least
     * {@link SrxSentenceSplitter#MARGIN} characters before the end of the
     * buffer) and print it. The remainder stays in {@code buffer} because
     * more text may still extend a boundary inside the margin (e.g.
     * {@code "Dr."} followed by {@code " Smith"} on the next chunk).
     */
    private void flushSentencesFromBuffer() {
        if (buffer.isEmpty() || isListItemLine(buffer) || hasOpenInlineCode(buffer)) {
            return;
        }
        final var ready = splitter.emittablePrefix(buffer, SrxSentenceSplitter.MARGIN);
        if (ready.isEmpty()) {
            return;
        }
        buffer = buffer.substring(ready.length());
        printLine(ready);
    }

    private void printBlock(String markdown) {
        printer.print(Printer.assistantMessage(MarkdownRenderer.toAnsi(markdown))
                .withImportant(true)
                .withRaw(true));
    }

    private void printLine(String line) {
        // MarkdownRenderer.toAnsi() appends '\n' via the Paragraph visitor so
        // that multi-paragraph documents flow correctly.  When individual lines
        // are streamed one-by-one that trailing newline causes JLine's
        // printAbove() to insert a spurious blank line after every normal line.
        // Strip the trailing newline before dispatching — JLine's printAbove()
        // already advances to the next line by itself.
        //
        // Blank lines from the LLM stream (e.g. the blank line between a heading
        // and a table, or between two paragraphs) must still be printed so that
        // the visual spacing in the terminal is preserved.  toAnsi("") returns
        // "", which printAbove renders as an empty line — exactly right.
        final var rendered = MarkdownRenderer.toAnsi(line);
        if (rendered == null) {
            return;
        }
        var stripped = rendered;
        if (!line.isBlank()) {
            while (stripped.endsWith("\n")) {
                stripped = stripped.substring(0, stripped.length() - 1);
            }
        }
        else if (stripped.endsWith("\n")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        printer.print(Printer.assistantMessage(stripped)
                .withImportant(true)
                .withRaw(true));
    }
}
