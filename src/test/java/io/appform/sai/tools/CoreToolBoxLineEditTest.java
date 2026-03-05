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

class CoreToolBoxLineEditTest {

    private CoreToolBox coreToolBox;
    private Path testFile;

    @BeforeEach
    void setUp() throws IOException {
        coreToolBox = new CoreToolBox((Printer) null);
        testFile = Files.createTempFile("line-edit-test-", ".txt");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(testFile);
    }

    @Test
    void testChecksumMismatch() throws Exception {
        String content = "line1\nline2";
        Files.writeString(testFile, content);

        var response = coreToolBox.lineEdit("Test checksum mismatch",
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
    void testDeleteMultipleLines() throws Exception {
        String content = "line1\nline2\nline3\nline4\nline5";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.lineEdit("Test delete multiple lines",
                                            testFile.toAbsolutePath().toString(),
                                            ToolIO.LineEditOperation.DELETE,
                                            2,
                                            4,
                                            null,
                                            checksum);

        assertTrue(response.isSuccess());
        assertEquals("line1\nline5", Files.readString(testFile));
    }

    @Test
    void testDeleteSingleLine() throws Exception {
        String content = "line1\nline2\nline3";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.lineEdit("Test delete single line",
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
    void testEndLineLessThanStartLine() throws Exception {
        String content = "line1\nline2\nline3";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.lineEdit("Test end < start",
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
    void testFileNotFound() {
        var response = coreToolBox.lineEdit("Test file not found",
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
    void testInsertAfter() throws Exception {
        String content = "line1\nline2\nline3";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.lineEdit("Test insert after",
                                            testFile.toAbsolutePath().toString(),
                                            ToolIO.LineEditOperation.INSERT_AFTER,
                                            2,
                                            null,
                                            "inserted line",
                                            checksum);

        assertTrue(response.isSuccess());
        assertEquals("line1\nline2\ninserted line\nline3", Files.readString(testFile));
    }

    @Test
    void testInsertAfterLastLine() throws Exception {
        String content = "line1\nline2";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.lineEdit("Test insert after last line",
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
    void testInsertBefore() throws Exception {
        String content = "line1\nline2\nline3";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.lineEdit("Test insert before",
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
    void testInsertBeforeFirstLine() throws Exception {
        String content = "line1\nline2";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.lineEdit("Test insert before first line",
                                            testFile.toAbsolutePath().toString(),
                                            ToolIO.LineEditOperation.INSERT_BEFORE,
                                            1,
                                            null,
                                            "new first line",
                                            checksum);

        assertTrue(response.isSuccess());
        assertEquals("new first line\nline1\nline2", Files.readString(testFile));
    }

    @Test
    void testInsertMultipleLines() throws Exception {
        String content = "line1\nline3";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.lineEdit("Test insert multiple lines",
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
    void testInvalidLineNumber() throws Exception {
        String content = "line1\nline2";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.lineEdit("Test invalid line number",
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
    void testLineNumberOutOfBounds() throws Exception {
        String content = "line1\nline2";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.lineEdit("Test out of bounds",
                                            testFile.toAbsolutePath().toString(),
                                            ToolIO.LineEditOperation.DELETE,
                                            10,
                                            null,
                                            null,
                                            checksum);

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("beyond"));
    }

    @Test
    void testReplaceMultipleLines() throws Exception {
        String content = "line1\nold1\nold2\nold3\nline5";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.lineEdit("Test replace multiple lines with single",
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
    void testReplaceSingleLine() throws Exception {
        String content = "line1\nold line\nline3";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.lineEdit("Test replace single line",
                                            testFile.toAbsolutePath().toString(),
                                            ToolIO.LineEditOperation.REPLACE,
                                            2,
                                            null,
                                            "new line",
                                            checksum);

        assertTrue(response.isSuccess());
        assertEquals("line1\nnew line\nline3", Files.readString(testFile));
    }

    @Test
    void testReplaceWithMultipleLines() throws Exception {
        String content = "line1\nold\nline3";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.lineEdit("Test replace single line with multiple",
                                            testFile.toAbsolutePath().toString(),
                                            ToolIO.LineEditOperation.REPLACE,
                                            2,
                                            null,
                                            "new1\nnew2\nnew3",
                                            checksum);

        assertTrue(response.isSuccess());
        assertEquals("line1\nnew1\nnew2\nnew3\nline3", Files.readString(testFile));
    }

    private String calculateChecksum(String content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
