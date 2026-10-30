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

import io.appform.sai.term.Printer;

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

class CoreToolBoxSearchReplaceTest {

    private CoreToolBox coreToolBox;
    private Path testFile;

    @BeforeEach
    void setUp() throws IOException {
        coreToolBox = new CoreToolBox((Printer) null);
        testFile = Files.createTempFile("search-replace-test-", ".txt");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(testFile);
    }

    @Test
    @SneakyThrows
    void testSearchReplaceAll() {
        String content = "Hello world! Hello again! Hello once more!";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.searchReplace(testFile.toAbsolutePath().toString(),
                                                 "Hello",
                                                 "Hi",
                                                 true,
                                                 checksum,
                                                 "Test replace all",
                                                 null);

        assertTrue(response.isSuccess());
        assertEquals(3, response.getReplacementCount());
        assertEquals("Hi world! Hi again! Hi once more!", Files.readString(testFile));
    }

    @Test
    @SneakyThrows
    void testSearchReplaceChecksumMismatchDoesNotLeakChecksum() {
        String content = "Hello world!";
        Files.writeString(testFile, content);

        var response = coreToolBox.searchReplace(testFile.toAbsolutePath().toString(),
                                                 "Hello",
                                                 "Hi",
                                                 false,
                                                 "wrong-checksum",
                                                 "Test checksum mismatch",
                                                 null);

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("Checksum mismatch"));
        // The error must not print the actual checksum: the model should re-read, not echo it back.
        assertFalse(response.getError().contains("Actual"));
        assertEquals(content, Files.readString(testFile));
    }

    @Test
    @SneakyThrows
    void testSearchReplaceEmptySearchText() {
        String content = "Hello world!";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.searchReplace(testFile.toAbsolutePath().toString(),
                                                 "",
                                                 "Hi",
                                                 false,
                                                 checksum,
                                                 "Test empty search text",
                                                 null);

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("cannot be empty"));
    }

    @Test
    void testSearchReplaceFileNotFound() {
        var response = coreToolBox.searchReplace("/nonexistent/file.txt",
                                                 "Hello",
                                                 "Hi",
                                                 false,
                                                 "any",
                                                 "Test file not found",
                                                 null);

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("not found"));
    }

    @Test
    @SneakyThrows
    void testSearchReplaceMissingChecksumReturnsDistinctError() {
        String content = "Hello world!";
        Files.writeString(testFile, content);

        var response = coreToolBox.searchReplace(testFile.toAbsolutePath().toString(),
                                                 "Hello",
                                                 "Hi",
                                                 false,
                                                 null,
                                                 "Test missing checksum",
                                                 null);

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("expectedChecksum"), response.getError());
        assertFalse(response.getError().contains("Checksum mismatch"), response.getError());
        assertEquals(content, Files.readString(testFile));
    }

    @Test
    @SneakyThrows
    void testSearchReplaceMultiLine() {
        String content = "line1\nold code\nline3";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.searchReplace(testFile.toAbsolutePath().toString(),
                                                 "old code",
                                                 "new code\nextra line",
                                                 false,
                                                 checksum,
                                                 "Test multiline replace",
                                                 null);

        assertTrue(response.isSuccess());
        assertEquals("line1\nnew code\nextra line\nline3", Files.readString(testFile));
    }

    @Test
    @SneakyThrows
    void testSearchReplaceMultipleMatchesRejectedWithoutReplaceAll() {
        String content = "apple banana apple cherry apple";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.searchReplace(testFile.toAbsolutePath().toString(),
                                                 "apple",
                                                 "orange",
                                                 false,
                                                 checksum,
                                                 "Test multiple matches",
                                                 null);

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("3 matches"));
        assertEquals(content, Files.readString(testFile));
    }

    @Test
    @SneakyThrows
    void testSearchReplaceOverlappingMatches() {
        // "aa" appears at indexes 0 and 1 in "aaa" when overlaps count.
        // The tool must count non-overlapping matches only: 1 match.
        String content = "aaa";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.searchReplace(testFile.toAbsolutePath().toString(),
                                                 "aa",
                                                 "b",
                                                 false,
                                                 checksum,
                                                 "Test overlapping matches",
                                                 null);

        assertTrue(response.isSuccess());
        assertEquals(1, response.getReplacementCount());
        assertEquals("ba", Files.readString(testFile));
    }

    @Test
    @SneakyThrows
    void testSearchReplaceRefusesToEmptyTheFile() {
        String content = "only this";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.searchReplace(testFile.toAbsolutePath().toString(),
                                                 "only this",
                                                 "",
                                                 false,
                                                 checksum,
                                                 "Test refuse empty result",
                                                 null);

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("empty"));
        assertEquals(content, Files.readString(testFile));
    }

    @Test
    @SneakyThrows
    void testSearchReplaceTextNotFound() {
        String content = "Hello world!";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.searchReplace(testFile.toAbsolutePath().toString(),
                                                 "Goodbye",
                                                 "Hi",
                                                 false,
                                                 checksum,
                                                 "Test text not found",
                                                 null);

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("not found"));
    }

    @Test
    @SneakyThrows
    void testSearchReplaceUniqueMatch() {
        String content = "line1\nold code\nline3";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.searchReplace(testFile.toAbsolutePath().toString(),
                                                 "old code",
                                                 "new code",
                                                 false,
                                                 checksum,
                                                 "Test unique match",
                                                 null);

        assertTrue(response.isSuccess());
        assertEquals(1, response.getReplacementCount());
        assertEquals("line1\nnew code\nline3", Files.readString(testFile));
        assertTrue(response.getDiff().contains("- old code"));
        assertTrue(response.getDiff().contains("+ new code"));
    }

    @Test
    @SneakyThrows
    void testSearchReplaceWithDelete() {
        String content = "keep this remove_this keep this too";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.searchReplace(testFile.toAbsolutePath().toString(),
                                                 "remove_this ",
                                                 "",
                                                 false,
                                                 checksum,
                                                 "Test delete via replace",
                                                 null);

        assertTrue(response.isSuccess());
        assertEquals("keep this keep this too", Files.readString(testFile));
    }

    @SneakyThrows
    private String calculateChecksum(String content) {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
