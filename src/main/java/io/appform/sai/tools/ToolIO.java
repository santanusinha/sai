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

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.UtilityClass;
import lombok.extern.jackson.Jacksonized;

@UtilityClass
public class ToolIO {

    @Value
    @JsonClassDescription("Input for the bash tool.")
    @Builder
    @Jacksonized
    public static class BashRequest {
        @JsonPropertyDescription("Reason for requesting the tool. This is shown to the user for informational purposes.")
        String requestReason;
        @JsonPropertyDescription("The bash command to execute. This should be a single line command. Multi-line commands are not supported.")
        String command;
        @JsonPropertyDescription("The timeout for the bash command execution in seconds. If the command does not complete within this time, it will be terminated. Default is 30 seconds. Adjust this if you expect the command to take longer to execute, but be cautious as setting it too high may lead to hanging processes.")
        int timeoutSeconds;
    }

    @Value
    @Builder
    @Jacksonized
    public static class BashResponse {
        @JsonPropertyDescription("The status code of the bash command execution. A status code of 0 indicates success, while any non-zero value indicates an error.")
        int statusCode;

        @JsonPropertyDescription("The stdout output of the bash command execution.")
        String stdout;

        @JsonPropertyDescription("The stderr output of the bash command execution.")
        String stderr;
    }

    @Value
    @JsonClassDescription("Input for the edit tool.")
    @Builder
    @Jacksonized
    public static class EditRequest {
        @JsonPropertyDescription("The absolute path to the file to edit.")
        String path;
        @JsonPropertyDescription("The patch content in unified diff format. CRITICAL: Every context line (unchanged lines) MUST start with a single space character. Lines starting with '-' are removed, lines starting with '+' are added. Example: ' unchanged line' (note the leading space), '-old line', '+new line'. The hunk header line counts must match actual lines.")
        String patchContent;
        @JsonPropertyDescription("The expected SHA-256 checksum of the file before editing.")
        String expectedChecksum;
        @JsonPropertyDescription("Reason for editing the file.")
        String requestReason;
    }

    @Value
    @Builder
    @Jacksonized
    public static class EditResponse {
        @JsonPropertyDescription("Whether the edit was successful.")
        boolean success;
        @JsonPropertyDescription("The new SHA-256 checksum of the file after editing.")
        String newChecksum;
        @JsonPropertyDescription("Error message if any.")
        String error;
    }

    /**
     * Operations supported by the line-based edit tool.
     */
    public enum LineEditOperation {
        /** Insert content before the specified line */
        INSERT_BEFORE,
        /** Insert content after the specified line */
        INSERT_AFTER,
        /** Replace lines from startLine to endLine (inclusive) with new content */
        REPLACE,
        /** Delete lines from startLine to endLine (inclusive) */
        DELETE
    }

    @Value
    @JsonClassDescription("Input for the line-based edit tool. Use this to insert, replace, or delete lines in a file.")
    @Builder
    @Jacksonized
    public static class LineEditRequest {
        @JsonPropertyDescription("The absolute path to the file to edit.")
        String path;
        @JsonPropertyDescription("The operation to perform: INSERT_BEFORE, INSERT_AFTER, REPLACE, or DELETE.")
        LineEditOperation operation;
        @JsonPropertyDescription("The starting line number (1-indexed). For INSERT_BEFORE/INSERT_AFTER, this is the reference line.")
        int startLine;
        @JsonPropertyDescription("The ending line number (1-indexed, inclusive). Only used for REPLACE and DELETE operations. If not specified, defaults to startLine.")
        Integer endLine;
        @JsonPropertyDescription("The content to insert or replace with. Not used for DELETE operation.")
        String content;
        @JsonPropertyDescription("The expected SHA-256 checksum of the file before editing. Use the checksum from a previous read operation.")
        String expectedChecksum;
        @JsonPropertyDescription("Reason for editing the file.")
        String requestReason;
    }

    @Value
    @Builder
    @Jacksonized
    public static class LineEditResponse {
        @JsonPropertyDescription("Whether the line edit was successful.")
        boolean success;
        @JsonPropertyDescription("The new SHA-256 checksum of the file after editing.")
        String newChecksum;
        @JsonPropertyDescription("Error message if any.")
        String error;
    }

    @Value
    @JsonClassDescription("Input for the read tool.")
    @Builder
    @Jacksonized
    public static class ReadRequest {
        @JsonPropertyDescription("The absolute path to the file to read.")
        String path;
        @JsonPropertyDescription("Reason for reading the file.")
        String requestReason;
    }

    // ==================== Search & Replace Tool ====================

    @Value
    @Builder
    @Jacksonized
    public static class ReadResponse {
        @JsonPropertyDescription("The content of the file.")
        String content;
        @JsonPropertyDescription("The SHA-256 checksum of the file content.")
        String checksum;
        @JsonPropertyDescription("Error message if any.")
        String error;
    }

    @Value
    @JsonClassDescription("Input for the search and replace tool. Use this to find and replace text in a file.")
    @Builder
    @Jacksonized
    public static class SearchReplaceRequest {
        @JsonPropertyDescription("The absolute path to the file to edit.")
        String path;
        @JsonPropertyDescription("The exact text to search for in the file. Include enough context to make the match unique.")
        String searchText;
        @JsonPropertyDescription("The text to replace the search text with.")
        String replaceText;
        @JsonPropertyDescription("Which occurrence to replace: 1 for first, 2 for second, etc. Use 0 or negative to replace ALL occurrences.")
        int occurrence;
        @JsonPropertyDescription("The expected SHA-256 checksum of the file before editing. Use the checksum from a previous read operation.")
        String expectedChecksum;
        @JsonPropertyDescription("Reason for editing the file.")
        String requestReason;
    }

    // ==================== Line-Based Edit Tool ====================

    @Value
    @Builder
    @Jacksonized
    public static class SearchReplaceResponse {
        @JsonPropertyDescription("Whether the search and replace was successful.")
        boolean success;
        @JsonPropertyDescription("The new SHA-256 checksum of the file after editing.")
        String newChecksum;
        @JsonPropertyDescription("Number of replacements made.")
        int replacementCount;
        @JsonPropertyDescription("Error message if any.")
        String error;
    }

    @Value
    @JsonClassDescription("Input for the write tool.")
    @Builder
    @Jacksonized
    public static class WriteRequest {
        @JsonPropertyDescription("The absolute path to the file to write.")
        String path;
        @JsonPropertyDescription("The content to write to the file.")
        String content;
        @JsonPropertyDescription("Reason for writing the file.")
        String requestReason;
    }

    @Value
    @Builder
    @Jacksonized
    public static class WriteResponse {
        @JsonPropertyDescription("Whether the write was successful.")
        boolean success;
        @JsonPropertyDescription("The number of bytes written to the file.")
        long bytesWritten;
        @JsonPropertyDescription("The SHA-256 checksum of the written content.")
        String checksum;
        @JsonPropertyDescription("Error message if any.")
        String error;
    }
}
