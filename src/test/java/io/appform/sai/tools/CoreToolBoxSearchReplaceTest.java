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
    void testSearchReplaceAll() throws Exception {
        String content = "Hello world! Hello again! Hello once more!";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.searchReplace(testFile.toAbsolutePath().toString(),
                                                 "Hello",
                                                 "Hi",
                                                 0, // Replace all
                                                 checksum,
                                                 "Test replace all");

        assertTrue(response.isSuccess());
        assertEquals(3, response.getReplacementCount());
        assertEquals("Hi world! Hi again! Hi once more!", Files.readString(testFile));
    }

    @Test
    void testSearchReplaceChecksumMismatch() throws Exception {
        String content = "Hello world!";
        Files.writeString(testFile, content);

        var response = coreToolBox.searchReplace(testFile.toAbsolutePath().toString(),
                                                 "Hello",
                                                 "Hi",
                                                 1,
                                                 "wrong-checksum",
                                                 "Test checksum mismatch");

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("Checksum mismatch"));
    }

    @Test
    void testSearchReplaceEmptySearchText() throws Exception {
        String content = "Hello world!";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.searchReplace(testFile.toAbsolutePath().toString(),
                                                 "",
                                                 "Hi",
                                                 1,
                                                 checksum,
                                                 "Test empty search text");

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("empty"));
    }

    @Test
    void testSearchReplaceFileNotFound() {
        var response = coreToolBox.searchReplace("/nonexistent/file.txt",
                                                 "Hello",
                                                 "Hi",
                                                 1,
                                                 "any",
                                                 "Test file not found");

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("not found"));
    }

    @Test
    void testSearchReplaceFirstOccurrence() throws Exception {
        String content = "foo bar foo baz foo";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.searchReplace(testFile.toAbsolutePath().toString(),
                                                 "foo",
                                                 "qux",
                                                 1, // Replace first occurrence
                                                 checksum,
                                                 "Test replace first occurrence");

        assertTrue(response.isSuccess());
        assertEquals(1, response.getReplacementCount());
        assertEquals("qux bar foo baz foo", Files.readString(testFile));
    }

    @Test
    void testSearchReplaceMultiLine() throws Exception {
        String content = "line1\nold code\nline3";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.searchReplace(testFile.toAbsolutePath().toString(),
                                                 "old code",
                                                 "new code\nextra line",
                                                 1,
                                                 checksum,
                                                 "Test multiline replace");

        assertTrue(response.isSuccess());
        assertEquals("line1\nnew code\nextra line\nline3", Files.readString(testFile));
    }

    @Test
    void testSearchReplaceOccurrenceNotFound() throws Exception {
        String content = "apple banana";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.searchReplace(testFile.toAbsolutePath().toString(),
                                                 "apple",
                                                 "orange",
                                                 5, // Only 1 occurrence exists
                                                 checksum,
                                                 "Test occurrence not found");

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("Occurrence"));
    }

    @Test
    void testSearchReplaceSpecificOccurrence() throws Exception {
        String content = "apple banana apple cherry apple";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.searchReplace(testFile.toAbsolutePath().toString(),
                                                 "apple",
                                                 "orange",
                                                 2, // Replace second occurrence
                                                 checksum,
                                                 "Test replace specific occurrence");

        assertTrue(response.isSuccess());
        assertEquals(1, response.getReplacementCount());
        assertEquals("apple banana orange cherry apple", Files.readString(testFile));
    }

    @Test
    void testSearchReplaceTextNotFound() throws Exception {
        String content = "Hello world!";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.searchReplace(testFile.toAbsolutePath().toString(),
                                                 "Goodbye",
                                                 "Hi",
                                                 1,
                                                 checksum,
                                                 "Test text not found");

        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("not found"));
    }

    @Test
    void testSearchReplaceWithDelete() throws Exception {
        String content = "keep this remove_this keep this too";
        Files.writeString(testFile, content);
        String checksum = calculateChecksum(content);

        var response = coreToolBox.searchReplace(testFile.toAbsolutePath().toString(),
                                                 "remove_this ",
                                                 "", // Delete by replacing with empty
                                                 1,
                                                 checksum,
                                                 "Test delete via replace");

        assertTrue(response.isSuccess());
        assertEquals("keep this keep this too", Files.readString(testFile));
    }

    private String calculateChecksum(String content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
