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

import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.core.utils.AgentUtils;

import io.appform.sai.Printer;
import io.appform.sai.models.Actor;
import io.appform.sai.models.Severity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class CoreToolBox implements ToolBox {

    private final Printer printer;

    @Tool("Run bash commands on the system where the agent is running. This is the core tool and should be used for any command execution needs. Use this tool to run any bash command, including those that interact with the file system, network, or other system resources. Be cautious while using this tool, as it can execute any command on the system. Do not operate on files mentioned in .gitignore")
    public ToolIO.BashResponse bash(ToolIO.BashRequest request) {
        // We run the command in the same thread and print the update to the printer. We can do streaming later if needed.
        // once completed we return the status code and the output as response.
        if (null != printer) {
            printer.print(Printer.Update.builder()
                    .actor(Actor.SYSTEM)
                    .severity(Severity.INFO)
                    .data("Executing bash command: " + request.getCommand())
                    .build());
        }
        log.info("Executing bash command: {}", request.getCommand());
        try {
            final var commandOutput = new BashCommandRunner(request.getCommand(),
                                                            Duration.ofSeconds(request.getTimeoutSeconds()))
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


    @Tool("Apply a patch to a file. Verifies file hasn't changed using checksum before applying.")
    public ToolIO.EditResponse edit(ToolIO.EditRequest request) {
        if (null != printer) {
            printer.print(Printer.Update.builder()
                    .actor(Actor.SYSTEM)
                    .severity(Severity.INFO)
                    .data("Editing file: " + request.getPath())
                    .build());
        }
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

            // Create temporary patch file
            final var patchFile = Files.createTempFile("sai-patch-", ".diff");
            Files.writeString(patchFile,
                              request.getPatchContent(),
                              StandardOpenOption.CREATE,
                              StandardOpenOption.TRUNCATE_EXISTING);

            try {
                // Apply patch using patch command
                // Command: patch <file> <patchFile>
                // Note: 'patch' command might need -u (unified) or normal. Usually it auto-detects.
                // We'll use: patch -u -N <file> <patchFile> (unified, allow creating new files?) No, just default.
                // Actually, standard usage: patch [options] [originalfile [patchfile]]

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

    @Override
    public String name() {
        return "core";
    }

    @Tool("Read a file from the local filesystem. Returns content and a checksum for verification.")
    public ToolIO.ReadResponse read(ToolIO.ReadRequest request) {
        if (null != printer) {
            printer.print(Printer.Update.builder()
                    .actor(Actor.SYSTEM)
                    .severity(Severity.INFO)
                    .data("Reading file: " + request.getPath())
                    .build());
        }
        log.info("Reading file: {}", request.getPath());
        try {
            final var path = Path.of(request.getPath());
            if (!Files.exists(path)) {
                return ToolIO.ReadResponse.builder()
                        .error("File not found: " + request.getPath())
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

    @Tool("Write content to a file. This will create the file if it doesn't exist, or overwrite it if it does.")
    public ToolIO.WriteResponse write(ToolIO.WriteRequest request) {
        if (null != printer) {
            printer.print(Printer.Update.builder()
                    .actor(Actor.SYSTEM)
                    .severity(Severity.INFO)
                    .data("Writing file: " + request.getPath())
                    .build());
        }
        log.info("Writing file: {}", request.getPath());
        try {
            final var path = Path.of(request.getPath());
            if (path.getParent() != null && !Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }

            Files.writeString(path,
                              request.getContent(),
                              StandardOpenOption.CREATE,
                              StandardOpenOption.TRUNCATE_EXISTING);
            final var checksum = calculateChecksum(request.getContent().getBytes(StandardCharsets.UTF_8));
            return ToolIO.WriteResponse.builder()
                    .success(true)
                    .bytesWritten(request.getContent().getBytes(StandardCharsets.UTF_8).length)
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

}
