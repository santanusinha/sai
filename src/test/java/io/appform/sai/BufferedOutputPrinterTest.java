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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import lombok.SneakyThrows;

/**
 * Unit tests for {@link BufferedOutputPrinter}.
 *
 * <p>Uses a {@code CapturingPrinter} that records every {@link Printer.Update}
 * without requiring a real terminal, then inspects the rendered (ANSI-stripped)
 * text for correctness.
 */
class BufferedOutputPrinterTest {

    // -------------------------------------------------------------------------
    // Test infrastructure
    // -------------------------------------------------------------------------

    private static class CapturingPrinter extends Printer {

        private final List<Printer.Update> captured = new CopyOnWriteArrayList<>();

        @SneakyThrows
        CapturingPrinter() {
            super(Settings.builder().headless(true).build(),
                  Executors.newSingleThreadExecutor(),
                  null,
                  null);
        }

        @Override
        public void print(List<Printer.Update> updates) {
            captured.addAll(updates);
        }

        List<Printer.Update> getCaptured() {
            return captured;
        }
    }

    private CapturingPrinter printer;
    private BufferedOutputPrinter bop;

    @Test
    void blankLineBetweenContentIsPreserved() {
        // A blank separator line in the LLM stream must be passed through as an
        // empty print call so that JLine's printAbove("") renders a visible
        // blank line in the terminal, preserving the LLM's intended spacing.
        feed("line one\n");
        feed("\n");
        feed("line two\n");
        assertEquals(3,
                     printCount(),
                     "blank separator line must produce a print call (empty string for visual gap)");
        final var parts = renderedParts();
        assertTrue(parts.get(0).contains("line one"), "first part");
        assertEquals("", parts.get(1), "middle part must be the blank line (empty string)");
        assertTrue(parts.get(2).contains("line two"), "third part");
    }

    @Test
    void carryOverIsNotDuplicatedOnContinuation() {
        feed("buf");
        feed("fer\nline2\n");
        final var parts = renderedParts();
        assertEquals(2, parts.size());
        assertTrue(parts.get(0).contains("buffer"), "first part should be 'buffer'");
        assertTrue(parts.get(1).contains("line2"), "second part should be 'line2'");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @Test
    void codeBlockChunkedAtFenceBoundary() {
        // Chunk boundary falls exactly on the opening fence line
        feed("text before\n");
        feed("```\n");
        feed("code body\n");
        feed("```\n");
        feed("text after\n");
        final var parts = renderedParts();
        assertEquals(3, parts.size());
        assertTrue(parts.get(0).contains("text before"));
        assertTrue(parts.get(1).contains("code body"));
        assertTrue(parts.get(2).contains("text after"));
    }

    @Test
    void codeBlockContentIsRendered() {
        feed("```java\nint x = 1;\n```\n");
        final var out = renderedOutput();
        assertTrue(out.contains("int x = 1;"), "code literal must appear in output");
    }

    @Test
    void codeBlockDoesNotContainSpuriousBlankLineBeforeClosingFence() {
        // blockBuffer already ends with '\n'; adding another '\n' before the
        // closing fence would create a blank line inside the rendered block.
        feed("```\ncode line\n```\n");
        final var raw = printer.getCaptured().get(0).getData();
        assertFalse(raw.contains("\n\n```"),
                    "no double-newline should appear before the closing fence");
    }

    @Test
    void codeBlockIsBufferedAndFlushedAsOneUnit() {
        feed("```java\nint x = 1;\n```\n");
        assertEquals(1, printCount(), "entire code block should be one print call");
    }

    // -------------------------------------------------------------------------
    // Mode detection unit tests
    // -------------------------------------------------------------------------

    @Test
    void codeBlockLanguageTagIsRendered() {
        feed("```java\nSystem.out.println(\"hi\");\n```\n");
        final var out = renderedOutput();
        assertTrue(out.contains("[java]"), "language tag should appear");
        assertTrue(out.contains("System.out.println"), "code literal must be present");
    }

    @Test
    void codeBlockSpanningMultipleChunksIsBufferedCorrectly() {
        feed("```\nline1\n");
        assertEquals(0, printCount(), "block not yet closed — should not print");
        feed("line2\n```\n");
        assertEquals(1, printCount(), "block closed — one print call");
        final var out = renderedOutput();
        assertTrue(out.contains("line1"));
        assertTrue(out.contains("line2"));
    }

    @Test
    void codeBlockWithoutLanguageRendersCorrectly() {
        feed("```\nplain code\n```\n");
        assertTrue(renderedOutput().contains("plain code"));
    }

    @Test
    void detectModeReturnsCodeForTildeFence() {
        assertEquals(BufferedOutputPrinter.Mode.CODE, bop.detectMode("~~~"));
    }

    @Test
    void detectModeReturnsCodeForTripleBacktick() {
        assertEquals(BufferedOutputPrinter.Mode.CODE, bop.detectMode("```java"));
    }

    @Test
    void detectModeReturnsNormalForPlainLine() {
        assertEquals(BufferedOutputPrinter.Mode.NORMAL, bop.detectMode("Hello world"));
    }

    // -------------------------------------------------------------------------
    // NORMAL mode — basic line handling
    // -------------------------------------------------------------------------

    @Test
    void detectModeReturnsTableForPipeDelimitedLine() {
        assertEquals(BufferedOutputPrinter.Mode.TABLE, bop.detectMode("| A | B |"));
    }

    @Test
    void detectModeReturnsTableForSeparatorRow() {
        assertEquals(BufferedOutputPrinter.Mode.TABLE, bop.detectMode("|---|---|"));
    }

    @Test
    void detectModeReturnsTableWhenLineHasTrailingSpace() {
        // Trailing whitespace must be stripped before matching
        assertEquals(BufferedOutputPrinter.Mode.TABLE, bop.detectMode("| A | B |  "));
    }

    @Test
    void emptyChunkIsIgnored() {
        bop.accept(null);
        bop.accept("");
        assertEquals(0, printCount(), "empty/null chunks should produce no output");
    }

    @Test
    void indentedOrderedListMarkerDotIsNotTreatedAsSentenceBoundary() {
        feed("   2. Nested item");
        assertEquals(0,
                     printCount(),
                     "an indented '2.' marker must not be flushed as a sentence");
    }

    // -------------------------------------------------------------------------
    // CODE block rendering
    // -------------------------------------------------------------------------

    @Test
    void markDoneFlushesResidualNormalLine() {
        feed("no newline at end");
        assertEquals(0, printCount());
        bop.markDone();
        assertEquals(1, printCount());
        assertTrue(renderedOutput().contains("no newline at end"));
    }

    @Test
    void markDoneFlushesTruncatedCodeBlock() {
        // Stream ends mid-block (e.g. connection cut before closing fence)
        feed("```\ncode without closing fence\n");
        assertEquals(0, printCount());
        bop.markDone();
        assertEquals(1, printCount());
        assertTrue(renderedOutput().contains("code without closing fence"));
    }

    @Test
    void markDoneIsIdempotentWhenBufferIsEmpty() {
        feed("line\n");
        bop.markDone();
        bop.markDone();
        assertEquals(1, printCount(), "markDone on empty state should not produce extra prints");
    }

    @Test
    void multiDigitOrderedListMarkerDotIsNotTreatedAsSentenceBoundary() {
        feed("42. Item forty two");
        assertEquals(0,
                     printCount(),
                     "a multi-digit '42.' marker must not be flushed as a sentence");
    }

    @Test
    void multipleNormalLinesThenCodeBlock() {
        feed("intro line\n");
        feed("```python\nprint('hello')\n```\n");
        feed("outro line\n");
        final var parts = renderedParts();
        // "intro line", code block, "outro line"
        assertEquals(3, parts.size());
        assertTrue(parts.get(0).contains("intro line"));
        assertTrue(parts.get(1).contains("print"));
        assertTrue(parts.get(2).contains("outro line"));
    }

    @Test
    void multipleNormalLinesThenTable() {
        feed("header text\n");
        feed("| X | Y |\n|---|---|\n| a | b |\n");
        feed("footer text\n");
        final var parts = renderedParts();
        // "header text", table, "footer text"
        assertEquals(3, parts.size());
        assertTrue(parts.get(0).contains("header text"));
        assertTrue(parts.get(1).contains("X") && parts.get(1).contains("Y"));
        assertTrue(parts.get(2).contains("footer text"));
    }

    @Test
    void nonTableLineAfterTableIsNotIncludedInTableOutput() {
        feed("| A | B |\n| - | - |\n| 1 | 2 |\nplain line\n");
        final var parts = renderedParts();
        assertEquals(2, parts.size());
        // Second part is the plain line — should contain no pipe characters
        assertFalse(parts.get(1).contains("|"),
                    "post-table plain line must not contain table content: " + parts.get(1));
        assertTrue(parts.get(1).contains("plain line"),
                   "plain line text should be preserved");
    }

    @Test
    void normalLineAfterCodeBlockIsPrintedSeparately() {
        feed("```\ncode\n```\nafter code\n");
        assertEquals(2, printCount(), "code block + 'after code' = 2 print calls");
        assertTrue(renderedOutput().contains("after code"));
    }

    @Test
    void normalLineDoesNotProduceTrailingBlankLine() {
        // MarkdownRenderer.toAnsi() appends '\n' via the Paragraph visitor.
        // printLine() must strip it so that JLine's printAbove() does not
        // insert a spurious blank line after every normal line.
        feed("Hello world\n");
        assertEquals(1, printCount());
        final var data = printer.getCaptured().get(0).getData();
        assertFalse(data.endsWith("\n"),
                    "printLine result must not end with newline, was: [" + data + "]");
    }

    @Test
    void normalLineRenderedTextDoesNotContainDoubleNewline() {
        // Regression guard: ensure no '\n\n' appears in any single printLine
        // result (which would imply a spurious blank line was embedded).
        feed("First line\nSecond line\n");
        for (final var update : printer.getCaptured()) {
            assertFalse(update.getData().contains("\n\n"),
                        "printLine output must not contain double-newline: [" + update.getData() + "]");
        }
    }

    // -------------------------------------------------------------------------
    // TABLE block rendering
    // -------------------------------------------------------------------------

    @Test
    void normalLinesArePrintedImmediatelyOneByOne() {
        feed("hello\nworld\n");
        assertEquals(2, printCount(), "each complete line becomes one print call");
    }

    @Test
    void orderedListMarkerDotIsNotTreatedAsSentenceBoundary() {
        // Streaming "1. First item" without a trailing newline must NOT flush
        // the "1." bullet marker on its own — doing so would separate the
        // list number from its item text and break the numbered-list render.
        feed("1. First item");
        assertEquals(0,
                     printCount(),
                     "the '1.' ordered-list marker must not be flushed as a sentence");
        bop.markDone();
        final var out = renderedOutput();
        assertTrue(out.contains("First item"), "list item text should survive");
    }

    @Test
    void partialLineIsNotPrintedUntilNewlineArrives() {
        feed("hel");
        assertEquals(0, printCount());
        feed("lo\n");
        assertEquals(1, printCount());
        assertTrue(renderedOutput().contains("hello"));
    }

    @Test
    void realSentenceBoundaryStillFlushesImmediately() {
        // Guard: ordinary prose that ends in a period followed by a space must
        // still flush eagerly (the list-marker guard must not suppress it).
        feed("This is a sentence. ");
        assertEquals(1, printCount(), "a normal sentence should flush on its boundary");
        assertTrue(renderedOutput().contains("This is a sentence."));
    }

    @Test
    void sentenceAfterOrderedListMarkerStillFlushes() {
        // The marker dot is skipped, but a genuine sentence end later in the
        // same buffer must still flush (up to and including that period).
        feed("1. First item. ");
        assertEquals(1,
                     printCount(),
                     "the real sentence boundary after the list item should flush");
        final var parts = renderedParts();
        assertTrue(parts.get(0).contains("1."),
                   "flushed text must keep the list marker with its item: " + parts.get(0));
        assertTrue(parts.get(0).contains("First item"), "item text should be present");
    }

    @BeforeEach
    void setUp() {
        printer = new CapturingPrinter();
        bop = new BufferedOutputPrinter(printer);
    }

    @Test
    void singleLineWithNoNewlineIsFlushedByMarkDone() {
        feed("a single line");
        assertEquals(0, printCount());
        bop.markDone();
        assertEquals(1, printCount());
        assertTrue(renderedOutput().contains("a single line"));
    }

    @Test
    void staleCarryOverIsNotPrependedAfterCodeBlock() {
        // Partial normal text, then code block, then another normal line.
        feed("lead");
        feed("ing\n```\ncode\n```\nnext\n");
        final var parts = renderedParts();
        // "leading", code block, "next"
        assertEquals(3, parts.size());
        assertTrue(parts.get(0).contains("leading"),
                   "first print should contain 'leading', was: " + parts.get(0));
        assertTrue(parts.get(2).contains("next"),
                   "third print should contain 'next', was: " + parts.get(2));
    }

    @Test
    void staleCarryOverIsNotPrependedAfterTableTransition() {
        // Partial normal line, then a table, then normal text
        feed("lead");
        feed("ing\n| A |\n| - |\n| 1 |\nnormal\n");
        final var parts = renderedParts();
        // "leading", table, "normal"
        assertEquals(3, parts.size());
        assertTrue(parts.get(0).contains("leading"),
                   "first print should be 'leading': " + parts.get(0));
        assertTrue(parts.get(2).contains("normal"),
                   "third print should be 'normal': " + parts.get(2));
    }

    @Test
    void tableAfterHeadingWithBlankLineSeparatorRendersCorrectly() {
        // heading (1) + blank line (1) + table (1) = 3 print calls.
        // The blank line between the heading and the table is preserved so the
        // terminal shows a visual gap, matching what the LLM intended.
        feed("## Tables\n");
        feed("\n");
        feed("| Language | Year |\n");
        feed("|----------|------|\n");
        feed("| Java     | 1995 |\n");
        bop.markDone();
        final var parts = renderedParts();
        // heading (1) + blank separator (1) + table (1) = 3
        assertEquals(3,
                     parts.size(),
                     "heading + blank + table = 3 print calls, got: " + parts);
        assertTrue(parts.get(0).contains("Tables"), "first part should be the heading");
        assertEquals("", parts.get(1), "second part must be the blank separator line");
        assertTrue(parts.get(2).contains("Language"), "third part should contain table header");
        assertTrue(parts.get(2).contains("Java"), "table should contain body cell Java");
    }

    @Test
    void tableBlockIsFlushedOnMarkDoneWhenNoTrailingNormalLine() {
        feed("| A | B |\n| - | - |\n| 1 | 2 |\n");
        // Table is still open (no non-table line to close it), markDone flushes it
        assertEquals(0, printCount(), "table not yet terminated by a normal line");
        bop.markDone();
        assertEquals(1, printCount(), "markDone should flush the table as one unit");
    }

    // -------------------------------------------------------------------------
    // Mixed / streaming scenarios
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Issue fixes: trailing newline, blank-line skip, consecutive code blocks,
    // table after heading
    // -------------------------------------------------------------------------

    @Test
    void tableBlockIsFlushedWhenNonTableLineArrives() {
        feed("| A | B |\n| - | - |\n| 1 | 2 |\nsome text after\n");
        // table (1) + "some text after" (1) = 2
        assertEquals(2, printCount());
    }


    @Test
    void tableChunkedMidRowCarriesOverCorrectly() {
        // Simulate chunk splitting mid-row (no newline in chunk)
        feed("| Col");
        feed(" A | Col B |\n");
        feed("|-----|-------|\n");
        feed("| v1  | v2    |\n");
        bop.markDone();
        final var out = renderedOutput();
        assertTrue(out.contains("Col A"), "reconstructed header cell");
        assertTrue(out.contains("Col B"), "reconstructed header cell");
        assertTrue(out.contains("v1") && out.contains("v2"), "body cells");
    }

    @Test
    void tableFollowedByCodeBlockFlushesTableFirst() {
        feed("| A |\n| - |\n| 1 |\n```\ncode\n```\n");
        // table (1) + code block (1) = 2
        assertEquals(2, printCount());
        final var parts = renderedParts();
        // First part should contain table box-drawing chars
        assertTrue(parts.get(0).contains("│") || parts.get(0).contains("┌"),
                   "first print should be the table");
        // Second part should contain the code literal
        assertTrue(parts.get(1).contains("code"),
                   "second print should be the code block");
    }

    @Test
    void tableHeaderAndBodyCellsAreRendered() {
        feed("| Language | Stars |\n|----------|-------|\n| Java     | 9999  |\n| Kotlin   | 8888  |\n");
        bop.markDone();
        final var out = renderedOutput();
        assertTrue(out.contains("Language"), "header cell 'Language'");
        assertTrue(out.contains("Stars"), "header cell 'Stars'");
        assertTrue(out.contains("Java"), "body cell 'Java'");
        assertTrue(out.contains("Kotlin"), "body cell 'Kotlin'");
        assertTrue(out.contains("9999"), "body cell '9999'");
        assertTrue(out.contains("8888"), "body cell '8888'");
    }

    @Test
    void tableHeaderSeparatorIsRendered() {
        feed("| Name | Age |\n|------|-----|\n| Bob  | 25  |\n");
        bop.markDone();
        final var out = renderedOutput();
        assertTrue(out.contains("├"), "header/body separator left junction");
        assertTrue(out.contains("┤"), "header/body separator right junction");
    }

    @Test
    void tableRendersBoxDrawingBorders() {
        feed("| Name | Age |\n|------|-----|\n| Alice | 30 |\n");
        bop.markDone();
        final var out = renderedOutput();
        assertTrue(out.contains("┌"), "top-left corner");
        assertTrue(out.contains("┐"), "top-right corner");
        assertTrue(out.contains("└"), "bottom-left corner");
        assertTrue(out.contains("┘"), "bottom-right corner");
        assertTrue(out.contains("│"), "vertical separators");
        assertTrue(out.contains("─"), "horizontal lines");
    }

    @Test
    void tableRowSplitMidLineAtChunkBoundaryRendersCorrectly() {
        // Regression test for block-mode residual bug:
        // When a chunk boundary falls inside a table row, the partial line must
        // be carried over in buffer (not blockBuffer) so that the next chunk's
        // continuation is concatenated with it before detectMode() runs.
        //
        // Without the fix, the separator row '|------|------|' delivered as
        // split tokens like '|---', '---', '-|---', '---', '---|' would leave
        // a residual fragment (e.g. '------|') in blockBuffer.  The next chunk
        // would then present '------|\n' as a standalone line; because it lacks
        // a leading '|', detectMode() returns NORMAL — prematurely flushing an
        // incomplete table (header-only) and producing raw pipe text instead of
        // box-drawing characters.
        //
        // Feed the separator row in fine-grained tokens, each without a newline,
        // then terminate the row and add a body row.
        feed("| Name | Score |\n");        // complete header row
        feed("|---");                       // chunk 1: partial separator row
        feed("---|---");                    // chunk 2: continuation
        feed("---|\n");                    // chunk 3: end of separator row
        feed("| Alice | 100 |\n");         // body row (complete)
        bop.markDone();

        assertEquals(1,
                     printCount(),
                     "entire table including split separator row must be one print call");
        final var out = renderedOutput();
        // A correctly-rendered table has box-drawing borders
        assertTrue(out.contains("┌"), "top-left corner must be present");
        assertTrue(out.contains("│"), "vertical separator must be present");
        assertTrue(out.contains("├"), "header/body separator junction must be present");
        assertTrue(out.contains("Name"), "header cell Name must be present");
        assertTrue(out.contains("Score"), "header cell Score must be present");
        assertTrue(out.contains("Alice"), "body cell Alice must be present");
        assertTrue(out.contains("100"), "body cell 100 must be present");
    }

    @Test
    void tableSpanningMultipleChunksIsAccumulatedCorrectly() {
        feed("| A | B |\n");
        feed("| - | - |\n");
        feed("| 1 | 2 |\n");
        assertEquals(0, printCount(), "table not closed yet");
        bop.markDone();
        assertEquals(1, printCount(), "complete table flushed on markDone");
        final var out = renderedOutput();
        assertTrue(out.contains("A") && out.contains("1"));
    }

    @Test
    void tableWithTrailingSpaceOnRowsIsDetectedCorrectly() {
        // Some LLMs emit table rows with trailing spaces
        feed("| A | B |  \n| - | - |  \n| 1 | 2 |  \n");
        bop.markDone();
        assertEquals(1, printCount(), "table with trailing spaces should still be one unit");
        final var out = renderedOutput();
        assertTrue(out.contains("A") && out.contains("B"), "header cells should be present");
        assertTrue(out.contains("1") && out.contains("2"), "body cells should be present");
    }

    void tearDown() throws IOException {
        printer.close();
    }

    @Test
    void tildeFenceCodeBlockIsHandled() {
        feed("~~~python\nprint('hi')\n~~~\n");
        assertEquals(1, printCount());
        assertTrue(renderedOutput().contains("print('hi')"));
    }

    @Test
    void twoConsecutiveCodeBlocksWithBlankLineBetweenRenderCorrectly() {
        // State-machine trace:
        //   ``` → CODE; body → CODE; ``` (close) → NORMAL+printBlock;
        //   "" (blank line) → NORMAL+printLine("") → empty print for visual gap;
        //   ``` → CODE (new block); body → CODE; ``` (close) → NORMAL+printBlock
        feed("```js\nfn1();\n```\n");
        feed("\n");
        feed("```python\nfn2()\n```\n");
        // block1 (1) + blank separator (1) + block2 (1) = 3
        assertEquals(3,
                     printCount(),
                     "two code blocks with blank separator → 3 print calls");
        final var parts = renderedParts();
        assertTrue(parts.get(0).contains("fn1()"), "first block must contain fn1()");
        assertEquals("", parts.get(1), "middle part must be the blank separator line");
        assertTrue(parts.get(2).contains("fn2()"), "second block must contain fn2()");
    }

    private void feed(String text) {
        bop.accept(text);
    }

    private int printCount() {
        return printer.getCaptured().size();
    }

    /** Strip ANSI escapes and return the combined data of all captured updates. */
    private String renderedOutput() {
        return printer.getCaptured().stream()
                .map(Printer.Update::getData)
                .collect(Collectors.joining("\n"))
                .replaceAll("\u001B\\[[0-9;]*m", "");
    }

    /** All individual rendered data strings (ANSI stripped) in order. */
    private List<String> renderedParts() {
        return printer.getCaptured().stream()
                .map(u -> u.getData().replaceAll("\u001B\\[[0-9;]*m", ""))
                .collect(Collectors.toList());
    }
}
