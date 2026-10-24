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
package io.appform.sai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonepe.sentinelai.core.agentmessages.MediaTypes.AudioFormat;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.MessageContentType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class MediaParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parseAtFileWithoutPrefixIsNotMedia() {
        final var input = "Read @./file.txt";
        final var result = MediaParser.parse(input);
        assertEquals("Read @./file.txt", result.getTextPrompt());
        assertTrue(result.getMedia().isEmpty());
    }

    @Test
    void parseAudioFileReturnsMedia() throws IOException {
        final var audioFile = tempDir.resolve("test.wav");
        Files.write(audioFile, new byte[]{
                1, 2, 3, 4
        });
        final var input = "Transcribe this @audio:" + audioFile;
        final var result = MediaParser.parse(input);
        assertEquals("Transcribe this", result.getTextPrompt());
        assertEquals(1, result.getMedia().size());
        final var media = result.getMedia().get(0);
        assertEquals(MessageContentType.AUDIO, media.getContentType());
        assertEquals(AudioFormat.WAV, media.getAudioFormat());
    }

    @Test
    void parseEmptyInputReturnsEmptyMedia() {
        final var result = MediaParser.parse("");
        assertEquals("", result.getTextPrompt());
        assertTrue(result.getMedia().isEmpty());
    }

    @Test
    void parseImageFileDetectsGifAndWebpMime() throws IOException {
        final var gif = tempDir.resolve("pic.gif");
        Files.write(gif, new byte[]{
                1
        });
        final var gifMedia = MediaParser.parse("@image:" + gif).getMedia().get(0);
        assertTrue(gifMedia.getContent().startsWith("data:image/gif;base64,"));

        final var webp = tempDir.resolve("pic.webp");
        Files.write(webp, new byte[]{
                2
        });
        final var webpMedia = MediaParser.parse("@image:" + webp).getMedia().get(0);
        assertTrue(webpMedia.getContent().startsWith("data:image/webp;base64,"));
    }

    @Test
    void parseImageFileReturnsMedia() throws IOException {
        final var imageFile = tempDir.resolve("test.png");
        Files.write(imageFile, new byte[]{
                1, 2, 3, 4
        });
        final var input = "What is in this image? @image:" + imageFile;
        final var result = MediaParser.parse(input);
        assertEquals("What is in this image?", result.getTextPrompt());
        assertEquals(1, result.getMedia().size());
        final var media = result.getMedia().get(0);
        assertEquals(MessageContentType.IMAGE_DATA, media.getContentType());
        assertNotNull(media.getContent());
    }

    @Test
    void parseImageFileUrlReturnsBase64Data() throws IOException {
        final var imageFile = tempDir.resolve("local.png");
        Files.write(imageFile, new byte[]{
                5, 6, 7, 8
        });
        final var input = "What is this @image-url:" + imageFile.toUri();
        final var result = MediaParser.parse(input);
        assertEquals(1, result.getMedia().size());
        final var media = result.getMedia().get(0);
        assertEquals(MessageContentType.IMAGE_DATA, media.getContentType());
        assertTrue(media.getContent().startsWith("data:image/png;base64,"));
    }

    @Test
    void parseImageUrlReturnsMedia() {
        final var input = "Describe this @image-url:https://example.com/image.png";
        final var result = MediaParser.parse(input);
        assertEquals("Describe this", result.getTextPrompt());
        assertEquals(1, result.getMedia().size());
        final var media = result.getMedia().get(0);
        assertEquals(MessageContentType.IMAGE_URL, media.getContentType());
        assertEquals("https://example.com/image.png", media.getContent());
    }

    @Test
    void parseImageUrlWithLocalPathReturnsBase64Data() throws IOException {
        final var imageFile = tempDir.resolve("bare.jpg");
        Files.write(imageFile, new byte[]{
                9, 9
        });
        final var input = "Look @image-url:" + imageFile.toAbsolutePath();
        final var result = MediaParser.parse(input);
        assertEquals(1, result.getMedia().size());
        final var media = result.getMedia().get(0);
        assertEquals(MessageContentType.IMAGE_DATA, media.getContentType());
        assertTrue(media.getContent().startsWith("data:image/jpeg;base64,"));
    }

    @Test
    void parseImageUrlWithPlainHttpUrlReturnsMedia() {
        final var result = MediaParser.parse("Check @image-url:http://example.com/a.png");
        assertEquals(1, result.getMedia().size());
        assertEquals(MessageContentType.IMAGE_URL, result.getMedia().get(0).getContentType());
    }

    @Test
    void parseImageUrlWithUnsupportedValueReportsError() {
        final var result = MediaParser.parse("Check @image-url:not-a-url-or-file.png");
        assertTrue(result.hasErrors());
        assertTrue(result.getMedia().isEmpty());
    }

    @Test
    void parseMediaWithNoSurroundingText() throws IOException {
        final var imageFile = tempDir.resolve("test.png");
        Files.write(imageFile, new byte[]{
                1, 2, 3
        });
        final var input = "@image:" + imageFile;
        final var result = MediaParser.parse(input);
        assertEquals("", result.getTextPrompt());
        assertEquals(1, result.getMedia().size());
    }

    @Test
    void parseMp3AudioFileDetectsFormat() throws IOException {
        final var audioFile = tempDir.resolve("test.mp3");
        Files.write(audioFile, new byte[]{
                1, 2, 3, 4
        });
        final var input = "Transcribe @audio:" + audioFile;
        final var result = MediaParser.parse(input);
        assertEquals(1, result.getMedia().size());
        final var media = result.getMedia().get(0);
        assertEquals(AudioFormat.MP3, media.getAudioFormat());
    }

    @Test
    void parseMultipleMediaReturnsAll() throws IOException {
        final var imageFile = tempDir.resolve("img.png");
        Files.write(imageFile, new byte[]{
                1, 2
        });
        final var input = "Look at @image:" + imageFile + " and @image-url:https://example.com/img.png";
        final var result = MediaParser.parse(input);
        assertEquals(2, result.getMedia().size());
    }

    @Test
    void parseNonExistentFileReportsError() {
        final var input = "Check this @image:/nonexistent/path/file.png";
        final var result = MediaParser.parse(input);
        assertTrue(result.hasErrors());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("/nonexistent/path/file.png"));
        assertTrue(result.getMedia().isEmpty());
    }

    @Test
    void parseNullInputReturnsEmptyMedia() {
        final var result = MediaParser.parse(null);
        assertEquals(null, result.getTextPrompt());
        assertTrue(result.getMedia().isEmpty());
    }

    @Test
    void parsePlainTextReturnsNoMedia() {
        final var result = MediaParser.parse("Hello, how are you?");
        assertEquals("Hello, how are you?", result.getTextPrompt());
        assertTrue(result.getMedia().isEmpty());
    }
}
