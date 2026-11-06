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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.appform.sai.tools.ToolIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import lombok.SneakyThrows;


class FileIOTest {

    private Path tempDir;
    private Path testFile;

    static Stream<Arguments> editMatrix() {
        return Stream.of(
                         Arguments.of("replace single line",
                                      "line1\nline2\nline3",
                                      2,
                                      2,
                                      "replaced",
                                      "line1\nreplaced\nline3"),
                         Arguments.of("replace range with single line",
                                      "line1\nline2\nline3",
                                      2,
                                      3,
                                      "replaced",
                                      "line1\nreplaced"),
                         Arguments.of("replace range with multiple lines",
                                      "before\nold1\nold2\nafter",
                                      2,
                                      3,
                                      "new1\nnew2\nnew3",
                                      "before\nnew1\nnew2\nnew3\nafter"),
                         Arguments.of("delete single line", "line1\nline2\nline3", 2, 2, "", "line1\nline3"),
                         Arguments.of("delete range", "line1\nline2\nline3\nline4", 2, 3, "", "line1\nline4"),
                         Arguments.of("trailing newline stripped from replacement",
                                      "line1\nline2\nline3",
                                      2,
                                      2,
                                      "replaced\n",
                                      "line1\nreplaced\nline3"),
                         Arguments.of("negative end line replaces to end of file",
                                      "line1\nline2\nline3\nline4",
                                      3,
                                      -1,
                                      "replaced",
                                      "line1\nline2\nreplaced"),
                         Arguments.of("negative end line deletes to end of file",
                                      "line1\nline2\nline3",
                                      2,
                                      -1,
                                      "",
                                      "line1"));
    }

    static Stream<Arguments> invalidLineRangeArgs() {
        return Stream.of(
                         Arguments.of("zero start line", 0, 2),
                         Arguments.of("start greater than end", 3, 1),
                         Arguments.of("end beyond file length", 1, 100));
    }

    @SneakyThrows
    private static String checksum(String content) {
        final var digest = MessageDigest.getInstance("SHA-256");
        final var hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private static void deleteRecursive(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            try (var entries = Files.list(path)) {
                for (var entry : entries.toList()) {
                    deleteRecursive(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    @Test
    void checksumBehaviour() {
        // SHA-256 of the empty byte array is a fixed, well-known value.
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                     FileIO.calculateChecksum(new byte[0]));
        final var content = "hello world".getBytes(StandardCharsets.UTF_8);
        assertEquals(FileIO.calculateChecksum(content), FileIO.calculateChecksum(content));
        assertNotEquals(FileIO.calculateChecksum("hello".getBytes(StandardCharsets.UTF_8)),
                        FileIO.calculateChecksum("world".getBytes(StandardCharsets.UTF_8)));
        final var checksum = FileIO.calculateChecksum("test".getBytes(StandardCharsets.UTF_8));
        assertTrue(checksum.matches("[0-9a-f]{64}"), "not 64 lowercase hex chars: " + checksum);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("editMatrix")
    @SneakyThrows
    void editAppliesOperation(String description,
                              String content,
                              int startLine,
                              int endLine,
                              String replacement,
                              String expected) {
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder()
                                          .startLine(startLine)
                                          .endLine(endLine)
                                          .content(replacement)
                                          .build());

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        assertNotNull(result.getNewChecksum());
        assertEquals(checksum(expected), result.getNewChecksum());
        assertNotNull(result.getCorrections());
        assertTrue(result.getCorrections().isEmpty());
        assertEquals(expected, Files.readString(testFile));
    }

    @Test
    @SneakyThrows
    void editChecksumMismatch() {

        Files.writeString(testFile, "line1\nline2\nline3", StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(1).endLine(2).content("replaced")
                                          .build());

        final var result = FileIO.editFile(testFile.toString(), edits, "wrong-checksum");

        assertNotNull(result.getError());
        assertTrue(result.getError().contains("Checksum mismatch"), result.getError());
        assertNull(result.getNewChecksum());
    }

    @Test
    @SneakyThrows
    void editDetectsExternalModificationAfterRead() {
        // The edit tool also computes the checksum from the file on disk at every call.
        Files.writeString(testFile, "line1\nline2", StandardCharsets.UTF_8);
        final var readResult = FileIO.readFile(testFile.toString(), 1, -1, false);

        Files.writeString(testFile, "changed externally", StandardCharsets.UTF_8);

        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(1).endLine(1).content("edited").build());
        final var response = FileIO.editFile(testFile.toString(), edits, readResult.getChecksum());

        assertNotNull(response.getError());
        assertTrue(response.getError().contains("Checksum mismatch"), response.getError());
        assertEquals("changed externally", Files.readString(testFile));
    }

    @Test
    void editFileNotFound() {
        final var nonExistent = tempDir.resolve("ghost.txt");
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(1).endLine(1).content("new").build());

        final var result = FileIO.editFile(nonExistent.toString(), edits, "any");

        assertNotNull(result.getError());
        assertTrue(result.getError().contains("File not found"), result.getError());
        assertNull(result.getNewChecksum());
    }

    @Test
    @SneakyThrows
    void editKeepsFirstReplacementLineWhenItEqualsTheOriginalFirstReplacedLine() {
        final var content = "line1\nline2\nline3\nline4\nline5";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        // The first replacement line equals the original first replaced line, not the
        // untouched neighbour: this is a normal partial rewrite and must be kept.
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder()
                                          .startLine(2)
                                          .endLine(4)
                                          .content("line2\nline3 new\nline4 new")
                                          .build());

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        assertNotNull(result.getCorrections());
        assertTrue(result.getCorrections().isEmpty());
        assertEquals("line1\nline2\nline3 new\nline4 new\nline5", Files.readString(testFile));
    }

    @Test
    @SneakyThrows
    void editKeepsRepeatedLineWhenNeighbourDiffers() {
        final var content = "line1\nline2\nline3\nline4\nline5";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        // A repeated line inside the replacement that does NOT copy the untouched
        // neighbours must be kept as-is: it may be intentional.
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder()
                                          .startLine(2)
                                          .endLine(4)
                                          .content("line2 new\nline2 new\nline2 new")
                                          .build());

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        assertNotNull(result.getCorrections());
        assertTrue(result.getCorrections().isEmpty());
        assertEquals("line1\nline2 new\nline2 new\nline2 new\nline5", Files.readString(testFile));
    }

    @Test
    @SneakyThrows
    void editMissingChecksumReturnsDistinctError() {
        Files.writeString(testFile, "line1\nline2\nline3", StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(1).endLine(1).content("replaced")
                                          .build());

        final var result = FileIO.editFile(testFile.toString(), edits, null);

        assertNotNull(result.getError());
        assertTrue(result.getError().contains("expectedChecksum"), result.getError());
        assertFalse(result.getError().contains("Checksum mismatch"), result.getError());
        assertEquals("line1\nline2\nline3", Files.readString(testFile));
    }

    @Test
    @SneakyThrows
    void editMultipleEditsAppliedInReverseOrder() {
        final var content = "A\nB\nC\nD\nE";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(2).endLine(2).content("").build(),
                                  ToolIO.FileEditOperation.builder().startLine(4).endLine(4).content("").build());

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        final var written = Files.readString(testFile);
        assertFalse(written.contains("B"));
        assertFalse(written.contains("D"));
        assertTrue(written.contains("A"));
        assertTrue(written.contains("C"));
        assertTrue(written.contains("E"));
    }

    @Test
    @SneakyThrows
    void editRemovesDuplicatedBoundaryLinesOnBothEdges() {
        final var content = "line1\nline2\nline3\nline4\nline5";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder()
                                          .startLine(2)
                                          .endLine(4)
                                          .content("line1\nline2 new\nline3 new\nline4 new\nline5")
                                          .build());

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        assertNotNull(result.getCorrections());
        assertEquals(2, result.getCorrections().size());
        assertEquals("line1\nline2 new\nline3 new\nline4 new\nline5", Files.readString(testFile));
    }

    @Test
    @SneakyThrows
    void editRemovesDuplicatedFirstBoundaryLine() {
        final var content = "line1\nline2\nline3\nline4\nline5";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        // Model echoes line 1 (the untouched line before the range) as the first replacement line
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder()
                                          .startLine(2)
                                          .endLine(4)
                                          .content("line1\nline2 new\nline3 new\nline4 new")
                                          .build());

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        assertNotNull(result.getNewChecksum());
        assertNotNull(result.getCorrections());
        assertEquals(1, result.getCorrections().size());
        assertEquals("line1", result.getCorrections().get(0));
        assertEquals("line1\nline2 new\nline3 new\nline4 new\nline5", Files.readString(testFile));
    }

    @Test
    @SneakyThrows
    void editRemovesDuplicatedLastBoundaryLine() {
        final var content = "line1\nline2\nline3\nline4\nline5";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        // Model echoes line 5 (the untouched line after the range) as the last replacement line
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder()
                                          .startLine(2)
                                          .endLine(4)
                                          .content("line2 new\nline3 new\nline4 new\nline5")
                                          .build());

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        assertNotNull(result.getNewChecksum());
        assertNotNull(result.getCorrections());
        assertEquals(1, result.getCorrections().size());
        assertEquals("line5", result.getCorrections().get(0));
        assertEquals("line1\nline2 new\nline3 new\nline4 new\nline5", Files.readString(testFile));
    }

    @Test
    void readEmptyFile() {

        final var result = FileIO.readFile(testFile.toString(), 1, -1, false);

        assertNull(result.getError());
        assertEquals("", result.getContent());
        assertNotNull(result.getChecksum());
    }

    @Test
    void readFileNotFound() {
        final var nonExistent = tempDir.resolve("does-not-exist.txt");

        final var result = FileIO.readFile(nonExistent.toString(), 1, -1, false);

        assertNotNull(result.getError());
        assertTrue(result.getError().contains("File not found"), result.getError());
        assertNull(result.getContent());
        assertNull(result.getChecksum());
    }

    @Test
    @SneakyThrows
    void readFileTooLarge() {
        Files.write(testFile, new byte[1024 * 1024 + 1]);

        final var result = FileIO.readFile(testFile.toString(), 1, -1, false);

        assertNotNull(result.getError());
        assertTrue(result.getError().contains("1 MB"), result.getError());
        assertNull(result.getContent());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidLineRangeArgs")
    @SneakyThrows
    void readPartialInvalidLineRange(String description, int startLine, int endLine) {
        Files.writeString(testFile, "a\nb\nc", StandardCharsets.UTF_8);

        final var result = FileIO.readFile(testFile.toString(), startLine, endLine, false);

        assertNotNull(result.getError());
        assertTrue(result.getError().contains("Invalid line range"), result.getError());
    }

    @Test
    @SneakyThrows
    void readWholeFile() {
        final var content = "line1\nline2\nline3";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);

        final var result = FileIO.readFile(testFile.toString(), 1, -1, false);

        assertNull(result.getError());
        assertEquals(content, result.getContent());
        assertEquals(FileIO.calculateChecksum(content.getBytes(StandardCharsets.UTF_8)), result.getChecksum());
    }

    @Test
    @SneakyThrows
    void readWholeFileWithLineNumbersFlag() {
        final var content = "alpha\nbeta";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);

        final var result = FileIO.readFile(testFile.toString(), 1, -1, true);

        assertNull(result.getError());
        assertNotNull(result.getChecksum());
        assertTrue(result.getContent().contains("     1\talpha"), result.getContent());
        assertTrue(result.getContent().contains("     2\tbeta"), result.getContent());
    }

    @Test
    @SneakyThrows
    void safePathAllowsFilesUnderTmp() {
        final var tmpDir = Files.createTempDirectory("sai-test-");
        try {
            for (final var relative : List.of("test.txt", "nested/sub.txt")) {
                final var tmpFile = tmpDir.resolve(relative);
                Files.createDirectories(tmpFile.getParent());
                final var content = "tmp content";

                final var response = FileIO.write(tmpFile.toString(), content, "");
                assertNull(response.getError());

                final var readResult = FileIO.readFile(tmpFile.toString(), 1, -1, false);
                assertNull(readResult.getError());
                assertEquals(content, readResult.getContent());
            }
        }
        finally {
            deleteRecursive(tmpDir);
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory(Path.of("target"), "fileio-test-");
        testFile = tempDir.resolve("test.txt");
        Files.createFile(testFile);
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteRecursive(tempDir);
    }

    @Test
    @SneakyThrows
    void writeDetectsExternalModificationAfterRead() {
        // The checksum is computed from the file on disk at every write call. A change
        // made after the read, for example by the bash tool, must fail the write.
        Files.writeString(testFile, "original content", StandardCharsets.UTF_8);
        final var readResult = FileIO.readFile(testFile.toString(), 1, -1, false);

        Files.writeString(testFile, "changed externally", StandardCharsets.UTF_8);

        final var response = FileIO.write(testFile.toString(), "new content", readResult.getChecksum());

        assertNotNull(response.getError());
        assertTrue(response.getError().contains("Checksum mismatch"), response.getError());
        assertEquals("changed externally", Files.readString(testFile));

        // A write with the checksum of a fresh read succeeds.
        final var fresh = FileIO.readFile(testFile.toString(), 1, -1, false);
        final var retried = FileIO.write(testFile.toString(), "new content", fresh.getChecksum());

        assertNull(retried.getError());
        assertEquals("new content", Files.readString(testFile));
    }

    @Test
    @SneakyThrows
    void writeEmptyContent() {
        final var newFile = tempDir.resolve("empty.txt");

        final var response = FileIO.write(newFile.toString(), "", "");

        assertNull(response.getError());
        assertTrue(Files.exists(newFile));
        assertEquals("", Files.readString(newFile));
    }

    @Test
    @SneakyThrows
    void writeExistingFileWithMissingChecksumReturnsDistinctError() {
        final var content = "some content";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);

        final var response = FileIO.write(testFile.toString(), "new content", null);

        assertNotNull(response.getError());
        assertTrue(response.getError().contains("expectedChecksum"), response.getError());
        assertFalse(response.getError().contains("Checksum mismatch"), response.getError());
        assertEquals(content, Files.readString(testFile));
    }

    @Test
    @SneakyThrows
    void writeFileSizeLimits() {
        final var atLimit = tempDir.resolve("at-limit.txt");
        final var atLimitContent = "x".repeat(1024 * 1024);

        final var atLimitResponse = FileIO.write(atLimit.toString(), atLimitContent, "");

        assertNull(atLimitResponse.getError());
        assertEquals(1024L * 1024L, Files.size(atLimit));

        Files.write(testFile, new byte[1024 * 1024 + 1]);
        final var overLimitExisting = FileIO.write(testFile.toString(), "small content", "any");
        assertNotNull(overLimitExisting.getError());
        assertTrue(overLimitExisting.getError().contains("1 MB"), overLimitExisting.getError());

        final var overLimitNew = tempDir.resolve("too-large-new.txt");
        final var overLimitNewResponse = FileIO.write(overLimitNew.toString(), "x".repeat(1024 * 1024 + 1), "");
        assertNotNull(overLimitNewResponse.getError());
        assertTrue(overLimitNewResponse.getError().contains("1 MB"), overLimitNewResponse.getError());
        assertFalse(Files.exists(overLimitNew));
    }

    @Test
    @SneakyThrows
    void writeNewFile() {
        final var newFile = tempDir.resolve("new.txt");
        final var content = "hello world";

        final var response = FileIO.write(newFile.toString(), content, "");

        assertNull(response.getError());
        assertEquals(content.length(), response.getCharsWritten());
        assertTrue(Files.exists(newFile));
        assertEquals(content, Files.readString(newFile));
    }

    @Test
    @SneakyThrows
    void writeOverwritesWithCorrectChecksum() {
        final var original = "original content";
        Files.writeString(testFile, original, StandardCharsets.UTF_8);
        final var newContent = "new content";

        final var response = FileIO.write(testFile.toString(), newContent, checksum(original));

        assertNull(response.getError());
        assertEquals(newContent, Files.readString(testFile));
    }

    @Test
    void writeRoundTripChecksumConsistent() {
        final var newFile = tempDir.resolve("round-trip.txt");
        final var content = "round trip content";

        final var writeResponse = FileIO.write(newFile.toString(), content, "");
        assertNull(writeResponse.getError());

        final var readResult = FileIO.readFile(newFile.toString(), 1, -1, false);
        assertNull(readResult.getError());
        assertEquals(content, readResult.getContent());
        assertEquals(FileIO.calculateChecksum(content.getBytes(StandardCharsets.UTF_8)), readResult.getChecksum());
    }

    @Test
    @SneakyThrows
    void writeWrongChecksumReturnsError() {
        final var content = "some content";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);

        final var response = FileIO.write(testFile.toString(), "new content", "wrong-checksum");

        assertNotNull(response.getError());
        assertTrue(response.getError().contains("Checksum mismatch"), response.getError());
        assertEquals(content, Files.readString(testFile));
    }
}
