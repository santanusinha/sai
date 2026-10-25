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
package io.appform.sai.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.appform.sai.tools.ToolIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.SneakyThrows;

/**
 * Verifies the cross-tool checksum workflow: the agent reads a file, modifies it with the
 * bash tool, and passes the bash-computed sha256sum to the next write/edit call. The
 * checksums computed by {@code sha256sum} and by {@link FileIO} must agree, so the call
 * succeeds instead of failing with a checksum mismatch.
 */
class BashChecksumInteropTest {

    private Path tempDir;
    private Path testFile;

    private static void deleteRecursive(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            try (var entries = Files.list(path)) {
                for (var entry : entries.toList()) {
                    deleteRecursive(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    @Test
    @SneakyThrows
    void bashChecksumMatchesFileIOChecksum() {
        Files.writeString(testFile, "line1\nline2\nline3", StandardCharsets.UTF_8);

        assertEquals(sha256sum(testFile), FileIO.readFile(testFile.toString(), 1, -1, false).getChecksum());
    }

    @Test
    @SneakyThrows
    void checksumMatchesSha256sumOnBinaryFile() {
        // The checksum is over raw file bytes, so it must equal sha256sum even for a
        // file that is not valid UTF-8.
        final var bytes = new byte[]{
                0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE, (byte) 0xC3, 0x28
        };
        Files.write(testFile, bytes);

        assertEquals(sha256sum(testFile), FileIO.readFile(testFile.toString(), 1, -1, false).getChecksum());
    }

    @Test
    @SneakyThrows
    void editAcceptsChecksumComputedByBashAfterBashModification() {
        Files.writeString(testFile, "line1\nline2\nline3", StandardCharsets.UTF_8);

        runBash("sed -i 's/line2/replaced by bash/' " + testFile);
        final var bashChecksum = sha256sum(testFile);

        final var edits = List.of(
                                  ToolIO.FileEditOperation.builder().startLine(1).endLine(1).content("edited").build());
        final var response = FileIO.editFile(testFile.toString(), edits, bashChecksum);

        assertNull(response.getError());
        assertNotNull(response.getNewChecksum());
        final var written = Files.readString(testFile);
        assertTrue(written.contains("edited"), written);
        assertTrue(written.contains("replaced by bash"), written);
    }

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory(Path.of("target"), "bash-interop-");
        testFile = tempDir.resolve("interop.txt");
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteRecursive(tempDir);
    }

    @Test
    @SneakyThrows
    void writeAcceptsBashChecksumOnBinaryFile() {
        // A binary file changed by bash must accept a write carrying the sha256sum of the
        // new raw bytes.
        final var bytes = new byte[]{
                0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE, (byte) 0xC3, 0x28
        };
        Files.write(testFile, bytes);

        runBash("printf '\\x0a\\x0b\\xff' > " + testFile);
        final var bashChecksum = sha256sum(testFile);

        final var response = FileIO.write(testFile.toString(), "text content", bashChecksum);

        assertNull(response.getError());
        assertEquals("text content", Files.readString(testFile));
    }

    @Test
    @SneakyThrows
    void writeAcceptsChecksumComputedByBashAfterBashModification() {
        Files.writeString(testFile, "original content", StandardCharsets.UTF_8);

        // The bash tool changes the file, then reports its new checksum.
        final var preBashChecksum = FileIO.readFile(testFile.toString(), 1, -1, false).getChecksum();
        runBash("printf 'changed by bash' > " + testFile);
        final var bashChecksum = sha256sum(testFile);

        // A write with the pre-bash checksum is rejected.
        final var rejected = FileIO.write(testFile.toString(), "agent content", preBashChecksum);
        assertNotNull(rejected.getError());
        assertTrue(rejected.getError().contains("Checksum mismatch"), rejected.getError());

        // A write with the bash-computed checksum succeeds.
        final var response = FileIO.write(testFile.toString(), "agent content", bashChecksum);

        assertNull(response.getError());
        assertEquals("agent content", Files.readString(testFile));
    }

    private void runBash(String command) throws IOException, InterruptedException {
        final var process = new ProcessBuilder("bash", "-c", command).start();
        final var errors = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), "bash failed: " + errors);
    }

    private String sha256sum(Path path) throws IOException, InterruptedException {
        final var process = new ProcessBuilder("sha256sum", path.toString()).start();
        final var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), "sha256sum failed: " + output);
        return output.split("\\s+")[0].trim();
    }
}
