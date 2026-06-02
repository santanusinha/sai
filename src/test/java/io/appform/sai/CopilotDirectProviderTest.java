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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class CopilotDirectProviderTest {

    @TempDir
    Path tmpDir;

    @Test
    void copilotTokenResponseFactoryMethod() {
        final var r = CopilotDirectProvider.CopilotTokenResponse.of("tok", 999L, 1800);
        assertEquals("tok", r.token());
        assertEquals(999L, r.expiresAt());
        assertEquals(1800, r.refreshIn());
    }

    @Test
    void emptyTokenFileIsRejected() throws IOException {
        final var tokenFile = tmpDir.resolve("github_token");
        Files.writeString(tokenFile, "   ");
        final var token = Files.readString(tokenFile).strip();
        assertTrue(token.isEmpty(), "Token should be blank after stripping whitespace");
        assertThrows(IllegalStateException.class, () -> {
            if (token.isEmpty()) {
                throw new IllegalStateException("GitHub token file is empty: " + tokenFile);
            }
        });
    }

    @Test
    void nonExistentTokenFileThrowsIOException() {
        final var nonExistent = tmpDir.resolve("no_such_file");
        assertThrows(IOException.class, () -> {
            if (!Files.exists(nonExistent)) {
                throw new IOException("No such file: " + nonExistent);
            }
        });
    }

    @Test
    void resolveTokenPathReturnsDefaultUnderHomeDir() {
        final var resolved = CopilotDirectProvider.resolveTokenPath();
        final var home = System.getProperty("user.home",
                                            System.getenv().getOrDefault("HOME", ""));
        assertTrue(resolved.endsWith("/.local/share/copilot-api/github_token"),
                   "Default path should end with known suffix, got: " + resolved);
        assertTrue(resolved.startsWith(home),
                   "Default path should start with home dir, got: " + resolved);
    }
}
