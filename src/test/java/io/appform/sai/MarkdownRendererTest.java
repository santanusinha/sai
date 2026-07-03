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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MarkdownRendererTest {

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Strip every ANSI escape sequence from {@code s}. */
    private static String stripAnsi(String s) {
        return s.replaceAll("\u001B\\[[0-9;]*m", "");
    }

    // ------------------------------------------------------------------
    // Null / blank passthrough
    // ------------------------------------------------------------------

    @Test
    void blankInputReturnsBlank() {
        final var result = MarkdownRenderer.toAnsi("   ");
        assertNotNull(result);
        assertTrue(result.isBlank());
    }

    @Test
    void blockquoteContainsCyanAndBlockMarker() {
        final var result = MarkdownRenderer.toAnsi("> some quote");
        assertTrue(result.contains(Printer.Colours.CYAN), "blockquote should be cyan");
        assertTrue(result.contains("▌ "), "blockquote marker should be present");
        assertTrue(stripAnsi(result).contains("some quote"), "quote text should survive");
    }

    // ------------------------------------------------------------------
    // Inline formatting
    // ------------------------------------------------------------------

    @Test
    void boldTextIsWrappedWithBoldEscapes() {
        final var result = MarkdownRenderer.toAnsi("**hello**");
        assertTrue(result.contains("\u001B[1m"), "should open bold");
        assertTrue(result.contains("\u001B[22m"), "should close bold");
        assertTrue(stripAnsi(result).contains("hello"), "plain text should be present");
    }

    @Test
    void bulletListItemsContainBulletCharacter() {
        final var md = "- alpha\n- beta\n- gamma\n";
        final var result = MarkdownRenderer.toAnsi(md);
        final var plain = stripAnsi(result);
        assertTrue(plain.contains("• alpha"), "first item should have bullet");
        assertTrue(plain.contains("• beta"), "second item should have bullet");
        assertTrue(plain.contains("• gamma"), "third item should have bullet");
    }

    @Test
    void fencedCodeBlockContainsLanguageTagAndGrayLiteral() {
        final var md = "```java\nSystem.out.println(\"hi\");\n```";
        final var result = MarkdownRenderer.toAnsi(md);
        assertTrue(result.contains("[java]"), "language info tag should be present");
        assertTrue(result.contains("System.out.println"), "code literal should be present");
        assertTrue(result.contains(Printer.Colours.GRAY), "code block should use GRAY colour");
    }

    // ------------------------------------------------------------------
    // Headings
    // ------------------------------------------------------------------

    @Test
    void fencedCodeBlockWithoutLanguageHasNoInfoTag() {
        final var md = "```\nplain code\n```";
        final var result = MarkdownRenderer.toAnsi(md);
        assertTrue(stripAnsi(result).contains("plain code"), "code literal should be present");
    }

    // ------------------------------------------------------------------
    // Code blocks
    // ------------------------------------------------------------------

    @Test
    void headingContainsCyanBoldAndLevelMarker() {
        final var result = MarkdownRenderer.toAnsi("## Section");
        assertTrue(result.contains(Printer.Colours.CYAN), "heading should be cyan");
        assertTrue(result.contains("\u001B[1m"), "heading should be bold");
        assertTrue(stripAnsi(result).contains("## Section"), "heading text and hashes should survive strip");
    }

    @Test
    void inlineCodeIsHighlightedWithYellowAndBackticks() {
        final var result = MarkdownRenderer.toAnsi("Use `foo()` here.");
        assertTrue(result.contains("`foo()`"), "backtick-wrapped literal should be present");
        assertTrue(result.contains(Printer.Colours.YELLOW), "should use YELLOW colour for inline code");
    }

    // ------------------------------------------------------------------
    // Lists
    // ------------------------------------------------------------------

    @Test
    void italicTextIsWrappedWithItalicEscapes() {
        final var result = MarkdownRenderer.toAnsi("*world*");
        assertTrue(result.contains("\u001B[3m"), "should open italic");
        assertTrue(result.contains("\u001B[23m"), "should close italic");
        assertTrue(stripAnsi(result).contains("world"), "plain text should be present");
    }

    @Test
    void mixedContentDoesNotThrow() {
        final var md = """
                # Title

                Some **bold** and *italic* text with `code`.

                - item one
                - item two

                | Col A | Col B |
                |-------|-------|
                | x     | y     |

                ```python
                print("hello")
                ```

                > A blockquote

                ---
                """;
        final var result = MarkdownRenderer.toAnsi(md);
        assertNotNull(result);
        final var plain = stripAnsi(result);
        assertTrue(plain.contains("Title"), "heading text");
        assertTrue(plain.contains("bold"), "bold text");
        assertTrue(plain.contains("italic"), "italic text");
        assertTrue(plain.contains("code"), "inline code text");
        assertTrue(plain.contains("• item one"), "bullet item");
        assertTrue(plain.contains("Col A"), "table header");
        assertTrue(plain.contains("x"), "table body cell");
        assertTrue(plain.contains("print"), "fenced code literal");
        assertTrue(plain.contains("A blockquote"), "blockquote text");
    }

    // ------------------------------------------------------------------
    // Blockquote
    // ------------------------------------------------------------------

    @Test
    void nullInputReturnsNull() {
        assertNull(MarkdownRenderer.toAnsi(null));
    }

    // ------------------------------------------------------------------
    // Thematic break
    // ------------------------------------------------------------------

    @Test
    void orderedListItemsContainSequentialNumbers() {
        final var md = "1. first\n2. second\n3. third\n";
        final var result = MarkdownRenderer.toAnsi(md);
        final var plain = stripAnsi(result);
        assertTrue(plain.contains("1. first"), "first numbered item");
        assertTrue(plain.contains("2. second"), "second numbered item");
        assertTrue(plain.contains("3. third"), "third numbered item");
    }

    // ------------------------------------------------------------------
    // GFM tables
    // ------------------------------------------------------------------

    @Test
    void plainParagraphPreservesText() {
        final var result = MarkdownRenderer.toAnsi("Hello, world!");
        assertTrue(stripAnsi(result).contains("Hello, world!"), "plain text should be preserved");
    }

    @Test
    void tableCellTextIsPresentInOutput() {
        final var md = "| Language | Stars |\n|----------|-------|\n| Java     | 9999  |\n| Kotlin   | 8888  |\n";
        final var result = MarkdownRenderer.toAnsi(md);
        final var plain = stripAnsi(result);

        assertTrue(plain.contains("Language"), "header cell 'Language'");
        assertTrue(plain.contains("Stars"), "header cell 'Stars'");
        assertTrue(plain.contains("Java"), "body cell 'Java'");
        assertTrue(plain.contains("Kotlin"), "body cell 'Kotlin'");
        assertTrue(plain.contains("9999"), "body cell '9999'");
        assertTrue(plain.contains("8888"), "body cell '8888'");
    }

    @Test
    void tableHeaderReceivesBoldAndCyanStyling() {
        final var md = "| Name  | Age |\n|-------|-----|\n| Alice | 30  |\n";
        final var result = MarkdownRenderer.toAnsi(md);

        // Header cells get CYAN + bold before the cell text
        assertTrue(result.contains(Printer.Colours.CYAN), "header cells should be CYAN");
        assertTrue(result.contains("\u001B[1m"), "header cells should be bold");
    }

    @Test
    void tableHeaderRowGetsHeaderSeparator() {
        final var md = "| Name  | Age |\n|-------|-----|\n| Alice | 30  |\n";
        final var result = MarkdownRenderer.toAnsi(md);
        final var plain = stripAnsi(result);

        // The separator row between header and body uses ├ ... ┼ ... ┤
        assertTrue(plain.contains("├"), "header/body separator left junction");
        assertTrue(plain.contains("┤"), "header/body separator right junction");
    }

    @Test
    void tableRendersBoxDrawingBorders() {
        final var md = "| Name  | Age |\n|-------|-----|\n| Alice | 30  |\n| Bob   | 25  |\n";
        final var result = MarkdownRenderer.toAnsi(md);
        final var plain = stripAnsi(result);

        assertTrue(plain.contains("┌"), "top-left corner should be present");
        assertTrue(plain.contains("┐"), "top-right corner should be present");
        assertTrue(plain.contains("└"), "bottom-left corner should be present");
        assertTrue(plain.contains("┘"), "bottom-right corner should be present");
        assertTrue(plain.contains("│"), "vertical separator should be present");
        assertTrue(plain.contains("─"), "horizontal line should be present");
    }

    // ------------------------------------------------------------------
    // Plain paragraph (regression)
    // ------------------------------------------------------------------

    @Test
    void tableWithInlineFormattingInCells() {
        final var md = "| Feature       | Status |\n|---------------|--------|\n| **Bold item** | ✓      |\n";
        final var result = MarkdownRenderer.toAnsi(md);
        final var plain = stripAnsi(result);

        assertTrue(plain.contains("Bold item"), "bold cell text stripped of markdown should appear");
        assertTrue(plain.contains("✓"), "emoji/unicode in cell should pass through");
    }

    // ------------------------------------------------------------------
    // Mixed content (smoke test)
    // ------------------------------------------------------------------

    @Test
    void thematicBreakRendersHorizontalLine() {
        final var result = MarkdownRenderer.toAnsi("---");
        assertTrue(stripAnsi(result).contains("─"), "thematic break should produce horizontal line characters");
    }
}
