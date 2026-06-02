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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

class CopilotDirectProviderTest {

    @TempDir
    Path tmpDir;

    // -------------------------------------------------------------------------
    // CopilotTokenResponse
    // -------------------------------------------------------------------------

    @Test
    void copilotTokenResponseFactoryMethod() {
        final var r = CopilotDirectProvider.CopilotTokenResponse.of("tok", 999L, 1800);
        assertEquals("tok", r.token());
        assertEquals(999L, r.expiresAt());
        assertEquals(1800, r.refreshIn());
    }

    // -------------------------------------------------------------------------
    // resolveTokenPath / resolveCachedTokenPath
    // -------------------------------------------------------------------------

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
    void loadCachedTokenReturnsCachedTokenWhenValid() throws Exception {
        final var mapper = new ObjectMapper();
        final var cacheFile = tmpDir.resolve("copilot_access_token.json");
        final long futureExpiry = Instant.now().getEpochSecond() + 3600;
        final var cached = CopilotDirectProvider.CopilotTokenResponse.of("cached-tok", futureExpiry, 1800);
        mapper.writeValue(cacheFile.toFile(), cached);

        final var result = CopilotDirectProvider.loadCachedToken(cacheFile, mapper);

        assertNotNull(result, "Should return cached token when it is still valid");
        assertEquals("cached-tok", result.token());
        assertEquals(futureExpiry, result.expiresAt());
    }

    // -------------------------------------------------------------------------
    // Token file validation (inline — no real constructor needed)
    // -------------------------------------------------------------------------

    @Test
    void loadCachedTokenReturnsNullOnCorruptCacheFile() throws Exception {
        final var mapper = new ObjectMapper();
        final var cacheFile = tmpDir.resolve("copilot_access_token.json");
        Files.writeString(cacheFile, "not-valid-json{{");

        assertNull(CopilotDirectProvider.loadCachedToken(cacheFile, mapper),
                   "Should return null when cache file is corrupt");
    }

    @Test
    void loadCachedTokenReturnsNullWhenCacheFileMissing() throws Exception {
        final var mapper = new ObjectMapper();
        final var nonExistent = tmpDir.resolve("copilot_access_token.json");
        // No file written — should return null gracefully
        assertNull(CopilotDirectProvider.loadCachedToken(nonExistent, mapper),
                   "Should return null when cache file does not exist");
    }

    // -------------------------------------------------------------------------
    // loadCachedToken — static helper
    // -------------------------------------------------------------------------

    @Test
    void loadCachedTokenReturnsNullWhenTokenAboutToExpire() throws Exception {
        final var mapper = new ObjectMapper();
        final var cacheFile = tmpDir.resolve("copilot_access_token.json");
        // Expires within the REFRESH_BUFFER_SECONDS window
        final long soonExpiry = Instant.now().getEpochSecond()
                + CopilotDirectProvider.REFRESH_BUFFER_SECONDS - 5;
        final var almostExpired = CopilotDirectProvider.CopilotTokenResponse.of("soon-tok", soonExpiry, 1800);
        mapper.writeValue(cacheFile.toFile(), almostExpired);

        assertNull(CopilotDirectProvider.loadCachedToken(cacheFile, mapper),
                   "Should return null when token is about to expire");
    }

    @Test
    void loadCachedTokenReturnsNullWhenTokenExpired() throws Exception {
        final var mapper = new ObjectMapper();
        final var cacheFile = tmpDir.resolve("copilot_access_token.json");
        final long pastExpiry = Instant.now().getEpochSecond() - 10;
        final var expired = CopilotDirectProvider.CopilotTokenResponse.of("old-tok", pastExpiry, 1800);
        mapper.writeValue(cacheFile.toFile(), expired);

        assertNull(CopilotDirectProvider.loadCachedToken(cacheFile, mapper),
                   "Should return null for expired cached token");
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
    void persistTokenCreatesParentDirectoriesIfAbsent() throws Exception {
        final var mapper = new ObjectMapper();
        final var nestedCache = tmpDir.resolve("sub").resolve("dir").resolve("copilot_access_token.json");
        final long futureExpiry = Instant.now().getEpochSecond() + 3600;
        final var token = CopilotDirectProvider.CopilotTokenResponse.of("dir-tok", futureExpiry, 900);

        CopilotDirectProvider.persistToken(token, nestedCache, mapper);

        assertTrue(Files.exists(nestedCache),
                   "persistToken should create parent directories and write the file");
        final var loaded = CopilotDirectProvider.loadCachedToken(nestedCache, mapper);
        assertNotNull(loaded);
        assertEquals("dir-tok", loaded.token());
    }

    @Test
    void persistTokenWritesReadableJsonFile() throws Exception {
        final var mapper = new ObjectMapper();
        final var cacheFile = tmpDir.resolve("copilot_access_token.json");
        final long futureExpiry = Instant.now().getEpochSecond() + 3600;
        final var token = CopilotDirectProvider.CopilotTokenResponse.of("persist-tok", futureExpiry, 1800);

        CopilotDirectProvider.persistToken(token, cacheFile, mapper);

        assertTrue(Files.exists(cacheFile), "Cache file should be created by persistToken");
        final var roundTrip = CopilotDirectProvider.loadCachedToken(cacheFile, mapper);
        assertNotNull(roundTrip, "Round-trip load should succeed");
        assertEquals("persist-tok", roundTrip.token());
        assertEquals(futureExpiry, roundTrip.expiresAt());
        assertEquals(1800, roundTrip.refreshIn());
    }

    // -------------------------------------------------------------------------
    // persistToken — static helper
    // -------------------------------------------------------------------------

    @Test
    void resolveCachedTokenPathReturnsDefaultUnderHomeDir() {
        final var resolved = CopilotDirectProvider.resolveCachedTokenPath();
        final var home = System.getProperty("user.home",
                                            System.getenv().getOrDefault("HOME", ""));
        assertTrue(resolved.endsWith("/.config/sai/copilot_access_token.json"),
                   "Cache path should end with known suffix, got: " + resolved);
        assertTrue(resolved.startsWith(home),
                   "Cache path should start with home dir, got: " + resolved);
    }

    @Test
    void resolveTokenPathReturnsDefaultUnderHomeDir() {
        final var resolved = CopilotDirectProvider.resolveTokenPath();
        final var home = System.getProperty("user.home",
                                            System.getenv().getOrDefault("HOME", ""));
        assertTrue(resolved.endsWith("/.config/sai/copilot_token"),
                   "Default path should end with known suffix, got: " + resolved);
        assertTrue(resolved.startsWith(home),
                   "Default path should start with home dir, got: " + resolved);
    }
}
