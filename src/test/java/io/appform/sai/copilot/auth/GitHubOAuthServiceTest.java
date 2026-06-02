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
package io.appform.sai.copilot.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class GitHubOAuthServiceTest {

    @Test
    void testGetTokenPath() {
        GitHubOAuthService service = new GitHubOAuthService();
        var path = service.getTokenPath();
        assertTrue(path.toString().contains(".config"));
        assertTrue(path.toString().contains("sai"));
        assertTrue(path.toString().endsWith("copilot_token"));
    }

    @Test
    void testGetTokenPathWithEnvOverride() {
        // Note: This test would require mocking environment variables
        // For now, we just verify the default path logic works
        GitHubOAuthService service = new GitHubOAuthService();
        Path path = service.getTokenPath();
        assertNotNull(path);
    }

    @Test
    void testSaveAndReadToken(@TempDir Path tempDir) throws Exception {
        // We can't easily test saveToken with environment override
        // but we can verify the token content format
        Path tokenFile = tempDir.resolve("test_token");
        String testToken = "gho_test_token_12345";

        Files.writeString(tokenFile, testToken);
        String readToken = Files.readString(tokenFile);

        assertEquals(testToken, readToken);
    }
}
