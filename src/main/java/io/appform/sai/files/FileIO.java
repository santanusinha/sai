/*
 * Copyright (c) 2026 Original Author(s)
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
package io.appform.sai.files;

import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.utils.AgentUtils;

import io.appform.sai.tools.ToolIO;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import lombok.Value;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class FileIO {

    private static final int MAX_FILE_SIZE_BYTES = 1024 * 1024; // 1 MB

    @Value
    public static class ReadResult {
        String content;
        String checksum;
        String error;
    }

    /**
     * Edits a file based on the provided edit operations.
     *
     * @param filePath         The path to the file to edit.
     * @param edits            A list of edit operations to perform on the file.
     * @param expectedChecksum The expected SHA-256 checksum of the file before editing.
     * @return An EditResponse indicating whether the edit was successful, the new checksum of the file after editing,
     *         and any error message if an error occurred.
     */
    public static ToolIO.FileEditResponse editFile(String filePath,
                                                   List<ToolIO.FileEditOperation> edits,
                                                   String expectedChecksum) {
        log.debug("Editing file: {}", filePath);
        final var path = Path.of(filePath);
        if (!Files.exists(path)) {
            log.debug("File {} does not exist", filePath);
            return error("File not found");
        }
        final var currentContent = readFile(filePath, 1, -1, false);
        if (!Strings.isNullOrEmpty(currentContent.error)) {
            return error(currentContent.error);
        }
        if (!currentContent.checksum.equals(expectedChecksum)) {
            log.error("Checksum mismatch for file {}.", filePath);
            return error("Checksum mismatch. Re-read the file and try again with the latest checksum.");
        }
        final var content = currentContent.getContent();
        try {
            final var lines = editLines(content, edits);
            final var editedContent = String.join(System.lineSeparator(), lines);
            Files.writeString(path, editedContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            final var newChecksum = calculateChecksum(editedContent.getBytes(StandardCharsets.UTF_8));
            return ToolIO.FileEditResponse.builder()
                    .newChecksum(newChecksum)
                    .build();
        }
        catch (Exception e) {
            final var errorMessage = "Error accessing file: " + AgentUtils.rootCause(e).getMessage();
            log.error(errorMessage, e);
            return error(errorMessage);
        }
    }

    /**
     * Reads the content of a file.
     *
     * @param filePath       The path to the file to read.
     * @param startLine      The starting line number to read (1-based). Ignored if endLine is -1.
     * @param endLine        The ending line number to read (1-based, inclusive). Use -1 to read until the end of the
     *                       file.
     * @param addLineNumbers Whether to add line numbers to the content.
     * @return A ReadResult containing the file content (with optional line numbers), the checksum of the content, And
     *         any error message if an error occurred.
     */
    public static ReadResult readFile(String filePath, int startLine, int endLine, boolean addLineNumbers) {
        log.debug("Reading file: {}", filePath);
        try {
            final var path = Path.of(filePath);
            if (!Files.exists(path)) {
                return new ReadResult(null, null, "File not found: " + filePath);
            }
            if (Files.size(path) > MAX_FILE_SIZE_BYTES) {
                return new ReadResult(null,
                                      null,
                                      "File size exceeds the maximum limit of 1 MB. Use the bash tool to read and operate on large files.");
            }
            final var content = Files.readString(path, StandardCharsets.UTF_8);
            final var lines = content.isEmpty() ? new String[0] : content.split(System.lineSeparator(), -1);
            final var checksum = calculateChecksum(content.getBytes(StandardCharsets.UTF_8));
            if (endLine == -1) {
                log.debug("Reading whole file {} as endLine is -1", filePath);
                if (addLineNumbers) {
                    return new ReadResult(addLineNumbers(lines, 1), checksum, null);
                }
                return new ReadResult(content, checksum, null);
            }
            if (startLine < 1 || startLine > endLine || endLine > lines.length) {
                final var errorMessage = "Invalid line range: (startLine=%d, endLine=%d). Valid: (startLine=1, endLine=%d)"
                        .formatted(startLine, endLine, lines.length);
                return new ReadResult(null, null, errorMessage);
            }
            log.debug("Reading file {} lines from {} to {}", filePath, startLine, endLine);
            final var relevantLines = Arrays.copyOfRange(lines, startLine - 1, endLine);
            if (addLineNumbers) {
                return new ReadResult(addLineNumbers(relevantLines, startLine), null, null);
            }
            return new ReadResult(String.join(System.lineSeparator(), relevantLines), null, null);
        }
        catch (Exception e) {
            final var errorMessage = "Error reading file: " + AgentUtils.rootCause(e).getMessage();
            log.error(errorMessage, e);
            return new ReadResult(null, null, errorMessage);
        }
    }

    public static ToolIO.WriteResponse write(String filePath, String content, String expectedChecksum) {
        log.debug("Writing to file: {}", filePath);
        final var path = Path.of(filePath);
        if (Files.exists(path)) {
            final var currentContent = readFile(filePath, 1, -1, false);
            if (!Strings.isNullOrEmpty(currentContent.error)) {
                // We don't want to handle massive files in the edit tool,
                // so if we get an error reading the file (like file too large),
                // we return that error instead of proceeding with edits.
                return ToolIO.WriteResponse.builder().error(currentContent.error).build();
            }
            if (!currentContent.checksum.equals(expectedChecksum)) {
                final var errorMessage = "Checksum mismatch. Re-read the file and try again with the latest checksum.";
                log.error("Checksum mismatch for file {}.", filePath);
                return ToolIO.WriteResponse.builder()
                        .error(errorMessage)
                        .build();
            }
        }
        try {
            Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return ToolIO.WriteResponse.builder()
                    .success(true)
                    .charsWritten(content.length())
                    .updatedChecksum(calculateChecksum(content.getBytes(StandardCharsets.UTF_8)))
                    .build();
        }
        catch (Exception e) {
            final var errorMessage = "Error writing to file: " + AgentUtils.rootCause(e).getMessage();
            log.error(errorMessage, e);
            return ToolIO.WriteResponse.builder().error(errorMessage).build();
        }
    }

    private static String addLineNumbers(String[] lines, int startLineNumber) {
        final var sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append(String.format("%6d\t%s%n", startLineNumber + i, lines[i]));
        }
        return sb.toString();
    }

    private static List<String> applyEdit(List<String> lines, int startLine, int endLine, String newContent) {
        final var effectiveEndLine = endLine == -1 ? lines.size() : endLine;
        if (startLine < 1 || effectiveEndLine < startLine || effectiveEndLine > lines.size()) {
            throw new IllegalArgumentException("Invalid line range in replacement: " + startLine + "-"
                    + effectiveEndLine);
        }
        final var trimmedContent = newContent != null && newContent.endsWith("\n")
                ? newContent.substring(0, newContent.length() - 1)
                : newContent;
        final var replacementLines = !Strings.isNullOrEmpty(trimmedContent)
                ? List.of(trimmedContent.split(System.lineSeparator(), -1))
                : List.<String>of();
        for (int lineNum = effectiveEndLine; lineNum >= startLine; lineNum--) {
            lines.remove(lineNum - 1);
        }
        if (!Strings.isNullOrEmpty(trimmedContent)) {
            lines.addAll(startLine - 1, replacementLines);
        }
        return lines;
    }

    private static List<String> editLines(String content, List<ToolIO.FileEditOperation> edits) {
        final var lines = new ArrayList<String>(Arrays.asList(content.split(System.lineSeparator(), -1)));
        //Now apply the edits in reverse order
        for (int i = edits.size() - 1; i >= 0; i--) {
            final var replacement = edits.get(i);
            final var startLine = replacement.getStartLine();
            final var endLine = replacement.getEndLine();
            final var newContent = replacement.getContent();
            applyEdit(lines, startLine, endLine, newContent);
        }
        return lines;
    }

    private static final ToolIO.FileEditResponse error(String message) {
        return ToolIO.FileEditResponse.builder()
                .error(message)
                .build();
    }

    public String calculateChecksum(byte[] content) {
        try {
            final var digest = MessageDigest.getInstance("SHA-256");
            final var encodedhash = digest.digest(content);
            return HexFormat.of().formatHex(encodedhash);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not found", e);
        }
    }

}
