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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.core.utils.AgentUtils;

import io.appform.sai.Printer;
import io.appform.sai.tools.ToolIO.LineEditOperation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class CoreToolBox implements ToolBox {

    private static final Pattern HUNK_PATTERN = Pattern.compile(
                                                                "^@@\\s+-(\\d+)(?:,(\\d+))?\\s+\\+(\\d+)(?:,(\\d+))?\\s+@@");

    private final Printer printer;

    @Tool("Run bash commands on the system where the agent is running. This is the core tool and should be used for any command execution needs. Use this tool to run any bash command, including those that interact with the file system, network, or other system resources. Be cautious while using this tool, as it can execute any command on the system. Do not operate on files mentioned in .gitignore")
    public ToolIO.BashResponse bash(
                                    @JsonPropertyDescription("Reason for requesting the tool. This is shown to the user for informational purposes.") String requestReason,
                                    @JsonPropertyDescription("The bash command to execute. This should be a single line command. Multi-line commands are not supported.") String command,
                                    @JsonPropertyDescription("The timeout for the bash command execution in seconds. If the command does not complete within this time, it will be terminated. Default is 30 seconds. Adjust this if you expect the command to take longer to execute, but be cautious as setting it too high may lead to hanging processes.") int timeoutSeconds) {
        log.info("Executing bash command: {}", command);
        try {
            final var commandOutput = new BashCommandRunner(command,
                                                            Duration.ofSeconds(timeoutSeconds))
                    .call();

            final var statusCode = commandOutput.getStatusCode();
            log.info("Bash command execution completed with status code: {}", statusCode);
            return new ToolIO.BashResponse(statusCode, commandOutput.getStdout(), commandOutput.getStderr());
        }
        catch (Exception e) {
            final var errorMessage = "Error executing bash command: " + AgentUtils.rootCause(e).getMessage();
            log.error(errorMessage, e);
            return new ToolIO.BashResponse(-1, "", errorMessage);
        }
    }

    // Patch-based edit - kept for internal use but not exposed to LLM due to formatting issues
    @SuppressWarnings("java:S3776")
    public ToolIO.EditResponse edit(ToolIO.EditRequest request) {
        log.info("Editing file: {}", request.getPath());
        try {
            final var path = Path.of(request.getPath());
            if (!Files.exists(path)) {
                return ToolIO.EditResponse.builder()
                        .success(false)
                        .error("File not found: " + request.getPath())
                        .build();
            }

            final var content = Files.readString(path, StandardCharsets.UTF_8);
            final var currentChecksum = calculateChecksum(content.getBytes(StandardCharsets.UTF_8));

            if (!currentChecksum.equals(request.getExpectedChecksum())) {
                return ToolIO.EditResponse.builder()
                        .success(false)
                        .error("Checksum mismatch. Expected: " + request.getExpectedChecksum() + ", Actual: "
                                + currentChecksum)
                        .build();
            }

            // Validate patch format before applying
            final var validationError = validatePatchFormat(request.getPatchContent());
            if (validationError.isPresent()) {
                return ToolIO.EditResponse.builder()
                        .success(false)
                        .error("Invalid patch format: " + validationError.get())
                        .build();
            }

            // Create temporary patch file
            final var patchFile = Files.createTempFile("sai-patch-", ".diff");
            Files.writeString(patchFile,
                              request.getPatchContent(),
                              StandardOpenOption.CREATE,
                              StandardOpenOption.TRUNCATE_EXISTING);

            try {
                final var command = String.format("patch %s %s", path.toAbsolutePath(), patchFile.toAbsolutePath());
                final var commandOutput = new BashCommandRunner(command, Duration.ofSeconds(30)).call();

                if (commandOutput.getStatusCode() != 0) {
                    return ToolIO.EditResponse.builder()
                            .success(false)
                            .error("Patch failed: " + commandOutput.getStderr() + "\nStdout: " + commandOutput
                                    .getStdout())
                            .build();
                }

                // Verify and return new checksum
                final var newContent = Files.readString(path, StandardCharsets.UTF_8);
                final var newChecksum = calculateChecksum(newContent.getBytes(StandardCharsets.UTF_8));

                return ToolIO.EditResponse.builder()
                        .success(true)
                        .newChecksum(newChecksum)
                        .build();

            }
            finally {
                Files.deleteIfExists(patchFile);
            }

        }
        catch (Exception e) {
            final var errorMessage = "Error editing file: " + AgentUtils.rootCause(e).getMessage();
            log.error(errorMessage, e);
            return ToolIO.EditResponse.builder()
                    .success(false)
                    .error(errorMessage)
                    .build();
        }
    }

    @Tool("Edit a file by line number. Use this to insert, replace, or delete lines at specific positions.")
    @SuppressWarnings("java:S3776")
    public ToolIO.LineEditResponse lineEdit(@JsonPropertyDescription("Reason for editing the file.") String requestReason,
                                            @JsonPropertyDescription("The absolute path to the file to edit.") String filePath,
                                            @JsonPropertyDescription("The operation to perform: INSERT_BEFORE, INSERT_AFTER, REPLACE, or DELETE.") LineEditOperation operation,
                                            @JsonPropertyDescription("The starting line number (1-indexed). For INSERT_BEFORE/INSERT_AFTER, this is the reference line.") int startLine,
                                            @JsonPropertyDescription("The ending line number (1-indexed, inclusive). Only used for REPLACE and DELETE operations. If not specified, defaults to startLine.") Integer endLine,
                                            @JsonPropertyDescription("The content to insert or replace with. Not used for DELETE operation.") String content,
                                            @JsonPropertyDescription("The expected SHA-256 checksum of the file before editing. Use the checksum from a previous read operation.") String expectedChecksum) {
        log.info("Line edit in file: {} operation: {}", filePath, operation);
        try {
            final var path = Path.of(filePath);
            if (!Files.exists(path)) {
                return ToolIO.LineEditResponse.builder()
                        .success(false)
                        .error("File not found: " + filePath)
                        .build();
            }

            final var fileContent = Files.readString(path, StandardCharsets.UTF_8);
            final var currentChecksum = calculateChecksum(fileContent.getBytes(StandardCharsets.UTF_8));

            if (!currentChecksum.equals(expectedChecksum)) {
                return ToolIO.LineEditResponse.builder()
                        .success(false)
                        .error("Checksum mismatch. Expected: " + expectedChecksum + ", Actual: "
                                + currentChecksum + ". Re-read the file to get the current checksum.")
                        .build();
            }

            // Split into lines, preserving trailing empty line if present
            final var lines = new ArrayList<>(List.of(fileContent.split("\n", -1)));
            final var totalLines = lines.size();

            final var opEndLine = endLine != null ? endLine : startLine;

            // Validate line numbers
            if (startLine < 1) {
                return ToolIO.LineEditResponse.builder()
                        .success(false)
                        .error("Start line must be >= 1. Got: " + startLine)
                        .build();
            }

            if (opEndLine < startLine) {
                return ToolIO.LineEditResponse.builder()
                        .success(false)
                        .error("End line (" + opEndLine + ") cannot be less than start line (" + startLine + ").")
                        .build();
            }

            // For operations that reference existing lines, check bounds
            if (operation != ToolIO.LineEditOperation.INSERT_BEFORE || startLine > 1) {
                if (startLine > totalLines) {
                    return ToolIO.LineEditResponse.builder()
                            .success(false)
                            .error("Start line " + startLine + " is beyond file length (" + totalLines + " lines).")
                            .build();
                }
            }

            if ((operation == ToolIO.LineEditOperation.REPLACE
                    || operation == ToolIO.LineEditOperation.DELETE)
                    && opEndLine > totalLines) {
                return ToolIO.LineEditResponse.builder()
                        .success(false)
                        .error("End line " + opEndLine + " is beyond file length (" + totalLines + " lines).")
                        .build();
            }

            // Parse content to insert (split by newlines)
            final var contentLines = !Strings.isNullOrEmpty(content)
                    ? List.of(content.split("\n", -1))
                    : List.<String>of();

            switch (operation) {
                case INSERT_BEFORE:
                    // Insert content before the specified line
                    // startLine is 1-indexed, so insert at index startLine-1
                    lines.addAll(startLine - 1, contentLines);
                    break;

                case INSERT_AFTER:
                    // Insert content after the specified line
                    // startLine is 1-indexed, so insert at index startLine
                    lines.addAll(startLine, contentLines);
                    break;

                case REPLACE:
                    // Remove lines from startLine to endLine (inclusive), then insert new content
                    // startLine and endLine are 1-indexed
                    for (int i = opEndLine; i >= startLine; i--) {
                        lines.remove(i - 1);
                    }
                    lines.addAll(startLine - 1, contentLines);
                    break;

                case DELETE:
                    // Remove lines from startLine to endLine (inclusive)
                    for (int i = opEndLine; i >= startLine; i--) {
                        lines.remove(i - 1);
                    }
                    break;

                default:
                    return ToolIO.LineEditResponse.builder()
                            .success(false)
                            .error("Unknown operation: " + operation)
                            .build();
            }

            // Join lines back together
            final var newContent = String.join("\n", lines);

            // Write the new content
            Files.writeString(path,
                              newContent,
                              StandardOpenOption.CREATE,
                              StandardOpenOption.TRUNCATE_EXISTING);
            final var newChecksum = calculateChecksum(newContent.getBytes(StandardCharsets.UTF_8));

            return ToolIO.LineEditResponse.builder()
                    .success(true)
                    .newChecksum(newChecksum)
                    .build();
        }
        catch (Exception e) {
            final var errorMessage = "Error during line edit: " + AgentUtils.rootCause(e).getMessage();
            log.error(errorMessage, e);
            return ToolIO.LineEditResponse.builder()
                    .success(false)
                    .error(errorMessage)
                    .build();
        }
    }

    @Override
    public String name() {
        return "core";
    }

    @Tool("Read a file from the local filesystem. Returns content and a checksum for verification.")
    public ToolIO.ReadResponse read(@JsonPropertyDescription("Reason for reading the file.") String requestReason,
                                    @JsonPropertyDescription("The absolute path to the file to read.") String filePath) {
        log.info("Reading file: {}", filePath);
        try {
            final var path = Path.of(filePath);
            if (!Files.exists(path)) {
                return ToolIO.ReadResponse.builder()
                        .error("File not found: " + filePath)
                        .build();
            }
            final var content = Files.readString(path, StandardCharsets.UTF_8);
            final var checksum = calculateChecksum(content.getBytes(StandardCharsets.UTF_8));
            return ToolIO.ReadResponse.builder()
                    .content(content)
                    .checksum(checksum)
                    .build();
        }
        catch (Exception e) {
            final var errorMessage = "Error reading file: " + AgentUtils.rootCause(e).getMessage();
            log.error(errorMessage, e);
            return ToolIO.ReadResponse.builder()
                    .error(errorMessage)
                    .build();
        }
    }

    @Tool("Search and replace text in a file. Use this for precise text substitutions.")
    public ToolIO.SearchReplaceResponse searchReplace(@JsonPropertyDescription("The absolute path to the file to edit.") String filePath,
                                                      @JsonPropertyDescription("The exact text to search for in the file. Include enough context to make the match unique.") String searchText,
                                                      @JsonPropertyDescription("The text to replace the search text with.") String replaceText,
                                                      @JsonPropertyDescription("Which occurrence to replace: 1 for first, 2 for second, etc. Use 0 or negative to replace ALL occurrences.") int occurrence,
                                                      @JsonPropertyDescription("The expected SHA-256 checksum of the file before editing. Use the checksum from a previous read operation.") String expectedChecksum,
                                                      @JsonPropertyDescription("Reason for editing the file.") String requestReason) {
        log.info("Search and replace in file: {}", filePath);
        try {
            final var path = Path.of(filePath);
            if (!Files.exists(path)) {
                return ToolIO.SearchReplaceResponse.builder()
                        .success(false)
                        .error("File not found: " + filePath)
                        .build();
            }

            final var content = Files.readString(path, StandardCharsets.UTF_8);
            final var currentChecksum = calculateChecksum(content.getBytes(StandardCharsets.UTF_8));

            if (!currentChecksum.equals(expectedChecksum)) {
                return ToolIO.SearchReplaceResponse.builder()
                        .success(false)
                        .error("Checksum mismatch. Expected: " + expectedChecksum + ", Actual: "
                                + currentChecksum + ". Re-read the file to get the current checksum.")
                        .build();
            }

            if (Strings.isNullOrEmpty(searchText)) {
                return ToolIO.SearchReplaceResponse.builder()
                        .success(false)
                        .error("Search text cannot be empty.")
                        .build();
            }

            if (!content.contains(searchText)) {
                return ToolIO.SearchReplaceResponse.builder()
                        .success(false)
                        .error("Search text not found in file. Make sure the search text matches exactly, including whitespace and newlines.")
                        .build();
            }

            final var replacement = replaceText != null ? replaceText : "";
            String newContent;
            int replacementCount;

            if (occurrence <= 0) {
                // Replace all occurrences
                newContent = content.replace(searchText, replacement);
                // Count occurrences
                int count = 0;
                int index = 0;
                while ((index = content.indexOf(searchText, index)) != -1) {
                    count++;
                    index += searchText.length();
                }
                replacementCount = count;
            }
            else {
                // Replace specific occurrence
                int index = -1;
                for (int i = 0; i < occurrence; i++) {
                    index = content.indexOf(searchText, index + 1);
                    if (index == -1) {
                        return ToolIO.SearchReplaceResponse.builder()
                                .success(false)
                                .error("Occurrence " + occurrence + " not found. Only found " + i + " occurrence(s).")
                                .build();
                    }
                }
                newContent = content.substring(0, index) + replacement
                        + content.substring(index + searchText.length());
                replacementCount = 1;
            }

            // Write the new content
            Files.writeString(path, newContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            final var newChecksum = calculateChecksum(newContent.getBytes(StandardCharsets.UTF_8));

            return ToolIO.SearchReplaceResponse.builder()
                    .success(true)
                    .newChecksum(newChecksum)
                    .replacementCount(replacementCount)
                    .build();
        }
        catch (Exception e) {
            final var errorMessage = "Error during search and replace: " + AgentUtils.rootCause(e).getMessage();
            log.error(errorMessage, e);
            return ToolIO.SearchReplaceResponse.builder()
                    .success(false)
                    .error(errorMessage)
                    .build();
        }
    }

    @Tool("Write content to a file. This will create the file if it doesn't exist, or overwrite it if it does.")
    public ToolIO.WriteResponse write(@JsonPropertyDescription("The absolute path to the file to write.") String filePath,
                                      @JsonPropertyDescription("The content to write to the file.") String content,
                                      @JsonPropertyDescription("Reason for writing the file.") String requestReason) {
        log.info("Writing file: {}", filePath);
        try {
            final var path = Path.of(filePath);
            if (path.getParent() != null && !Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }

            Files.writeString(path,
                              content,
                              StandardOpenOption.CREATE,
                              StandardOpenOption.TRUNCATE_EXISTING);
            final var bytes = content.getBytes(StandardCharsets.UTF_8);
            final var checksum = calculateChecksum(bytes);
            return ToolIO.WriteResponse.builder()
                    .success(true)
                    .bytesWritten(bytes.length)
                    .checksum(checksum)
                    .build();
        }
        catch (Exception e) {
            final var errorMessage = "Error writing file: " + AgentUtils.rootCause(e).getMessage();
            log.error(errorMessage, e);
            return ToolIO.WriteResponse.builder()
                    .success(false)
                    .error(errorMessage)
                    .build();
        }
    }

    private String calculateChecksum(byte[] content) {
        try {
            final var digest = MessageDigest.getInstance("SHA-256");
            final var encodedhash = digest.digest(content);
            return HexFormat.of().formatHex(encodedhash);
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    /**
     * Validates unified diff patch format.
     * Returns an error message if validation fails, empty otherwise.
     */
    @SuppressWarnings("java:S3776")
    private Optional<String> validatePatchFormat(String patchContent) {
        if (patchContent == null || patchContent.isBlank()) {
            return Optional.of("Patch content is empty");
        }

        final var lines = patchContent.split("\n", -1);
        boolean hasHunkHeader = false;
        int expectedOldLines = 0;
        int expectedNewLines = 0;
        int actualOldLines = 0;
        int actualNewLines = 0;

        for (int i = 0; i < lines.length; i++) {
            final var line = lines[i];

            // Skip file headers
            if (line.startsWith("---") || line.startsWith("+++")) {
                continue;
            }

            // Check for hunk header
            final Matcher matcher = HUNK_PATTERN.matcher(line);
            if (matcher.find()) {
                // Verify previous hunk was complete
                if (hasHunkHeader && (actualOldLines != expectedOldLines || actualNewLines != expectedNewLines)) {
                    return Optional.of(String.format(
                                                     "Hunk line count mismatch. Expected %d old/%d new lines, got %d old/%d new lines. "
                                                             + "Make sure context lines start with a space character.",
                                                     expectedOldLines,
                                                     expectedNewLines,
                                                     actualOldLines,
                                                     actualNewLines));
                }
                hasHunkHeader = true;
                expectedOldLines = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 1;
                expectedNewLines = matcher.group(4) != null ? Integer.parseInt(matcher.group(4)) : 1;
                actualOldLines = 0;
                actualNewLines = 0;
                continue;
            }

            if (hasHunkHeader) {
                if (line.startsWith(" ")) {
                    // Context line - counts for both old and new
                    actualOldLines++;
                    actualNewLines++;
                }
                else if (line.startsWith("-")) {
                    actualOldLines++;
                }
                else if (line.startsWith("+")) {
                    actualNewLines++;
                }
                else if (!line.isEmpty()) {
                    // Line doesn't start with space, -, or + and is not empty
                    return Optional.of(String.format(
                                                     "Line %d in patch is malformed. Context lines MUST start with a space character, "
                                                             + "removed lines with '-', added lines with '+'. Got: '%s'",
                                                     i + 1,
                                                     line.length() > 50 ? line.substring(0, 50) + "..." : line));
                }
            }
        }

        // Verify final hunk
        if (hasHunkHeader && (actualOldLines != expectedOldLines || actualNewLines != expectedNewLines)) {
            return Optional.of(String.format(
                                             "Hunk line count mismatch. Expected %d old/%d new lines, got %d old/%d new lines. "
                                                     + "Make sure context lines start with a space character.",
                                             expectedOldLines,
                                             expectedNewLines,
                                             actualOldLines,
                                             actualNewLines));
        }

        if (!hasHunkHeader) {
            return Optional.of("No hunk header (@@ ... @@) found in patch");
        }

        return Optional.empty();
    }

}
