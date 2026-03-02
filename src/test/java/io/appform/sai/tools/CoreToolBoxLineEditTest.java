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
        coreToolBox = new CoreToolBox(null);
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

        var request = ToolIO.LineEditRequest.builder()
                .path(testFile.toAbsolutePath().toString())
                .operation(ToolIO.LineEditOperation.DELETE)
                .startLine(1)
                .expectedChecksum("wrong-checksum")
                .requestReason("Test checksum mismatch")
                .build();

        var response = coreToolBox.lineEdit(request);

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("Checksum mismatch"));
    }

    @Test
    void testDeleteMultipleLines() throws Exception {
        String content = "line1\nline2\nline3\nline4\nline5";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var request = ToolIO.LineEditRequest.builder()
                .path(testFile.toAbsolutePath().toString())
                .operation(ToolIO.LineEditOperation.DELETE)
                .startLine(2)
                .endLine(4)
                .expectedChecksum(checksum)
                .requestReason("Test delete multiple lines")
                .build();

        var response = coreToolBox.lineEdit(request);

        assertTrue(response.isSuccess());
        assertEquals("line1\nline5", Files.readString(testFile));
    }

    @Test
    void testDeleteSingleLine() throws Exception {
        String content = "line1\nline2\nline3";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var request = ToolIO.LineEditRequest.builder()
                .path(testFile.toAbsolutePath().toString())
                .operation(ToolIO.LineEditOperation.DELETE)
                .startLine(2)
                .expectedChecksum(checksum)
                .requestReason("Test delete single line")
                .build();

        var response = coreToolBox.lineEdit(request);

        assertTrue(response.isSuccess());
        assertEquals("line1\nline3", Files.readString(testFile));
    }

    @Test
    void testEndLineLessThanStartLine() throws Exception {
        String content = "line1\nline2\nline3";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var request = ToolIO.LineEditRequest.builder()
                .path(testFile.toAbsolutePath().toString())
                .operation(ToolIO.LineEditOperation.DELETE)
                .startLine(3)
                .endLine(1)
                .expectedChecksum(checksum)
                .requestReason("Test end < start")
                .build();

        var response = coreToolBox.lineEdit(request);

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("less than"));
    }

    @Test
    void testFileNotFound() {
        var request = ToolIO.LineEditRequest.builder()
                .path("/nonexistent/file.txt")
                .operation(ToolIO.LineEditOperation.DELETE)
                .startLine(1)
                .expectedChecksum("any")
                .requestReason("Test file not found")
                .build();

        var response = coreToolBox.lineEdit(request);

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("not found"));
    }

    @Test
    void testInsertAfter() throws Exception {
        String content = "line1\nline2\nline3";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var request = ToolIO.LineEditRequest.builder()
                .path(testFile.toAbsolutePath().toString())
                .operation(ToolIO.LineEditOperation.INSERT_AFTER)
                .startLine(2)
                .content("inserted line")
                .expectedChecksum(checksum)
                .requestReason("Test insert after")
                .build();

        var response = coreToolBox.lineEdit(request);

        assertTrue(response.isSuccess());
        assertEquals("line1\nline2\ninserted line\nline3", Files.readString(testFile));
    }

    @Test
    void testInsertAfterLastLine() throws Exception {
        String content = "line1\nline2";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var request = ToolIO.LineEditRequest.builder()
                .path(testFile.toAbsolutePath().toString())
                .operation(ToolIO.LineEditOperation.INSERT_AFTER)
                .startLine(2)
                .content("new last line")
                .expectedChecksum(checksum)
                .requestReason("Test insert after last line")
                .build();

        var response = coreToolBox.lineEdit(request);

        assertTrue(response.isSuccess());
        assertEquals("line1\nline2\nnew last line", Files.readString(testFile));
    }

    @Test
    void testInsertBefore() throws Exception {
        String content = "line1\nline2\nline3";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var request = ToolIO.LineEditRequest.builder()
                .path(testFile.toAbsolutePath().toString())
                .operation(ToolIO.LineEditOperation.INSERT_BEFORE)
                .startLine(2)
                .content("inserted line")
                .expectedChecksum(checksum)
                .requestReason("Test insert before")
                .build();

        var response = coreToolBox.lineEdit(request);

        assertTrue(response.isSuccess());
        assertEquals("line1\ninserted line\nline2\nline3", Files.readString(testFile));
    }

    @Test
    void testInsertBeforeFirstLine() throws Exception {
        String content = "line1\nline2";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var request = ToolIO.LineEditRequest.builder()
                .path(testFile.toAbsolutePath().toString())
                .operation(ToolIO.LineEditOperation.INSERT_BEFORE)
                .startLine(1)
                .content("new first line")
                .expectedChecksum(checksum)
                .requestReason("Test insert before first line")
                .build();

        var response = coreToolBox.lineEdit(request);

        assertTrue(response.isSuccess());
        assertEquals("new first line\nline1\nline2", Files.readString(testFile));
    }

    @Test
    void testInsertMultipleLines() throws Exception {
        String content = "line1\nline3";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var request = ToolIO.LineEditRequest.builder()
                .path(testFile.toAbsolutePath().toString())
                .operation(ToolIO.LineEditOperation.INSERT_AFTER)
                .startLine(1)
                .content("line2a\nline2b")
                .expectedChecksum(checksum)
                .requestReason("Test insert multiple lines")
                .build();

        var response = coreToolBox.lineEdit(request);

        assertTrue(response.isSuccess());
        assertEquals("line1\nline2a\nline2b\nline3", Files.readString(testFile));
    }

    @Test
    void testInvalidLineNumber() throws Exception {
        String content = "line1\nline2";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var request = ToolIO.LineEditRequest.builder()
                .path(testFile.toAbsolutePath().toString())
                .operation(ToolIO.LineEditOperation.DELETE)
                .startLine(0)  // Invalid - should be >= 1
                .expectedChecksum(checksum)
                .requestReason("Test invalid line number")
                .build();

        var response = coreToolBox.lineEdit(request);

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains(">= 1"));
    }

    @Test
    void testLineNumberOutOfBounds() throws Exception {
        String content = "line1\nline2";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var request = ToolIO.LineEditRequest.builder()
                .path(testFile.toAbsolutePath().toString())
                .operation(ToolIO.LineEditOperation.DELETE)
                .startLine(10)
                .expectedChecksum(checksum)
                .requestReason("Test out of bounds")
                .build();

        var response = coreToolBox.lineEdit(request);

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("beyond"));
    }

    @Test
    void testReplaceMultipleLines() throws Exception {
        String content = "line1\nold1\nold2\nold3\nline5";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var request = ToolIO.LineEditRequest.builder()
                .path(testFile.toAbsolutePath().toString())
                .operation(ToolIO.LineEditOperation.REPLACE)
                .startLine(2)
                .endLine(4)
                .content("new line")
                .expectedChecksum(checksum)
                .requestReason("Test replace multiple lines with single")
                .build();

        var response = coreToolBox.lineEdit(request);

        assertTrue(response.isSuccess());
        assertEquals("line1\nnew line\nline5", Files.readString(testFile));
    }

    @Test
    void testReplaceSingleLine() throws Exception {
        String content = "line1\nold line\nline3";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var request = ToolIO.LineEditRequest.builder()
                .path(testFile.toAbsolutePath().toString())
                .operation(ToolIO.LineEditOperation.REPLACE)
                .startLine(2)
                .content("new line")
                .expectedChecksum(checksum)
                .requestReason("Test replace single line")
                .build();

        var response = coreToolBox.lineEdit(request);

        assertTrue(response.isSuccess());
        assertEquals("line1\nnew line\nline3", Files.readString(testFile));
    }

    @Test
    void testReplaceWithMultipleLines() throws Exception {
        String content = "line1\nold\nline3";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var request = ToolIO.LineEditRequest.builder()
                .path(testFile.toAbsolutePath().toString())
                .operation(ToolIO.LineEditOperation.REPLACE)
                .startLine(2)
                .content("new1\nnew2\nnew3")
                .expectedChecksum(checksum)
                .requestReason("Test replace single line with multiple")
                .build();

        var response = coreToolBox.lineEdit(request);

        assertTrue(response.isSuccess());
        assertEquals("line1\nnew1\nnew2\nnew3\nline3", Files.readString(testFile));
    }

    private String calculateChecksum(String content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
