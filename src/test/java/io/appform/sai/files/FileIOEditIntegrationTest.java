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
package io.appform.sai.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.appform.sai.tools.ToolIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import lombok.SneakyThrows;

/**
 * Complex integration tests for editFile to verify all 4 reported bugs are fixed
 * and the tool works correctly in real-world multi-edit scenarios.
 */
class FileIOEditIntegrationTest {

    private Path tempDir;
    private Path testFile;

    @Test
    @SneakyThrows
    void chainedEditsWithChecksum() {
        final var content = "a\nb\nc\nd\ne";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);

        // Chain 3 sequential edits, each using the returned checksum
        var cs = checksum(content);

        var r1 = FileIO.editFile(testFile.toString(),
                                 List.of(ToolIO.FileEditOperation.builder().startLine(1).endLine(1).content("A")
                                         .build()),
                                 cs);
        assertNull(r1.getError());
        cs = r1.getNewChecksum();

        var r2 = FileIO.editFile(testFile.toString(),
                                 List.of(ToolIO.FileEditOperation.builder().startLine(3).endLine(3).content("C")
                                         .build()),
                                 cs);
        assertNull(r2.getError());
        cs = r2.getNewChecksum();

        var r3 = FileIO.editFile(testFile.toString(),
                                 List.of(ToolIO.FileEditOperation.builder().startLine(5).endLine(5).content("E")
                                         .build()),
                                 cs);
        assertNull(r3.getError());

        final var written = Files.readString(testFile);
        assertEquals("A\nb\nC\nd\nE", written);
    }

    @Test
    @SneakyThrows
    void checksumMismatchReturnsError() {
        final var content = "line1\nline2\nline3";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(2).endLine(2).content("modified").build()
        );

        final var result = FileIO.editFile(testFile.toString(), edits, "wrong_checksum_value");

        assertNotNull(result.getError());
        assertTrue(result.getError().contains("Checksum mismatch"));
        // File should be unchanged
        assertEquals(content, Files.readString(testFile));
    }

    @Test
    @SneakyThrows
    void checksumUsedForSubsequentEdit() {
        final var content = "line1\nline2\nline3";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);

        // First edit
        final var edits1 = List.of(
                                   ToolIO.FileEditOperation.builder().startLine(2).endLine(2).content("modified2")
                                           .build()
        );
        final var result1 = FileIO.editFile(testFile.toString(), edits1, checksum(content));
        assertNull(result1.getError());
        assertNotNull(result1.getNewChecksum());

        // Second edit using returned checksum — should NOT get checksum mismatch
        final var edits2 = List.of(
                                   ToolIO.FileEditOperation.builder().startLine(3).endLine(3).content("modified3")
                                           .build()
        );
        final var result2 = FileIO.editFile(testFile.toString(), edits2, result1.getNewChecksum());
        assertNull(result2.getError(),
                   "Second edit with returned checksum should succeed, but got: " + result2.getError());
        assertNotNull(result2.getNewChecksum());

        final var written = Files.readString(testFile);
        assertEquals("line1\nmodified2\nmodified3", written);
    }

    // ===== Bug #1: Single-line replacement =====

    @Test
    @SneakyThrows
    void deleteMultipleLinesAndReplace() {
        final var content = "header\n// remove this\n// and this\n// and this too\nkeep\nfooter";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);

        // Delete lines 2-4 (the comments)
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(2).endLine(4).content("").build()
        );

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        final var written = Files.readString(testFile);
        assertEquals("header\nkeep\nfooter", written);
    }

    @Test
    @SneakyThrows
    void endLineMinusOneDeletesFromStartToEnd() {
        final var content = "keep1\nremove2\nremove3\nremove4";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(2).endLine(-1).content("").build()
        );

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        final var written = Files.readString(testFile);
        assertEquals("keep1", written);
    }

    @Test
    @SneakyThrows
    void endLineMinusOneFromLine1ReplacesAll() {
        final var content = "old1\nold2\nold3";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(1).endLine(-1).content(
                                                                                                      "completely\nnew\ncontent")
                                          .build()
        );

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        final var written = Files.readString(testFile);
        assertEquals("completely\nnew\ncontent", written);
    }

    // ===== Bug #2: endLine=-1 replaces to end of file =====

    @Test
    @SneakyThrows
    void endLineMinusOneReplacesFromStartToEnd() {
        final var content = "keep1\nkeep2\nremove3\nremove4\nremove5";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(3).endLine(-1).content("new_end").build()
        );

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        final var written = Files.readString(testFile);
        assertEquals("keep1\nkeep2\nnew_end", written);
    }

    @Test
    @SneakyThrows
    void expandingEditOutOfOrderKeepsOtherEditAligned() {
        final var content = "L1\nL2\nL3\nL4\nL5\nL6\nL7\nL8";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        // Lower edit expands one line into three; higher edit is a plain replace.
        // Supplied high-to-low; the higher replace must still land on original line 6.
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(6).endLine(6).content("Y").build(),
                                  ToolIO.FileEditOperation.builder().startLine(2).endLine(2).content("A\nB\nC")
                                          .build()
        );

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        final var written = Files.readString(testFile);
        assertEquals("L1\nA\nB\nC\nL3\nL4\nL5\nY\nL7\nL8", written);
    }

    @Test
    @SneakyThrows
    void invalidLineRange() {
        final var content = "line1\nline2\nline3";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(5).endLine(5).content("out_of_range")
                                          .build()
        );

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNotNull(result.getError());
        assertTrue(result.getError().contains("Invalid line range"));
    }

    // ===== Bug #3: Checksum returned after edit =====

    @Test
    @SneakyThrows
    void multilineContentWithTrailingNewline() {
        final var content = "line1\nline2\nline3\nline4";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(2).endLine(3).content("new2\nnew3\n")
                                          .build()
        );

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        final var written = Files.readString(testFile);
        assertEquals("line1\nnew2\nnew3\nline4", written);
    }

    @Test
    @SneakyThrows
    void multipleEditsInReverseOrder() {
        final var content = "import java.util.List;\nimport java.util.Map;\n\npublic class Example {\n    void method1() {}\n    void method2() {}\n    void method3() {}\n}";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        // Multiple edits: replace method2 AND add a new import
        // Edits are applied in reverse order, so line numbers refer to original content
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(2).endLine(2).content(
                                                                                                     "import java.util.Map;\nimport java.util.Set;")
                                          .build(),
                                  ToolIO.FileEditOperation.builder().startLine(6).endLine(6).content(
                                                                                                     "    void method2Modified() {}")
                                          .build()
        );

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        final var written = Files.readString(testFile);
        assertTrue(written.contains("import java.util.Set;"), "New import should be added");
        assertTrue(written.contains("method2Modified"), "Method should be replaced");
        assertFalse(written.contains("import java.util.Map;\nimport java.util.Map;"),
                    "Should not duplicate Map import");
    }

    // ===== Bug #4: Trailing newline handling =====

    @Test
    @SneakyThrows
    void multipleEditsSuppliedOutOfOrderDoNotDuplicateBoundaries() {
        final var content = "L1\nL2\nL3\nL4\nL5\nL6\nL7\nL8";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        // Two "insert" idiom edits (re-send the boundary line + new line), but the caller
        // lists them in descending order instead of ascending. This previously produced a
        // duplicated boundary line ("L6") in the output.
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(6).endLine(6).content("L6\nNEW_B")
                                          .build(),
                                  ToolIO.FileEditOperation.builder().startLine(2).endLine(2).content("L2\nNEW_A")
                                          .build()
        );

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        final var written = Files.readString(testFile);
        assertEquals("L1\nL2\nNEW_A\nL3\nL4\nL5\nL6\nNEW_B\nL7\nL8", written);
    }

    @Test
    @SneakyThrows
    void overlappingEditRangesAreRejected() {
        final var content = "L1\nL2\nL3\nL4\nL5\nL6";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(2).endLine(4).content("A").build(),
                                  ToolIO.FileEditOperation.builder().startLine(4).endLine(6).content("B").build()
        );

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNotNull(result.getError());
        assertTrue(result.getError().contains("Overlapping"));
        // File must be left untouched on a rejected edit set
        assertEquals(content, Files.readString(testFile));
    }

    // ===== Complex multi-edit scenarios =====

    @Test
    @SneakyThrows
    void realWorldJavaMethodReplacement() {
        final var content = String.join("\n",
                                        List.of(
                                                "package com.example;",
                                                "",
                                                "public class Service {",
                                                "",
                                                "    public String process(String input) {",
                                                "        return input.toUpperCase();",
                                                "    }",
                                                "",
                                                "    public void cleanup() {",
                                                "        // TODO: implement",
                                                "    }",
                                                "}"
                                        ));
        Files.writeString(testFile, content, StandardCharsets.UTF_8);

        // Replace the process method body (lines 5-7) with a more complex implementation
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder()
                                          .startLine(5)
                                          .endLine(7)
                                          .content("    public String process(String input) {\n        if (input == null) {\n            return \"\";\n        }\n        return input.trim().toUpperCase();\n    }")
                                          .build()
        );

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        final var written = Files.readString(testFile);
        assertTrue(written.contains("if (input == null)"));
        assertTrue(written.contains("return input.trim().toUpperCase()"));
        assertFalse(written.contains("return input.toUpperCase();"));
        // Verify structure preserved
        assertTrue(written.contains("public void cleanup()"));
        assertTrue(written.contains("package com.example;"));
    }

    @Test
    @SneakyThrows
    void replaceFirstLine() {
        final var content = "old_first\nsecond\nthird";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(1).endLine(1).content("new_first")
                                          .build()
        );

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        final var written = Files.readString(testFile);
        assertEquals("new_first\nsecond\nthird", written);
    }

    @Test
    @SneakyThrows
    void replaceLastLine() {
        final var content = "first\nsecond\nold_last";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(3).endLine(3).content("new_last").build()
        );

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        final var written = Files.readString(testFile);
        assertEquals("first\nsecond\nnew_last", written);
    }

    @Test
    @SneakyThrows
    void replaceRangeWithFewerLines() {
        final var content = "line1\nline2\nline3\nline4\nline5";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        // Replace 3 lines (2-4) with 1 line
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(2).endLine(4).content(
                                                                                                     "single_replacement")
                                          .build()
        );

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        final var written = Files.readString(testFile);
        assertEquals("line1\nsingle_replacement\nline5", written);
    }

    @Test
    @SneakyThrows
    void replaceRangeWithMoreLines() {
        final var content = "line1\nline2\nline3";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        // Replace 1 line (2) with 4 lines
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(2).endLine(2).content(
                                                                                                     "expanded_a\nexpanded_b\nexpanded_c\nexpanded_d")
                                          .build()
        );

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        final var written = Files.readString(testFile);
        assertEquals("line1\nexpanded_a\nexpanded_b\nexpanded_c\nexpanded_d\nline3", written);
    }

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory(Path.of("target"), "fileio-edit-integration");
        testFile = tempDir.resolve("test.java");
    }

    @Test
    @SneakyThrows
    void singleLineDeleteEmpty() {
        final var content = "line1\nline2\nline3";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(2).endLine(2).content("").build()
        );

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        final var written = Files.readString(testFile);
        assertEquals("line1\nline3", written);
    }

    @Test
    @SneakyThrows
    void singleLineReplaceNoDuplicate() {
        final var content = "public class Foo {\n    int x = 1;\n    int y = 2;\n}";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(2).endLine(2).content("    int x = 42;")
                                          .build()
        );

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        final var written = Files.readString(testFile);
        assertEquals("public class Foo {\n    int x = 42;\n    int y = 2;\n}", written);
        // Critical: original line must NOT be present
        assertFalse(written.contains("int x = 1"));
    }

    @Test
    @SneakyThrows
    void singleLineReplaceWithMultilineContent() {
        final var content = "line1\nline2\nline3";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(2).endLine(2).content(
                                                                                                     "new2a\nnew2b\nnew2c")
                                          .build()
        );

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        final var written = Files.readString(testFile);
        assertEquals("line1\nnew2a\nnew2b\nnew2c\nline3", written);
    }

    @Test
    @SneakyThrows
    void successfulEditReturnsChecksum() {
        final var content = "line1\nline2\nline3";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(2).endLine(2).content("modified").build()
        );

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        assertNotNull(result.getNewChecksum(), "Checksum must be returned after successful edit");
        assertFalse(result.getNewChecksum().isEmpty(), "Checksum must not be empty");
        // Verify returned checksum matches the actual file content
        final var actualContent = Files.readString(testFile);
        assertEquals(checksum(actualContent), result.getNewChecksum());
    }

    // ===== Regression: boundary duplication when edits are supplied out of order =====

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(testFile)) {
            Files.delete(testFile);
        }
        Files.delete(tempDir);
    }

    @Test
    @SneakyThrows
    void trailingNewlineNoExtraLine() {
        final var content = "line1\nline2\nline3";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(2).endLine(2).content("replaced\n")
                                          .build()
        );

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        final var written = Files.readString(testFile);
        // Should NOT have an extra blank line: "line1\nreplaced\n\nline3"
        assertEquals("line1\nreplaced\nline3", written);
    }

    private String checksum(String content) {
        return FileIO.calculateChecksum(content.getBytes(StandardCharsets.UTF_8));
    }
}
