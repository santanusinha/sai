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

    @Test
    void testFirstRead_NoKnownChecksum_ReturnsContentAndChanged() throws IOException {
        // Arrange
        String content = "Hello, World!";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);

        // Act - First read with empty checksum
        ToolIO.ReadResponse response = toolBox.readFile("test first read", testFile.toString(), "");

        // Assert
        assertNull(response.getError(), "Should not have error");
        assertEquals(content, response.getContent(), "Should return file content");
        assertNotNull(response.getChecksum(), "Should return checksum");
        assertTrue(response.isChanged(), "Should mark as changed when no known checksum provided");
    }

    @Test
    void testFirstRead_NullKnownChecksum_ReturnsContentAndChanged() throws IOException {
        // Arrange
        String content = "Hello, World!";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);

        // Act - First read with null checksum
        ToolIO.ReadResponse response = toolBox.readFile("test first read", testFile.toString(), null);

        // Assert
        assertNull(response.getError(), "Should not have error");
        assertEquals(content, response.getContent(), "Should return file content");
        assertNotNull(response.getChecksum(), "Should return checksum");
        assertTrue(response.isChanged(), "Should mark as changed when null checksum provided");
    }

    @Test
    void testReRead_EmptyFile_WorksCorrectly() throws IOException {
        // Arrange - Create empty file
        Files.writeString(testFile, "", StandardCharsets.UTF_8);

        // Act - First read
        ToolIO.ReadResponse firstRead = toolBox.readFile("first read", testFile.toString(), "");

        // Assert first read
        assertNull(firstRead.getError(), "Should not have error");
        assertEquals("", firstRead.getContent(), "Should return empty content");
        assertNotNull(firstRead.getChecksum(), "Should return checksum even for empty file");
        assertTrue(firstRead.isChanged(), "Should mark as changed on first read");

        // Act - Second read with same checksum
        ToolIO.ReadResponse secondRead = toolBox.readFile("second read",
                                                          testFile.toString(),
                                                          firstRead.getChecksum());

        // Assert second read
        assertNull(secondRead.getError(), "Should not have error");
        assertNull(secondRead.getContent(), "Should not return content when unchanged");
        assertFalse(secondRead.isChanged(), "Should mark as NOT changed");
    }

    @Test
    void testReRead_FileDeleted_ReturnsError() throws IOException {
        // Arrange
        String content = "Hello, World!";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);

        // Act - First read to get checksum
        ToolIO.ReadResponse firstRead = toolBox.readFile("first read", testFile.toString(), "");
        String checksum = firstRead.getChecksum();

        // Delete the file
        Files.delete(testFile);

        // Act - Try to read deleted file
        ToolIO.ReadResponse secondRead = toolBox.readFile("second read", testFile.toString(), checksum);

        // Assert
        assertNotNull(secondRead.getError(), "Should have error");
        assertTrue(secondRead.getError().contains("File not found"),
                   "Error should indicate file not found");
        assertNull(secondRead.getContent(), "Should not have content");
    }

    @Test
    void testReRead_FileModifiedBetweenReads() throws IOException {
        // Arrange
        String content1 = "Version 1";
        Files.writeString(testFile, content1, StandardCharsets.UTF_8);

        // Act - First read
        ToolIO.ReadResponse read1 = toolBox.readFile("read 1", testFile.toString(), "");
        String checksum1 = read1.getChecksum();

        // Modify file
        String content2 = "Version 2";
        Files.writeString(testFile, content2, StandardCharsets.UTF_8);

        // Act - Second read (detects change)
        ToolIO.ReadResponse read2 = toolBox.readFile("read 2", testFile.toString(), checksum1);
        String checksum2 = read2.getChecksum();

        // Assert read2 detected change
        assertTrue(read2.isChanged(), "Should detect change");
        assertEquals(content2, read2.getContent(), "Should return new content");

        // Act - Third read with new checksum (no change)
        ToolIO.ReadResponse read3 = toolBox.readFile("read 3", testFile.toString(), checksum2);

        // Assert read3 sees no change
        assertFalse(read3.isChanged(), "Should not detect change");
        assertNull(read3.getContent(), "Should not return content");

        // Modify again
        String content3 = "Version 3";
        Files.writeString(testFile, content3, StandardCharsets.UTF_8);

        // Act - Fourth read (detects another change)
        ToolIO.ReadResponse read4 = toolBox.readFile("read 4", testFile.toString(), checksum2);

        // Assert read4 detected change
        assertTrue(read4.isChanged(), "Should detect second change");
        assertEquals(content3, read4.getContent(), "Should return newest content");
    }

    @Test
    void testReRead_IncorrectChecksum_ReturnsNewContentAndChanged() throws IOException {
        // Arrange
        String content = "Hello, World!";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);

        // Act - Read with a fake/wrong checksum
        String fakeChecksum = "0000000000000000000000000000000000000000000000000000000000000000";
        ToolIO.ReadResponse response = toolBox.readFile("read with wrong checksum",
                                                        testFile.toString(),
                                                        fakeChecksum);

        // Assert
        assertNull(response.getError(), "Should not have error");
        assertEquals(content, response.getContent(), "Should return content");
        assertNotNull(response.getChecksum(), "Should return checksum");
        assertTrue(response.isChanged(), "Should mark as changed when checksum doesn't match");
        assertFalse(fakeChecksum.equals(response.getChecksum()),
                    "Returned checksum should differ from fake checksum");
    }

    @Test
    void testReRead_ModifiedFile_ReturnsNewContentAndChanged() throws IOException {
        // Arrange
        String originalContent = "Hello, World!";
        Files.writeString(testFile, originalContent, StandardCharsets.UTF_8);

        // Act - First read to get checksum
        ToolIO.ReadResponse firstRead = toolBox.readFile("first read", testFile.toString(), "");
        String originalChecksum = firstRead.getChecksum();

        // Modify the file
        String modifiedContent = "Hello, Modified World!";
        Files.writeString(testFile, modifiedContent, StandardCharsets.UTF_8);

        // Act - Second read with old checksum (file changed)
        ToolIO.ReadResponse secondRead = toolBox.readFile("second read", testFile.toString(), originalChecksum);

        // Assert
        assertNull(secondRead.getError(), "Should not have error");
        assertEquals(modifiedContent, secondRead.getContent(), "Should return NEW content");
        assertNotNull(secondRead.getChecksum(), "Should return new checksum");
        assertTrue(secondRead.isChanged(), "Should mark as changed");
        assertFalse(originalChecksum.equals(secondRead.getChecksum()),
                    "New checksum should be different from original");
    }

    @Test
    void testReRead_MultipleReads_UnchangedFile() throws IOException {
        // Arrange
        String content = "Persistent content";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);

        // Act - First read
        ToolIO.ReadResponse firstRead = toolBox.readFile("first read", testFile.toString(), "");
        String checksum = firstRead.getChecksum();

        // Act - Multiple subsequent reads without file modification
        ToolIO.ReadResponse secondRead = toolBox.readFile("second read", testFile.toString(), checksum);
        ToolIO.ReadResponse thirdRead = toolBox.readFile("third read", testFile.toString(), checksum);
        ToolIO.ReadResponse fourthRead = toolBox.readFile("fourth read", testFile.toString(), checksum);

        // Assert - All subsequent reads should indicate no change
        assertFalse(secondRead.isChanged(), "Second read should not be changed");
        assertFalse(thirdRead.isChanged(), "Third read should not be changed");
        assertFalse(fourthRead.isChanged(), "Fourth read should not be changed");

        // All should have same checksum
        assertEquals(checksum, secondRead.getChecksum(), "Checksums should match");
        assertEquals(checksum, thirdRead.getChecksum(), "Checksums should match");
        assertEquals(checksum, fourthRead.getChecksum(), "Checksums should match");

        // None should return content
        assertNull(secondRead.getContent(), "Should not return content");
        assertNull(thirdRead.getContent(), "Should not return content");
        assertNull(fourthRead.getContent(), "Should not return content");
    }

    @Test
    void testReRead_UnchangedFile_ReturnsNoContentAndNotChanged() throws IOException {
        // Arrange
        String content = "Hello, World!";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);

        // Act - First read to get checksum
        ToolIO.ReadResponse firstRead = toolBox.readFile("first read", testFile.toString(), "");
        String checksum = firstRead.getChecksum();

        // Act - Second read with same checksum (file unchanged)
        ToolIO.ReadResponse secondRead = toolBox.readFile("second read", testFile.toString(), checksum);

        // Assert
        assertNull(secondRead.getError(), "Should not have error");
        assertNull(secondRead.getContent(), "Should NOT return content when unchanged");
        assertEquals(checksum, secondRead.getChecksum(), "Should return same checksum");
        assertFalse(secondRead.isChanged(), "Should mark as NOT changed");
    }

    @Test
    void testRead_FileNotFound_ReturnsError() {
        // Arrange
        Path nonExistentFile = tempDir.resolve("does-not-exist.txt");

        // Act
        ToolIO.ReadResponse response = toolBox.readFile("test read", nonExistentFile.toString(), "");

        // Assert
        assertNotNull(response.getError(), "Should have error");
        assertTrue(response.getError().contains("File not found"),
                   "Error should indicate file not found");
        assertNull(response.getContent(), "Should not have content");
        assertNull(response.getChecksum(), "Should not have checksum");
    }
}
