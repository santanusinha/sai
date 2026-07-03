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

import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import lombok.experimental.UtilityClass;

/**
 * Converts Markdown text (as returned by an LLM) into an ANSI-escaped string
 * suitable for display in a JLine terminal via {@link Printer}.
 *
 * <p>Uses the {@code commonmark-java} AST visitor so that fenced code blocks,
 * nested emphasis, and other constructs are handled correctly — unlike a purely
 * regex-based approach which can misfire inside code spans.
 *
 * <p>Supports GitHub Flavored Markdown (GFM) extensions including tables.
 *
 * <h3>GFM table dispatch</h3>
 * commonmark-java's {@code TableBlock} extends {@link CustomBlock} and
 * {@code TableBody/Head/Row/Cell} extend {@link CustomNode}. Their
 * {@code accept()} methods dispatch to {@code visitor.visit(CustomBlock)} and
 * {@code visitor.visit(CustomNode)} respectively — NOT to type-specific
 * overloads. We therefore override those two methods and use {@code instanceof}
 * pattern matching to reach our table logic.
 */
@UtilityClass
public class MarkdownRenderer {

    private static final Parser PARSER = Parser.builder().extensions(Arrays.asList(TablesExtension.create())).build();

    private static final class AnsiVisitor extends AbstractVisitor {

        private final StringBuilder sb = new StringBuilder();
        // Stack tracking whether each nesting level is an ordered list (true) or bullet (false)
        private final Deque<Boolean> listTypeStack = new ArrayDeque<>();
        private int orderedListCounter = 0;
        private final List<List<String>> tableRows = new ArrayList<>();
        private List<String> currentRow = null;
        private StringBuilder currentCell = null;

        private static String stripAnsi(String s) {
            return s.replaceAll("\u001B\\[[0-9;]*m", "");
        }

        @Override
        public void visit(BlockQuote blockQuote) {
            sb.append(Printer.Colours.CYAN).append("▌ ");
            visitChildren(blockQuote);
            sb.append(Printer.Colours.RESET);
        }

        @Override
        public void visit(BulletList bulletList) {
            listTypeStack.push(false);
            visitChildren(bulletList);
            listTypeStack.pop();
            if (listTypeStack.isEmpty()) {
                sb.append("\n");
            }
        }

        @Override
        public void visit(Code code) {
            final var target = currentCell != null ? currentCell : sb;
            target.append(Printer.Colours.YELLOW)
                    .append("`")
                    .append(code.getLiteral())
                    .append("`")
                    .append(Printer.Colours.RESET);
        }

        /**
         * Entry point for all GFM table nodes ({@link TableBlock} extends {@link CustomBlock}).
         * {@code TableBlock.accept()} calls {@code visitor.visit(CustomBlock)}, so we dispatch
         * here via {@code instanceof} rather than relying on unreachable overloaded methods.
         */
        @Override
        public void visit(CustomBlock customBlock) {
            if (customBlock instanceof TableBlock) {
                tableRows.clear();
                visitChildren(customBlock);
                renderTable(tableRows);
            }
            else {
                visitChildren(customBlock);
            }
        }

        /**
         * Entry point for {@link TableBody}, {@link TableHead}, {@link TableRow}, and
         * {@link TableCell} — all extend {@link CustomNode} whose {@code accept()} dispatches
         * to {@code visitor.visit(CustomNode)}.
         */
        @Override
        public void visit(CustomNode customNode) {
            if (customNode instanceof TableBody || customNode instanceof TableHead) {
                visitChildren(customNode);
            }
            else if (customNode instanceof TableRow) {
                currentRow = new ArrayList<>();
                visitChildren(customNode);
                if (!currentRow.isEmpty()) {
                    tableRows.add(new ArrayList<>(currentRow));
                }
                currentRow = null;
            }
            else if (customNode instanceof TableCell) {
                currentCell = new StringBuilder();
                visitChildren(customNode);
                if (currentRow != null) {
                    currentRow.add(currentCell.toString());
                }
                currentCell = null;
            }
            else {
                visitChildren(customNode);
            }
        }

        @Override
        public void visit(Emphasis emphasis) {
            final var target = currentCell != null ? currentCell : sb;
            target.append("\u001B[3m");
            visitChildren(emphasis);
            target.append("\u001B[23m");
        }

        @Override
        public void visit(FencedCodeBlock fencedCodeBlock) {
            final var info = fencedCodeBlock.getInfo();
            sb.append(Printer.Colours.YELLOW);
            if (info != null && !info.isBlank()) {
                sb.append("[").append(info).append("]");
            }
            sb.append("\n").append(Printer.Colours.RESET);
            sb.append(Printer.Colours.GRAY)
                    .append(fencedCodeBlock.getLiteral())
                    .append(Printer.Colours.RESET);
        }

        @Override
        public void visit(HardLineBreak hardLineBreak) {
            (currentCell != null ? currentCell : sb).append("\n");
        }

        @Override
        public void visit(Heading heading) {
            sb.append(Printer.Colours.CYAN)
                    .append("\u001B[1m")
                    .append("#".repeat(heading.getLevel()))
                    .append(" ");
            visitChildren(heading);
            sb.append("\u001B[22m").append(Printer.Colours.RESET).append("\n");
        }

        @Override
        public void visit(HtmlInline htmlInline) {
            final var target = currentCell != null ? currentCell : sb;
            target.append(Printer.Colours.GRAY)
                    .append(htmlInline.getLiteral())
                    .append(Printer.Colours.RESET);
        }

        @Override
        public void visit(IndentedCodeBlock indentedCodeBlock) {
            sb.append(Printer.Colours.GRAY)
                    .append(indentedCodeBlock.getLiteral())
                    .append(Printer.Colours.RESET);
        }

        @Override
        public void visit(Link link) {
            visitChildren(link);
            (currentCell != null ? currentCell : sb)
                    .append(Printer.Colours.CYAN)
                    .append(" (")
                    .append(link.getDestination())
                    .append(")")
                    .append(Printer.Colours.RESET);
        }

        @Override
        public void visit(ListItem listItem) {
            final int depth = listTypeStack.size();
            final var indent = "  ".repeat(depth - 1);
            sb.append(Printer.Colours.YELLOW).append(indent);
            final boolean ordered = Boolean.TRUE.equals(listTypeStack.peek());
            if (ordered) {
                sb.append(orderedListCounter++).append(". ");
            }
            else {
                sb.append("• ");
            }
            sb.append(Printer.Colours.RESET);
            visitChildren(listItem);
        }

        @Override
        public void visit(OrderedList orderedList) {
            final int savedCounter = orderedListCounter;
            orderedListCounter = orderedList.getMarkerStartNumber();
            listTypeStack.push(true);
            visitChildren(orderedList);
            listTypeStack.pop();
            orderedListCounter = savedCounter;
            if (listTypeStack.isEmpty()) {
                sb.append("\n");
            }
        }

        @Override
        public void visit(Paragraph paragraph) {
            if (currentCell != null) {
                appendChildrenToBuffer(paragraph, currentCell);
            }
            else {
                visitChildren(paragraph);
                sb.append("\n");
            }
        }

        @Override
        public void visit(SoftLineBreak softLineBreak) {
            (currentCell != null ? currentCell : sb).append(" ");
        }

        @Override
        public void visit(StrongEmphasis strongEmphasis) {
            final var target = currentCell != null ? currentCell : sb;
            target.append("\u001B[1m");
            visitChildren(strongEmphasis);
            target.append("\u001B[22m");
        }

        @Override
        public void visit(Text text) {
            (currentCell != null ? currentCell : sb).append(text.getLiteral());
        }

        @Override
        public void visit(ThematicBreak thematicBreak) {
            sb.append(Printer.Colours.GRAY)
                    .append("─".repeat(60))
                    .append(Printer.Colours.RESET)
                    .append("\n");
        }

        String build() {
            return sb.toString();
        }

        private void appendChildrenToBuffer(Node node, StringBuilder buffer) {
            var child = node.getFirstChild();
            while (child != null) {
                if (child instanceof Text t) {
                    buffer.append(t.getLiteral());
                }
                else if (child instanceof Code c) {
                    buffer.append(Printer.Colours.YELLOW)
                            .append("`")
                            .append(c.getLiteral())
                            .append("`")
                            .append(Printer.Colours.RESET);
                }
                else if (child instanceof StrongEmphasis) {
                    buffer.append("\u001B[1m");
                    appendChildrenToBuffer(child, buffer);
                    buffer.append("\u001B[22m");
                }
                else if (child instanceof Emphasis) {
                    buffer.append("\u001B[3m");
                    appendChildrenToBuffer(child, buffer);
                    buffer.append("\u001B[23m");
                }
                else {
                    appendChildrenToBuffer(child, buffer);
                }
                child = child.getNext();
            }
        }

        private void renderTable(List<List<String>> rows) {
            if (rows.isEmpty()) {
                return;
            }
            final var numCols = rows.get(0).size();
            final var colWidths = new int[numCols];
            for (final var row : rows) {
                for (int i = 0; i < Math.min(row.size(), numCols); i++) {
                    colWidths[i] = Math.max(colWidths[i], stripAnsi(row.get(i)).length());
                }
            }

            sb.append(Printer.Colours.GRAY);
            // Top border
            sb.append("┌");
            for (int i = 0; i < numCols; i++) {
                sb.append("─".repeat(colWidths[i] + 2));
                sb.append(i < numCols - 1 ? "┬" : "┐");
            }
            sb.append("\n");

            for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
                final var row = rows.get(rowIdx);
                sb.append("│");
                for (int i = 0; i < numCols; i++) {
                    final var cell = i < row.size() ? row.get(i) : "";
                    final var padding = colWidths[i] - stripAnsi(cell).length();
                    sb.append(" ");
                    if (rowIdx == 0) {
                        sb.append(Printer.Colours.CYAN).append("\u001B[1m");
                        sb.append(cell);
                        sb.append("\u001B[22m").append(Printer.Colours.GRAY);
                    }
                    else {
                        sb.append(Printer.Colours.WHITE).append(cell).append(Printer.Colours.GRAY);
                    }
                    sb.append(" ".repeat(padding)).append(" │");
                }
                sb.append("\n");
                // Separator after header row
                if (rowIdx == 0) {
                    sb.append("├");
                    for (int i = 0; i < numCols; i++) {
                        sb.append("─".repeat(colWidths[i] + 2));
                        sb.append(i < numCols - 1 ? "┼" : "┤");
                    }
                    sb.append("\n");
                }
            }

            // Bottom border
            sb.append("└");
            for (int i = 0; i < numCols; i++) {
                sb.append("─".repeat(colWidths[i] + 2));
                sb.append(i < numCols - 1 ? "┴" : "┘");
            }
            sb.append("\n");
            sb.append(Printer.Colours.RESET);
        }
    }

    // -------------------------------------------------------------------------

    /**
     * Parse {@code markdown} and return an ANSI-escaped string ready for
     * {@link Printer#assistantMessage(String)}.
     */
    public static String toAnsi(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return markdown;
        }
        final var visitor = new AnsiVisitor();
        PARSER.parse(markdown).accept(visitor);
        return visitor.build();
    }
}
