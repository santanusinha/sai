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
    void checksumDifferentContent() {
        final var a = "hello".getBytes(StandardCharsets.UTF_8);
        final var b = "world".getBytes(StandardCharsets.UTF_8);
        assertNotEquals(FileIO.calculateChecksum(a), FileIO.calculateChecksum(b));
    }

    @Test
    void checksumEmpty() {
        final var result = FileIO.calculateChecksum(new byte[0]);
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", result);
    }

    @Test
    void checksumIsLowercaseHex() {
        final var checksum = FileIO.calculateChecksum("test".getBytes(StandardCharsets.UTF_8));
        assertTrue(checksum.matches("[0-9a-f]{64}"), "not 64 lowercase hex chars: " + checksum);
    }

    @Test
    void checksumSameContent() {
        final var content = "hello world".getBytes(StandardCharsets.UTF_8);
        assertEquals(FileIO.calculateChecksum(content), FileIO.calculateChecksum(content));
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
    void editDeleteRange() {
        final var content = "line1\nline2\nline3\nline4";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(2).endLine(3).content("").build());

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        assertNotNull(result.getNewChecksum());
        final var written = Files.readString(testFile);
        assertFalse(written.contains("line2"));
        assertFalse(written.contains("line3"));
        assertTrue(written.contains("line1"));
        assertTrue(written.contains("line4"));
    }

    @Test
    @SneakyThrows
    void editDeleteSingleLine() {
        final var content = "line1\nline2\nline3";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(2).endLine(2).content("").build());

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        assertNotNull(result.getNewChecksum());
        final var written = Files.readString(testFile);
        assertFalse(written.contains("line2"));
        assertTrue(written.contains("line1"));
        assertTrue(written.contains("line3"));
    }

    @Test
    @SneakyThrows
    void editEndLineMinusOneDeleteToEnd() {
        final var content = "line1\nline2\nline3";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(2).endLine(-1).content("")
                                          .build());

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        final var written = Files.readString(testFile);
        assertEquals("line1", written);
    }

    @Test
    @SneakyThrows
    void editEndLineMinusOneReplacesToEnd() {
        final var content = "line1\nline2\nline3\nline4";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(3).endLine(-1).content("replaced")
                                          .build());

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        assertNotNull(result.getNewChecksum());
        final var written = Files.readString(testFile);
        assertEquals("line1\nline2\nreplaced", written);
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
    void editReplaceRange() {
        final var content = "line1\nline2\nline3";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(2).endLine(3).content("replaced")
                                          .build());

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        final var written = Files.readString(testFile);
        assertTrue(written.contains("replaced"));
        assertFalse(written.contains("line2"));
        assertFalse(written.contains("line3"));
        assertTrue(written.contains("line1"));
    }

    @Test
    @SneakyThrows
    void editReplaceRangeWithMultipleLines() {
        final var content = "before\nold1\nold2\nafter";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(2).endLine(3).content("new1\nnew2\nnew3")
                                          .build());

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        final var written = Files.readString(testFile);
        assertTrue(written.contains("new1"));
        assertTrue(written.contains("new2"));
        assertTrue(written.contains("new3"));
        assertFalse(written.contains("old1"));
        assertFalse(written.contains("old2"));
        assertTrue(written.contains("before"));
        assertTrue(written.contains("after"));
    }

    @Test
    @SneakyThrows
    void editReplaceSingleLine() {
        final var content = "line1\nline2\nline3";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(2).endLine(2).content("replaced")
                                          .build());

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        assertNotNull(result.getNewChecksum());
        final var written = Files.readString(testFile);
        assertEquals("line1\nreplaced\nline3", written);
    }

    @Test
    @SneakyThrows
    void editReturnsNewChecksum() {
        final var content = "line1\nline2\nline3";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(2).endLine(2).content("changed")
                                          .build());

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        assertNotNull(result.getNewChecksum());
        final var written = Files.readString(testFile);
        assertEquals(checksum(written), result.getNewChecksum());
    }

    @Test
    @SneakyThrows
    void editTrailingNewlineStripped() {
        final var content = "line1\nline2\nline3";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(2).endLine(2).content("replaced\n")
                                          .build());

        final var result = FileIO.editFile(testFile.toString(), edits, checksum(content));

        assertNull(result.getError());
        final var written = Files.readString(testFile);
        assertEquals("line1\nreplaced\nline3", written);
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
        assertNotNull(result.getChecksum());
    }

    @Test
    @SneakyThrows
    void readWholeFileChecksumMatchesContent() {
        final var content = "hello";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);

        final var result = FileIO.readFile(testFile.toString(), 1, -1, false);

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
    void safePathAllowsPathUnderTmp() {
        final var tmpDir = Files.createTempDirectory("sai-test-");
        final var tmpFile = tmpDir.resolve("test.txt");
        try {
            final var content = "tmp content";
            final var response = FileIO.write(tmpFile.toString(), content, "");
            assertNull(response.getError());
            final var readResult = FileIO.readFile(tmpFile.toString(), 1, -1, false);
            assertNull(readResult.getError());
            assertEquals(content, readResult.getContent());
        }
        finally {
            deleteRecursive(tmpDir);
        }
    }

    @Test
    @SneakyThrows
    void safePathAllowsSubdirectoryUnderTmp() {
        final var tmpSubDir = Files.createTempDirectory("sai-subdir-");
        final var tmpSubFile = tmpSubDir.resolve("nested.txt");
        try {
            final var content = "nested tmp content";
            final var response = FileIO.write(tmpSubFile.toString(), content, "");
            assertNull(response.getError());
            final var readResult = FileIO.readFile(tmpSubFile.toString(), 1, -1, false);
            assertNull(readResult.getError());
            assertEquals(content, readResult.getContent());
        }
        finally {
            deleteRecursive(tmpSubDir);
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
    void writeBytesWrittenMatchesContentLength() {
        final var newFile = tempDir.resolve("bytes.txt");
        final var content = "abcde";

        final var response = FileIO.write(newFile.toString(), content, "");
        assertEquals(content.length(), response.getCharsWritten());
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
    void writeFileTooLargeReturnsError() {
        Files.write(testFile, new byte[1024 * 1024 + 1]);

        final var response = FileIO.write(testFile.toString(), "small content", "any");

        assertNotNull(response.getError());
        assertTrue(response.getError().contains("1 MB"), response.getError());
    }

    @Test
    @SneakyThrows
    void writeNewFile() {
        final var newFile = tempDir.resolve("new.txt");
        final var content = "hello world";

        final var response = FileIO.write(newFile.toString(), content, "");

        assertNull(response.getError());
        assertTrue(response.getCharsWritten() > 0);
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
