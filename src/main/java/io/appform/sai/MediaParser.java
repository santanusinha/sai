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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
 * <li>{@code @image:clipboard} — image copied to the system clipboard (Wayland or X11)</li>
 * <li>{@code @image-url:https://example.com/image.png} — public HTTPS image URL;
 * a {@code file:} URI or an existing local path is read and sent as base64 data</li>
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

    private static final String CLIPBOARD_TOKEN = "clipboard";

    @Value
    public static class ParsedInput {
        String textPrompt;
        List<MediaInput> media;
        List<String> errors;

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
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
            return new ParsedInput(input, List.of(), List.of());
        }

        final var media = new ArrayList<MediaInput>();
        final var errors = new ArrayList<String>();
        final var matcher = MEDIA_TOKEN.matcher(input);
        final var cleaned = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            cleaned.append(input, lastEnd, matcher.start());
            final var type = matcher.group(1);
            final var path = matcher.group(2);

            try {
                final var mediaInput = switch (type) {
                    case "image" -> CLIPBOARD_TOKEN.equals(path)
                            ? readClipboardImage()
                            : readImageFile(path);
                    case "image-url" -> resolveImageUrl(path);
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
                errors.add("Cannot attach %s '%s': %s".formatted(type, path, e.getMessage()));
                cleaned.append(matcher.group(0));
            }

            lastEnd = matcher.end();
        }
        cleaned.append(input, lastEnd, input.length());

        return new ParsedInput(cleaned.toString().trim(), List.copyOf(media), List.copyOf(errors));
    }

    static String[] clipboardCommand() {
        return clipboardCommand(System.getenv("WAYLAND_DISPLAY"), System.getenv("DISPLAY"));
    }

    static String[] clipboardCommand(String waylandDisplay, String display) {
        if (!isBlank(waylandDisplay)) {
            return new String[]{
                    "wl-paste", "-t", "image/png"
            };
        }
        if (!isBlank(display)) {
            return new String[]{
                    "xclip", "-selection", "clipboard", "-t", "image/png", "-o"
            };
        }
        return null;
    }

    static String detectImageMime(byte[] bytes) {
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A) {
            return "png";
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "gif";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return "webp";
        }
        // The clipboard command requests image/png, so default to png.
        return "png";
    }

    static MediaInput readImageFromProcess(String[] command) throws IOException {
        final Process process;
        try {
            process = new ProcessBuilder(command).start();
        }
        catch (IOException e) {
            throw new IOException("Failed to run '" + command[0] + "': " + e.getMessage());
        }
        // Consume the streams on helper threads. Reading them on the current
        // thread before waitFor() blocks until the process exits, and reading
        // them after waitFor() can deadlock when the pipe buffer fills up.
        final var stdout = CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));
        final var stderr = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));
        final boolean finished;
        try {
            finished = process.waitFor(10, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            abort(process, stdout, stderr);
            throw new IOException("Interrupted while reading the clipboard.");
        }
        if (!finished) {
            abort(process, stdout, stderr);
            throw new IOException("'" + command[0] + "' did not finish in time.");
        }
        final byte[] bytes;
        final String stderrText;
        try {
            bytes = stdout.get(2, TimeUnit.SECONDS);
            stderrText = new String(stderr.get(2, TimeUnit.SECONDS), StandardCharsets.UTF_8).trim();
        }
        catch (ExecutionException | InterruptedException | TimeoutException e) {
            abort(process, stdout, stderr);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IOException("Failed to read the output of '" + command[0] + "'.");
        }
        if (process.exitValue() != 0 || bytes.length == 0) {
            throw new IOException("The clipboard does not contain an image. "
                    + "Copy an image to the clipboard first."
                    + (stderrText.isEmpty() ? "" : " (" + stderrText + ")"));
        }
        if (bytes.length > MAX_MEDIA_SIZE_BYTES) {
            throw new IOException("Image size %d bytes exceeds the maximum limit of %d bytes."
                    .formatted(bytes.length, MAX_MEDIA_SIZE_BYTES));
        }
        final var mime = detectImageMime(bytes);
        final var base64 = Base64.getEncoder().encodeToString(bytes);
        return MediaInput.imageContent("data:image/%s;base64,%s".formatted(mime, base64),
                                       ImageDetail.AUTO);
    }

    private static void abort(Process process, CompletableFuture<byte[]>... reads) {
        process.destroyForcibly();
        for (var read : reads) {
            read.cancel(true);
        }
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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static MediaInput readAudioFile(String filePath) throws IOException {
        final var path = Path.of(filePath).toAbsolutePath().normalize();
        checkFileSize(path);
        final var bytes = Files.readAllBytes(path);
        final var base64 = Base64.getEncoder().encodeToString(bytes);
        final var audioFormat = detectAudioFormat(filePath);
        return MediaInput.audio(base64, audioFormat);
    }

    private static MediaInput readClipboardImage() throws IOException {
        final var command = clipboardCommand();
        if (command == null) {
            throw new IOException("No graphical session found. "
                    + "Copy an image to the clipboard inside a Wayland or X11 session first.");
        }
        return readImageFromProcess(command);
    }

    private static MediaInput readImageFile(String filePath) throws IOException {
        final var path = Path.of(filePath).toAbsolutePath().normalize();
        checkFileSize(path);
        final var bytes = Files.readAllBytes(path);
        final var base64 = Base64.getEncoder().encodeToString(bytes);
        // UserPrompt.imageData requires the content to be a full data URL of the
        // form data:image/{mime};base64,&lt;content&gt;. Only png, jpeg, gif and
        // webp are accepted by the model APIs.
        final var lower = filePath.toLowerCase();
        final var mime = lower.endsWith(".png")
                ? "png"
                : lower.endsWith(".gif")
                        ? "gif"
                : lower.endsWith(".webp") ? "webp"
                : "jpeg";
        return MediaInput.imageContent("data:image/%s;base64,%s".formatted(mime, base64), ImageDetail.AUTO);
    }

    private static byte[] readStream(java.io.InputStream stream) {
        try (stream) {
            return stream.readAllBytes();
        }
        catch (IOException e) {
            return new byte[0];
        }
    }

    private static MediaInput resolveImageUrl(String value) throws IOException {
        if (CLIPBOARD_TOKEN.equals(value)) {
            return readClipboardImage();
        }
        if (value.startsWith("file:")) {
            return readImageFile(Path.of(URI.create(value)).toString());
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return MediaInput.imageUrl(value, ImageDetail.AUTO);
        }
        // A bare token that points at an existing local file is treated as a
        // local image; anything else would be rejected by the model API.
        if (Files.exists(Path.of(value))) {
            return readImageFile(value);
        }
        throw new IOException("Model APIs accept public HTTPS URLs or data URLs only. "
                + "Use @image:" + value + " to attach a local image file.");
    }
}
