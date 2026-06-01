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
package io.appform.sai.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.appform.sai.Printer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import lombok.SneakyThrows;

/**
 * Unit tests for the {@code lineEdit} operation in {@link CoreToolBox}.
 *
 * <p>Covers the four edit modes (DELETE, INSERT_BEFORE, INSERT_AFTER, REPLACE) and the defensive
 * error paths (checksum mismatch, invalid line numbers, file not found, end-before-start).
 */
class CoreToolBoxLineEditTest {

    private CoreToolBox coreToolBox;
    private Path testFile;

    @Test
    @SneakyThrows
    void checksumMismatchReturnsError() {
        final var content = "line1\nline2";
        Files.writeString(testFile, content);

        final var response = coreToolBox.lineEdit("Test checksum mismatch",
                                                  testFile.toAbsolutePath().toString(),
                                                  ToolIO.LineEditOperation.DELETE,
                                                  1,
                                                  null,
                                                  null,
                                                  "wrong-checksum");

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("Checksum mismatch"));
    }

    @Test
    @SneakyThrows
    void deletingMultipleLinesSucceeds() {
        final var content = "line1\nline2\nline3\nline4\nline5";
        Files.writeString(testFile, content);
        final var checksum = calculateChecksum(content);

        final var response = coreToolBox.lineEdit("Test delete multiple lines",
                                                  testFile.toAbsolutePath().toString(),
                                                  ToolIO.LineEditOperation.DELETE,
                                                  2,
                                                  4,
                                                  null,
                                                  checksum);

        assertTrue(response.isSuccess());
        assertEquals("line1\nline5", Files.readString(testFile));
    }

    // ===== Guard / error-path tests =====

    @Test
    @SneakyThrows
    void deletingSingleLineSucceeds() {
        final var content = "line1\nline2\nline3";
        Files.writeString(testFile, content);
        final var checksum = calculateChecksum(content);

        final var response = coreToolBox.lineEdit("Test delete single line",
                                                  testFile.toAbsolutePath().toString(),
                                                  ToolIO.LineEditOperation.DELETE,
                                                  2,
                                                  null,
                                                  null,
                                                  checksum);

        assertTrue(response.isSuccess());
        assertEquals("line1\nline3", Files.readString(testFile));
    }

    @Test
    @SneakyThrows
    void endLineLessThanStartLineReturnsError() {
        final var content = "line1\nline2\nline3";
        Files.writeString(testFile, content);
        final var checksum = calculateChecksum(content);

        final var response = coreToolBox.lineEdit("Test end < start",
                                                  testFile.toAbsolutePath().toString(),
                                                  ToolIO.LineEditOperation.DELETE,
                                                  3,
                                                  1,
                                                  null,
                                                  checksum);

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("less than"));
    }

    @Test
    void fileNotFoundReturnsError() {
        final var response = coreToolBox.lineEdit("Test file not found",
                                                  "/nonexistent/file.txt",
                                                  ToolIO.LineEditOperation.DELETE,
                                                  1,
                                                  null,
                                                  null,
                                                  "any");

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("not found"));
    }

    @Test
    @SneakyThrows
    void insertAfterLastLineSucceeds() {
        final var content = "line1\nline2";
        Files.writeString(testFile, content);
        final var checksum = calculateChecksum(content);

        final var response = coreToolBox.lineEdit("Test insert after last line",
                                                  testFile.toAbsolutePath().toString(),
                                                  ToolIO.LineEditOperation.INSERT_AFTER,
                                                  2,
                                                  null,
                                                  "new last line",
                                                  checksum);

        assertTrue(response.isSuccess());
        assertEquals("line1\nline2\nnew last line", Files.readString(testFile));
    }

    @Test
    @SneakyThrows
    void insertAfterMiddleLineSucceeds() {
        final var content = "line1\nline2\nline3";
        Files.writeString(testFile, content);
        final var checksum = calculateChecksum(content);

        final var response = coreToolBox.lineEdit("Test insert after",
                                                  testFile.toAbsolutePath().toString(),
                                                  ToolIO.LineEditOperation.INSERT_AFTER,
                                                  2,
                                                  null,
                                                  "inserted line",
                                                  checksum);

        assertTrue(response.isSuccess());
        assertEquals("line1\nline2\ninserted line\nline3", Files.readString(testFile));
    }

    // ===== DELETE tests =====

    @Test
    @SneakyThrows
    void insertAfterMultipleLinesSucceeds() {
        final var content = "line1\nline3";
        Files.writeString(testFile, content);
        final var checksum = calculateChecksum(content);

        final var response = coreToolBox.lineEdit("Test insert multiple lines",
                                                  testFile.toAbsolutePath().toString(),
                                                  ToolIO.LineEditOperation.INSERT_AFTER,
                                                  1,
                                                  null,
                                                  "line2a\nline2b",
                                                  checksum);

        assertTrue(response.isSuccess());
        assertEquals("line1\nline2a\nline2b\nline3", Files.readString(testFile));
    }

    @Test
    @SneakyThrows
    void insertBeforeFirstLineSucceeds() {
        final var content = "line1\nline2";
        Files.writeString(testFile, content);
        final var checksum = calculateChecksum(content);

        final var response = coreToolBox.lineEdit("Test insert before first line",
                                                  testFile.toAbsolutePath().toString(),
                                                  ToolIO.LineEditOperation.INSERT_BEFORE,
                                                  1,
                                                  null,
                                                  "new first line",
                                                  checksum);

        assertTrue(response.isSuccess());
        assertEquals("new first line\nline1\nline2", Files.readString(testFile));
    }

    // ===== INSERT_AFTER tests =====

    @Test
    @SneakyThrows
    void insertBeforeMiddleLineSucceeds() {
        final var content = "line1\nline2\nline3";
        Files.writeString(testFile, content);
        final var checksum = calculateChecksum(content);

        final var response = coreToolBox.lineEdit("Test insert before",
                                                  testFile.toAbsolutePath().toString(),
                                                  ToolIO.LineEditOperation.INSERT_BEFORE,
                                                  2,
                                                  null,
                                                  "inserted line",
                                                  checksum);

        assertTrue(response.isSuccess());
        assertEquals("line1\ninserted line\nline2\nline3", Files.readString(testFile));
    }

    @Test
    @SneakyThrows
    void invalidLineNumberReturnsError() {
        final var content = "line1\nline2";
        Files.writeString(testFile, content);
        final var checksum = calculateChecksum(content);

        final var response = coreToolBox.lineEdit("Test invalid line number",
                                                  testFile.toAbsolutePath().toString(),
                                                  ToolIO.LineEditOperation.DELETE,
                                                  0,
                                                  null,
                                                  null,
                                                  checksum);

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains(">= 1"));
    }

    @Test
    @SneakyThrows
    void lineNumberOutOfBoundsReturnsError() {
        final var content = "line1\nline2";
        Files.writeString(testFile, content);
        final var checksum = calculateChecksum(content);

        final var response = coreToolBox.lineEdit("Test out of bounds",
                                                  testFile.toAbsolutePath().toString(),
                                                  ToolIO.LineEditOperation.DELETE,
                                                  10,
                                                  null,
                                                  null,
                                                  checksum);

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("beyond"));
    }

    // ===== INSERT_BEFORE tests =====

    @Test
    @SneakyThrows
    void replacingMultipleLinesWithSingleLineSucceeds() {
        final var content = "line1\nold1\nold2\nold3\nline5";
        Files.writeString(testFile, content);
        final var checksum = calculateChecksum(content);

        final var response = coreToolBox.lineEdit("Test replace multiple lines with single",
                                                  testFile.toAbsolutePath().toString(),
                                                  ToolIO.LineEditOperation.REPLACE,
                                                  2,
                                                  4,
                                                  "new line",
                                                  checksum);

        assertTrue(response.isSuccess());
        assertEquals("line1\nnew line\nline5", Files.readString(testFile));
    }

    @Test
    @SneakyThrows
    void replacingSingleLineSucceeds() {
        final var content = "line1\nold line\nline3";
        Files.writeString(testFile, content);
        final var checksum = calculateChecksum(content);

        final var response = coreToolBox.lineEdit("Test replace single line",
                                                  testFile.toAbsolutePath().toString(),
                                                  ToolIO.LineEditOperation.REPLACE,
                                                  2,
                                                  null,
                                                  "new line",
                                                  checksum);

        assertTrue(response.isSuccess());
        assertEquals("line1\nnew line\nline3", Files.readString(testFile));
    }

    // ===== REPLACE tests =====

    @Test
    @SneakyThrows
    void replacingSingleLineWithMultipleLinesSucceeds() {
        final var content = "line1\nold\nline3";
        Files.writeString(testFile, content);
        final var checksum = calculateChecksum(content);

        final var response = coreToolBox.lineEdit("Test replace single line with multiple",
                                                  testFile.toAbsolutePath().toString(),
                                                  ToolIO.LineEditOperation.REPLACE,
                                                  2,
                                                  null,
                                                  "new1\nnew2\nnew3",
                                                  checksum);

        assertTrue(response.isSuccess());
        assertEquals("line1\nnew1\nnew2\nnew3\nline3", Files.readString(testFile));
    }

    @BeforeEach
    void setUp() throws IOException {
        coreToolBox = new CoreToolBox((Printer) null);
        testFile = Files.createTempFile("line-edit-test-", ".txt");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(testFile);
    }

    @SneakyThrows
    private String calculateChecksum(String content) {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        final byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
