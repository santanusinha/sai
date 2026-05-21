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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.appform.sai.Printer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for the read() method in CoreToolBox, particularly the checksum-based
 * change detection logic introduced to optimize file reads.
 */
class CoreToolBoxReadTest {

    private CoreToolBox toolBox;
    private Path tempDir;
    private Path testFile;

    @Test
    void emptyChecksumForcesReReadEvenWhenUnchanged() throws IOException {
        Files.writeString(testFile, "Unchanged content", StandardCharsets.UTF_8);
        final var firstRead = toolBox.readFile("first read", testFile.toString(), "");
        final var checksum = firstRead.getChecksum();

        // Verify file is unchanged with correct checksum
        final var unchanged = toolBox.readFile("check unchanged", testFile.toString(), checksum);
        assertFalse(unchanged.isChanged());
        assertNull(unchanged.getContent());

        // Force re-read with empty checksum — should return content even though file is unchanged
        final var forced = toolBox.readFile("force re-read", testFile.toString(), "");
        assertTrue(forced.isChanged());
        assertNotNull(forced.getContent());
        assertTrue(forced.getContent().contains("Unchanged content"), forced.getContent());
        assertEquals(checksum, forced.getChecksum());
    }

    @Test
    void firstReadEmptyChecksum() throws IOException {
        final var content = "Hello, World!";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);

        final var response = toolBox.readFile("test first read", testFile.toString(), "");

        assertNull(response.getError());
        assertTrue(response.getContent().contains(content), response.getContent());
        assertTrue(response.getContent().startsWith("     1\t"), response.getContent());
        assertNotNull(response.getChecksum());
        assertTrue(response.isChanged());
    }

    @Test
    void firstReadNullChecksum() throws IOException {
        final var content = "Hello, World!";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);

        final var response = toolBox.readFile("test first read", testFile.toString(), null);

        assertNull(response.getError());
        assertTrue(response.getContent().contains(content), response.getContent());
        assertTrue(response.getContent().startsWith("     1\t"), response.getContent());
        assertNotNull(response.getChecksum());
        assertTrue(response.isChanged());
    }

    @Test
    void nullChecksumForcesReReadEvenWhenUnchanged() throws IOException {
        Files.writeString(testFile, "Unchanged content", StandardCharsets.UTF_8);
        final var firstRead = toolBox.readFile("first read", testFile.toString(), "");
        final var checksum = firstRead.getChecksum();

        // Verify file is unchanged with correct checksum
        final var unchanged = toolBox.readFile("check unchanged", testFile.toString(), checksum);
        assertFalse(unchanged.isChanged());
        assertNull(unchanged.getContent());

        // Force re-read with null checksum — should return content even though file is unchanged
        final var forced = toolBox.readFile("force re-read", testFile.toString(), null);
        assertTrue(forced.isChanged());
        assertNotNull(forced.getContent());
        assertTrue(forced.getContent().contains("Unchanged content"), forced.getContent());
        assertEquals(checksum, forced.getChecksum());
    }

    @Test
    void reReadDeletedFileReturnsError() throws IOException {
        Files.writeString(testFile, "Hello, World!", StandardCharsets.UTF_8);
        final var checksum = toolBox.readFile("first read", testFile.toString(), "").getChecksum();
        Files.delete(testFile);

        final var response = toolBox.readFile("second read", testFile.toString(), checksum);

        assertNotNull(response.getError());
        assertTrue(response.getError().contains("File not found"), response.getError());
        assertNull(response.getContent());
    }

    @Test
    void reReadDetectsModifications() throws IOException {
        Files.writeString(testFile, "Version 1", StandardCharsets.UTF_8);
        final var checksum1 = toolBox.readFile("read 1", testFile.toString(), "").getChecksum();

        Files.writeString(testFile, "Version 2", StandardCharsets.UTF_8);
        final var read2 = toolBox.readFile("read 2", testFile.toString(), checksum1);
        final var checksum2 = read2.getChecksum();

        assertTrue(read2.isChanged());
        assertTrue(read2.getContent().contains("Version 2"), read2.getContent());

        final var read3 = toolBox.readFile("read 3", testFile.toString(), checksum2);
        assertFalse(read3.isChanged());
        assertNull(read3.getContent());

        Files.writeString(testFile, "Version 3", StandardCharsets.UTF_8);
        final var read4 = toolBox.readFile("read 4", testFile.toString(), checksum2);

        assertTrue(read4.isChanged());
        assertTrue(read4.getContent().contains("Version 3"), read4.getContent());
    }

    @Test
    void reReadEmptyFileUnchanged() throws IOException {
        Files.writeString(testFile, "", StandardCharsets.UTF_8);

        final var firstRead = toolBox.readFile("first read", testFile.toString(), "");

        assertNull(firstRead.getError());
        assertEquals("", firstRead.getContent());
        assertNotNull(firstRead.getChecksum());
        assertTrue(firstRead.isChanged());

        final var secondRead = toolBox.readFile("second read", testFile.toString(), firstRead.getChecksum());

        assertNull(secondRead.getError());
        assertNull(secondRead.getContent());
        assertFalse(secondRead.isChanged());
    }

    @Test
    void reReadModifiedFileReturnsNewContent() throws IOException {
        Files.writeString(testFile, "Hello, World!", StandardCharsets.UTF_8);
        final var originalChecksum = toolBox.readFile("first read", testFile.toString(), "").getChecksum();

        Files.writeString(testFile, "Hello, Modified World!", StandardCharsets.UTF_8);
        final var response = toolBox.readFile("second read", testFile.toString(), originalChecksum);

        assertNull(response.getError());
        assertTrue(response.getContent().contains("Hello, Modified World!"), response.getContent());
        assertNotNull(response.getChecksum());
        assertTrue(response.isChanged());
        assertFalse(originalChecksum.equals(response.getChecksum()));
    }

    @Test
    void reReadUnchangedFileMultipleTimes() throws IOException {
        Files.writeString(testFile, "Persistent content", StandardCharsets.UTF_8);
        final var checksum = toolBox.readFile("first read", testFile.toString(), "").getChecksum();

        final var second = toolBox.readFile("second read", testFile.toString(), checksum);
        final var third = toolBox.readFile("third read", testFile.toString(), checksum);
        final var fourth = toolBox.readFile("fourth read", testFile.toString(), checksum);

        assertFalse(second.isChanged());
        assertFalse(third.isChanged());
        assertFalse(fourth.isChanged());
        assertEquals(checksum, second.getChecksum());
        assertEquals(checksum, third.getChecksum());
        assertEquals(checksum, fourth.getChecksum());
        assertNull(second.getContent());
        assertNull(third.getContent());
        assertNull(fourth.getContent());
    }

    @Test
    void reReadUnchangedFileReturnsNoContent() throws IOException {
        Files.writeString(testFile, "Hello, World!", StandardCharsets.UTF_8);
        final var checksum = toolBox.readFile("first read", testFile.toString(), "").getChecksum();

        final var response = toolBox.readFile("second read", testFile.toString(), checksum);

        assertNull(response.getError());
        assertNull(response.getContent());
        assertEquals(checksum, response.getChecksum());
        assertFalse(response.isChanged());
    }

    @Test
    void reReadWrongChecksumReturnsContent() throws IOException {
        final var content = "Hello, World!";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);
        final var fakeChecksum = "0000000000000000000000000000000000000000000000000000000000000000";

        final var response = toolBox.readFile("read with wrong checksum", testFile.toString(), fakeChecksum);

        assertNull(response.getError());
        assertTrue(response.getContent().contains(content), response.getContent());
        assertNotNull(response.getChecksum());
        assertTrue(response.isChanged());
        assertFalse(fakeChecksum.equals(response.getChecksum()));
    }

    @BeforeEach
    void setUp() throws IOException {
        toolBox = new CoreToolBox((Printer) null);
        tempDir = Files.createTempDirectory("coretoolbox-read-test");
        testFile = tempDir.resolve("test.txt");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(testFile)) {
            Files.delete(testFile);
        }
        Files.delete(tempDir);
    }
}
