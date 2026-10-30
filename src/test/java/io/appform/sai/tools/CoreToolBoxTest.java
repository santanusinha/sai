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

import com.phonepe.sentinelai.core.agentmessages.MediaTypes.ImageDetail;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.MessageContentType;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.hooks.PendingMessagesQueue;

import io.appform.sai.MediaParser;
import io.appform.sai.files.FileIO;
import io.appform.sai.term.Printer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for the read() and writeFile() methods in CoreToolBox.
 */
class CoreToolBoxTest {

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00
    };

    private static final byte[] JPEG_BYTES = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10
    };

    private static final byte[] GIF_BYTES = {
            'G', 'I', 'F', '8', '9', 'a', 0x01, 0x00
    };

    private CoreToolBox toolBox;
    private Path tempDir;
    private Path testFile;

    @Test
    void emptyChecksumForcesReReadEvenWhenUnchanged() throws IOException {
        Files.writeString(testFile, "Unchanged content", StandardCharsets.UTF_8);
        final var firstRead = toolBox.readFile("first read", testFile.toString(), "", null);
        final var checksum = firstRead.getChecksum();

        final var unchanged = toolBox.readFile("check unchanged", testFile.toString(), checksum, null);
        assertFalse(unchanged.isChanged());
        assertNull(unchanged.getContent());

        final var forced = toolBox.readFile("force re-read", testFile.toString(), "", null);
        assertTrue(forced.isChanged());
        assertNotNull(forced.getContent());
        assertTrue(forced.getContent().contains("Unchanged content"), forced.getContent());
        assertEquals(checksum, forced.getChecksum());
    }

    @Test
    void firstReadEmptyChecksum() throws IOException {
        final var content = "Hello, World!";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);

        final var response = toolBox.readFile("test first read", testFile.toString(), "", null);

        assertNull(response.getError());
        assertTrue(response.getContent().contains(content), response.getContent());
        assertTrue(response.getContent().startsWith("     1\t"), response.getContent());
        assertNotNull(response.getChecksum());
        assertTrue(response.isChanged());
    }

    // ---- read_image tests ----

    @Test
    void firstReadNullChecksum() throws IOException {
        final var content = "Hello, World!";
        Files.writeString(testFile, content, StandardCharsets.UTF_8);

        final var response = toolBox.readFile("test first read", testFile.toString(), null, null);

        assertNull(response.getError());
        assertTrue(response.getContent().contains(content), response.getContent());
        assertTrue(response.getContent().startsWith("     1\t"), response.getContent());
        assertNotNull(response.getChecksum());
        assertTrue(response.isChanged());
    }

    @Test
    void nullChecksumForcesReReadEvenWhenUnchanged() throws IOException {
        Files.writeString(testFile, "Unchanged content", StandardCharsets.UTF_8);
        final var firstRead = toolBox.readFile("first read", testFile.toString(), "", null);
        final var checksum = firstRead.getChecksum();

        final var unchanged = toolBox.readFile("check unchanged", testFile.toString(), checksum, null);
        assertFalse(unchanged.isChanged());
        assertNull(unchanged.getContent());

        final var forced = toolBox.readFile("force re-read", testFile.toString(), null, null);
        assertTrue(forced.isChanged());
        assertNotNull(forced.getContent());
        assertTrue(forced.getContent().contains("Unchanged content"), forced.getContent());
        assertEquals(checksum, forced.getChecksum());
    }

    @Test
    void reReadDeletedFileReturnsError() throws IOException {
        Files.writeString(testFile, "Hello, World!", StandardCharsets.UTF_8);
        final var checksum = toolBox.readFile("first read", testFile.toString(), "", null).getChecksum();
        Files.delete(testFile);

        final var response = toolBox.readFile("second read", testFile.toString(), checksum, null);

        assertNotNull(response.getError());
        assertTrue(response.getError().contains("File not found"), response.getError());
        assertNull(response.getContent());
    }

    @Test
    void reReadDetectsModifications() throws IOException {
        Files.writeString(testFile, "Version 1", StandardCharsets.UTF_8);
        final var checksum1 = toolBox.readFile("read 1", testFile.toString(), "", null).getChecksum();

        Files.writeString(testFile, "Version 2", StandardCharsets.UTF_8);
        final var read2 = toolBox.readFile("read 2", testFile.toString(), checksum1, null);
        final var checksum2 = read2.getChecksum();

        assertTrue(read2.isChanged());
        assertTrue(read2.getContent().contains("Version 2"), read2.getContent());

        final var read3 = toolBox.readFile("read 3", testFile.toString(), checksum2, null);
        assertFalse(read3.isChanged());
        assertNull(read3.getContent());

        Files.writeString(testFile, "Version 3", StandardCharsets.UTF_8);
        final var read4 = toolBox.readFile("read 4", testFile.toString(), checksum2, null);

        assertTrue(read4.isChanged());
        assertTrue(read4.getContent().contains("Version 3"), read4.getContent());
    }

    @Test
    void reReadEmptyFileUnchanged() throws IOException {
        Files.writeString(testFile, "", StandardCharsets.UTF_8);

        final var firstRead = toolBox.readFile("first read", testFile.toString(), "", null);

        assertNull(firstRead.getError());
        assertEquals("", firstRead.getContent());
        assertNotNull(firstRead.getChecksum());
        assertTrue(firstRead.isChanged());

        final var secondRead = toolBox.readFile("second read", testFile.toString(), firstRead.getChecksum(), null);

        assertNull(secondRead.getError());
        assertNull(secondRead.getContent());
        assertFalse(secondRead.isChanged());
    }

    @Test
    void reReadModifiedFileReturnsNewContent() throws IOException {
        Files.writeString(testFile, "Hello, World!", StandardCharsets.UTF_8);
        final var originalChecksum = toolBox.readFile("first read", testFile.toString(), "", null).getChecksum();

        Files.writeString(testFile, "Hello, Modified World!", StandardCharsets.UTF_8);
        final var response = toolBox.readFile("second read", testFile.toString(), originalChecksum, null);

        assertNull(response.getError());
        assertTrue(response.getContent().contains("Hello, Modified World!"), response.getContent());
        assertNotNull(response.getChecksum());
        assertTrue(response.isChanged());
        assertFalse(originalChecksum.equals(response.getChecksum()));
    }

    // ---- read tests ----

    @Test
    void reReadUnchangedFileMultipleTimes() throws IOException {
        Files.writeString(testFile, "Persistent content", StandardCharsets.UTF_8);
        final var checksum = toolBox.readFile("first read", testFile.toString(), "", null).getChecksum();

        final var second = toolBox.readFile("second read", testFile.toString(), checksum, null);
        final var third = toolBox.readFile("third read", testFile.toString(), checksum, null);
        final var fourth = toolBox.readFile("fourth read", testFile.toString(), checksum, null);

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
        final var checksum = toolBox.readFile("first read", testFile.toString(), "", null).getChecksum();

        final var response = toolBox.readFile("second read", testFile.toString(), checksum, null);

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

        final var response = toolBox.readFile("read with wrong checksum", testFile.toString(), fakeChecksum, null);

        assertNull(response.getError());
        assertTrue(response.getContent().contains(content), response.getContent());
        assertNotNull(response.getChecksum());
        assertTrue(response.isChanged());
        assertFalse(fakeChecksum.equals(response.getChecksum()));
    }

    @Test
    void readImageGifIsRejected() throws IOException {
        final var pending = new PendingMessagesQueue();
        final var imageToolBox = new CoreToolBox((Printer) null, null, pending, "session-1");
        final var image = tempDir.resolve("test.gif");
        Files.write(image, GIF_BYTES);

        final var response = imageToolBox.readImage("see the animation", image.toString(), null, null);

        assertNotNull(response.getError());
        assertTrue(response.getError().contains("Only PNG and JPEG"), response.getError());
        assertFalse(response.isAttached());
        assertEquals(0, pending.size());
    }

    @Test
    void readImageJpegHonoursHighDetail() throws IOException {
        final var pending = new PendingMessagesQueue();
        final var imageToolBox = new CoreToolBox((Printer) null, null, pending, "session-1");
        final var image = tempDir.resolve("test.jpg");
        Files.write(image, JPEG_BYTES);

        final var response = imageToolBox.readImage("see the photo", image.toString(), "HIGH", null);

        assertNull(response.getError());
        assertTrue(response.isAttached());
        assertEquals("jpeg", response.getFormat());
        assertEquals("HIGH", response.getImageDetail());
        assertEquals(1, pending.size());
        final var queued = (UserPrompt) pending.drain().get(0);
        assertEquals(ImageDetail.HIGH, queued.getImageDetail());
        assertTrue(queued.getContent().startsWith("data:image/jpeg;base64,"), queued.getContent());
    }

    @Test
    void readImageMissingFileReturnsError() throws IOException {
        final var pending = new PendingMessagesQueue();
        final var imageToolBox = new CoreToolBox((Printer) null, null, pending, "session-1");
        final var image = tempDir.resolve("missing.png");

        final var response = imageToolBox.readImage("see the image", image.toString(), null, null);

        assertNotNull(response.getError());
        assertTrue(response.getError().contains("File not found"), response.getError());
        assertEquals(0, pending.size());
    }

    @Test
    void readImageOversizeFileIsRejected() throws IOException {
        final var pending = new PendingMessagesQueue();
        final var imageToolBox = new CoreToolBox((Printer) null, null, pending, "session-1");
        final var image = tempDir.resolve("big.png");
        Files.write(image, PNG_BYTES);
        try (final var out = Files.newOutputStream(image,
                                                   java.nio.file.StandardOpenOption.APPEND)) {
            final var padding = new byte[1024];
            final var toWrite = MediaParser.maxMediaSizeBytes() + 1L;
            for (long written = PNG_BYTES.length; written < toWrite; written += padding.length) {
                out.write(padding, 0, (int) Math.min(padding.length, toWrite - written));
            }
        }

        final var response = imageToolBox.readImage("see the huge image", image.toString(), null, null);

        assertNotNull(response.getError());
        assertTrue(response.getError().contains("too large"), response.getError());
        assertFalse(response.isAttached());
        assertEquals(0, pending.size());
    }

    @Test
    void readImagePngQueuesImageDataMessage() throws IOException {
        final var pending = new PendingMessagesQueue();
        final var imageToolBox = new CoreToolBox((Printer) null, null, pending, "session-1");
        final var image = tempDir.resolve("test.png");
        Files.write(image, PNG_BYTES);

        final var response = imageToolBox.readImage("see the diagram", image.toString(), null, null);

        assertNull(response.getError());
        assertTrue(response.isAttached());
        assertEquals("png", response.getFormat());
        assertEquals("AUTO", response.getImageDetail());
        assertEquals(PNG_BYTES.length, response.getBytes());
        assertEquals(1, pending.size());
        final var queued = (UserPrompt) pending.drain().get(0);
        assertEquals(MessageContentType.IMAGE_DATA, queued.getContentType());
        assertEquals(ImageDetail.AUTO, queued.getImageDetail());
        assertEquals("session-1", queued.getSessionId());
        assertTrue(queued.getContent().startsWith("data:image/png;base64,"), queued.getContent());
    }

    @Test
    void readImageWithoutQueueReturnsError() throws IOException {
        final var image = tempDir.resolve("test.png");
        Files.write(image, PNG_BYTES);

        final var response = toolBox.readImage("see the image", image.toString(), null, null);

        assertNotNull(response.getError());
        assertTrue(response.getError().contains("not available"), response.getError());
        assertFalse(response.isAttached());
    }

    @BeforeEach
    void setUp() throws IOException {
        toolBox = new CoreToolBox((Printer) null);
        tempDir = Files.createTempDirectory(Path.of("target"), "coretoolbox-test");
        testFile = tempDir.resolve("test.txt");
        // No store bound: edits pass unattributed (backwards-compatible mode).
        io.appform.sai.tasks.TaskStateRegistry.getInstance().bind(null);
    }

    @AfterEach
    void tearDown() throws IOException {
        try (var entries = Files.list(tempDir)) {
            for (var entry : entries.toList()) {
                Files.deleteIfExists(entry);
            }
        }
        Files.deleteIfExists(tempDir);
    }

    // ---- write tests ----

    @Test
    void writeCreatesNewFile() throws IOException {
        final var newFile = tempDir.resolve("new.txt");
        final var content = "hello world";

        final var response = toolBox.writeFile(newFile.toString(), content, "write test", null, "");

        assertNull(response.getError());
        assertTrue(response.isSuccess());
        assertTrue(Files.exists(newFile));
        assertEquals(content, Files.readString(newFile));
        assertEquals(content.length(), response.getCharsWritten());
    }

    @Test
    void writeMultibyteContentReportsCharacters() throws IOException {
        final var newFile = tempDir.resolve("multibyte.txt");
        final var content = "héllo wörld 😀";

        final var response = toolBox.writeFile(newFile.toString(), content, "write test", null, "");

        assertNull(response.getError());
        assertEquals(content, Files.readString(newFile));
    }

    @Test
    void writeNewFileTooLargeReturnsError() throws IOException {
        final var newFile = tempDir.resolve("too-large.txt");
        final var content = "x".repeat(1024 * 1024 + 1);

        final var response = toolBox.writeFile(newFile.toString(), content, "write test", null, "");

        assertNotNull(response.getError());
        assertTrue(response.getError().contains("1 MB"), response.getError());
        assertFalse(Files.exists(newFile));
    }

    @Test
    void writeOverwritesWithCorrectChecksum() throws IOException {
        final var file = tempDir.resolve("existing.txt");
        final var original = "original content";
        Files.writeString(file, original, StandardCharsets.UTF_8);
        final var newContent = "new content";

        final var response = toolBox.writeFile(file.toString(),
                                               newContent,
                                               "write test",
                                               null,
                                               FileIO.calculateChecksum(original.getBytes(StandardCharsets.UTF_8)));

        assertNull(response.getError());
        assertTrue(response.isSuccess());
        assertEquals(newContent, Files.readString(file));
    }

    @Test
    void writeWrongChecksumReturnsError() throws IOException {
        final var file = tempDir.resolve("existing.txt");
        Files.writeString(file, "some content", StandardCharsets.UTF_8);

        final var response = toolBox.writeFile(file.toString(), "new content", "write test", null, "wrong-checksum");

        assertNotNull(response.getError());
        assertTrue(response.getError().contains("Checksum mismatch"), response.getError());
        assertEquals("some content", Files.readString(file));
    }
}
