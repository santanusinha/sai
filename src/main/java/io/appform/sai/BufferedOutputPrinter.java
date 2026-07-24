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
package io.appform.sai;


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

    BufferedOutputPrinter(Printer printer) {
        this.printer = printer;
    }

    /**
     * Return {@code true} when the {@code '.'} at {@code dotIndex} is the
     * terminator of an ordered-list marker rather than a sentence end, i.e.
     * the whole preceding content of {@code buffer} (ignoring leading
     * whitespace used for indentation/nesting) consists only of digits, like
     * {@code "1."}, {@code "42."}, or {@code "   3."}. In those cases the dot
     * belongs to the list bullet and must not trigger a sentence flush.
     */
    private static boolean isOrderedListMarker(String buffer, int dotIndex) {
        int start = 0;
        while (start < dotIndex && Character.isWhitespace(buffer.charAt(start))) {
            start++;
        }
        // Must have at least one digit and nothing but digits before the dot.
        if (start == dotIndex) {
            return false;
        }
        for (int i = start; i < dotIndex; i++) {
            if (!Character.isDigit(buffer.charAt(i))) {
                return false;
            }
        }
        return true;
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

        // Handle the residual (last element after split).
        // The residual is always stored in buffer, regardless of the current mode.
        // This ensures that buffer+content at the top of the next accept() call
        // always reconstructs the full partial line before detectMode() sees it.
        // (Storing block-mode residuals in blockBuffer would cause the continuation
        // fragment to be processed as a standalone new line, breaking multi-token
        // table rows that lack a leading '|' in mid-chunk fragments.)
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
     * more complete sentences (i.e. text ending with {@code .}, {@code !}, or
     * {@code ?} followed by a space or the end of the buffer). Flush those
     * complete sentences immediately so the user sees output as each sentence
     * arrives rather than waiting for the entire line's newline.
     *
     * <p>The remainder (any text after the last sentence boundary) is kept in
     * {@code buffer} for reassembly with the next chunk.
     */
    private void flushSentencesFromBuffer() {
        if (buffer.isEmpty()) {
            return;
        }
        // Find the rightmost sentence boundary: [.!?] followed by a space, or
        // [.!?] at the very end of the buffer (stream may end mid-sentence).
        int flushUpTo = -1;
        for (int i = 0; i < buffer.length(); i++) {
            final char c = buffer.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                // Accept boundary if followed by a space or at end of buffer.
                if (i + 1 == buffer.length() || buffer.charAt(i + 1) == ' ') {
                    // A '.' that merely terminates an ordered-list marker
                    // (e.g. "1." or "  2."), where everything before it is
                    // digits only, is a list bullet — not a sentence end.
                    // Splitting here would flush the marker away from its
                    // list item, so leave it in the buffer.
                    if (c == '.' && isOrderedListMarker(buffer, i)) {
                        continue;
                    }
                    flushUpTo = i + 1; // exclusive: include the punctuation
                }
            }
        }
        if (flushUpTo <= 0) {
            return;
        }
        final String toFlush = buffer.substring(0, flushUpTo);
        buffer = buffer.substring(flushUpTo);
        printLine(toFlush);
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
        final var stripped = rendered.endsWith("\n")
                ? rendered.substring(0, rendered.length() - 1)
                : rendered;
        printer.print(Printer.assistantMessage(stripped)
                .withImportant(true)
                .withRaw(true));
    }
}
