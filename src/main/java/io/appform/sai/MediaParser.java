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

import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.agent.MediaInput;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.AudioFormat;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.ImageDetail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

import lombok.Value;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Parses media references from user input text.
 *
 * <p>Supported syntax (space-delimited tokens in the input):
 * <ul>
 * <li>{@code @image:./path/to/image.png} — image file, base64-encoded</li>
 * <li>{@code @image-url:https://example.com/image.png} — image URL</li>
 * <li>{@code @audio:./path/to/audio.wav} — audio file, base64-encoded</li>
 * </ul>
 *
 * <p>The plain {@code @./path/to/file.txt} syntax (no type prefix) is handled
 * by {@link SaiCommand#resolveInput(String)} and reads the file as text.
 */
@Slf4j
@UtilityClass
public class MediaParser {

    private static final Pattern MEDIA_TOKEN = Pattern.compile(
                                                               "@(image|image-url|audio):([^\\s]+)");

    private static final int MAX_MEDIA_SIZE_BYTES = 20 * 1024 * 1024; // 20 MB

    @Value
    public static class ParsedInput {
        String textPrompt;
        List<MediaInput> media;
    }

    /**
     * Scans the input for media references, reads and encodes the referenced files,
     * and returns the cleaned text prompt with the media list.
     *
     * @param input the raw user input
     * @return a {@link ParsedInput} with the text prompt and media list
     */
    public static ParsedInput parse(String input) {
        if (Strings.isNullOrEmpty(input)) {
            return new ParsedInput(input, List.of());
        }

        final var media = new ArrayList<MediaInput>();
        final var matcher = MEDIA_TOKEN.matcher(input);
        final var cleaned = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            cleaned.append(input, lastEnd, matcher.start());
            final var type = matcher.group(1);
            final var path = matcher.group(2);

            try {
                final var mediaInput = switch (type) {
                    case "image" -> readImageFile(path);
                    case "image-url" -> MediaInput.imageUrl(path, ImageDetail.AUTO);
                    case "audio" -> readAudioFile(path);
                    default -> throw new IllegalArgumentException("Unsupported media type: " + type);
                };
                media.add(mediaInput);
                log.info("Parsed media reference: type={}, path={}", type, path);
            }
            catch (Exception e) {
                log.error("Failed to parse media reference: type={}, path={}, error={}",
                          type,
                          path,
                          e.getMessage());
                // Keep the original token in the text so the user sees the error
                cleaned.append(matcher.group(0));
            }

            lastEnd = matcher.end();
        }
        cleaned.append(input, lastEnd, input.length());

        return new ParsedInput(cleaned.toString().trim(), List.copyOf(media));
    }

    private static void checkFileSize(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("File not found: " + path);
        }
        final var size = Files.size(path);
        if (size > MAX_MEDIA_SIZE_BYTES) {
            throw new IOException(
                                  "File size %d bytes exceeds the maximum limit of %d bytes: %s"
                                          .formatted(size, MAX_MEDIA_SIZE_BYTES, path));
        }
    }

    private static AudioFormat detectAudioFormat(String filePath) {
        final var lower = filePath.toLowerCase();
        if (lower.endsWith(".mp3")) {
            return AudioFormat.MP3;
        }
        if (lower.endsWith(".wav")) {
            return AudioFormat.WAV;
        }
        log.warn("Could not detect audio format from extension, defaulting to WAV: {}", filePath);
        return AudioFormat.WAV;
    }

    private static MediaInput readAudioFile(String filePath) throws IOException {
        final var path = Path.of(filePath).toAbsolutePath().normalize();
        checkFileSize(path);
        final var bytes = Files.readAllBytes(path);
        final var base64 = Base64.getEncoder().encodeToString(bytes);
        final var audioFormat = detectAudioFormat(filePath);
        return MediaInput.audio(base64, audioFormat);
    }

    private static MediaInput readImageFile(String filePath) throws IOException {
        final var path = Path.of(filePath).toAbsolutePath().normalize();
        checkFileSize(path);
        final var bytes = Files.readAllBytes(path);
        final var base64 = Base64.getEncoder().encodeToString(bytes);
        return MediaInput.imageContent(base64, ImageDetail.AUTO);
    }
}
