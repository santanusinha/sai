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
package io.appform.sai.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class AgentConfigLoaderTest {

    private ObjectMapper jsonMapper;

    @Test
    void leavesUnresolvedPlaceholdersIntact() throws Exception {
        final var personaPath = resourcePath("personas/unresolved-vars.yaml");
        final var config = AgentConfigLoader.load(personaPath, jsonMapper);

        assertNotNull(config);
        assertEquals("partial-agent", config.getAgentId());
        assertTrue(config.getModel().contains("${UNDEFINED_MODEL_VAR}"),
                   "unresolved placeholder should remain in place");
        assertTrue(config.getPrompt().contains("${UNDEFINED_PROMPT_VAR}"),
                   "unresolved placeholder in prompt should remain in place");
    }

    @Test
    void loadsJsonWithEnvVarsSubstituted(@TempDir Path tempDir) throws Exception {
        final var personaFile = tempDir.resolve("test.json");
        final var pathValue = System.getenv("PATH");
        assertNotNull(pathValue, "PATH env var must be set");

        Files.writeString(personaFile,
                          String.join("\n",
                                      "{",
                                      "  \"agentId\": \"env-test-json\",",
                                      "  \"name\": \"Env Test JSON\",",
                                      "  \"description\": \"Agent with PATH=${PATH}\",",
                                      "  \"model\": \"openai/gpt-4o\"",
                                      "}"));

        final var config = AgentConfigLoader.load(personaFile, jsonMapper);

        assertEquals("env-test-json", config.getAgentId());
        assertTrue(config.getDescription().contains(pathValue),
                   "description should contain the value of PATH env var");
    }

    @Test
    void loadsYamlWithEnvVarsSubstituted(@TempDir Path tempDir) throws Exception {
        final var personaFile = tempDir.resolve("test.yaml");
        final var pathValue = System.getenv("PATH");
        assertNotNull(pathValue, "PATH env var must be set");

        Files.writeString(personaFile,
                          String.join("\n",
                                      "agentId: env-test",
                                      "name: Env Test Agent",
                                      "description: Agent with PATH=${PATH}",
                                      "model: openai/gpt-4o",
                                      ""));

        final var config = AgentConfigLoader.load(personaFile, jsonMapper);

        assertEquals("env-test", config.getAgentId());
        assertTrue(config.getDescription().contains(pathValue),
                   "description should contain the value of PATH env var");
    }

    @Test
    void loadsYamlWithNoPlaceholders() throws Exception {
        final var personaPath = resourcePath("personas/no-vars.yaml");
        final var config = AgentConfigLoader.load(personaPath, jsonMapper);

        assertNotNull(config);
        assertEquals("no-vars-agent", config.getAgentId());
        assertEquals("Static Agent", config.getName());
        assertEquals("openai/gpt-4o", config.getModel());
    }

    @BeforeEach
    void setUp() {
        jsonMapper = new ObjectMapper();
        jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Test
    void throwsWhenFileDoesNotExist(@TempDir Path tempDir) {
        final var missing = tempDir.resolve("missing.yaml");
        final var ex = assertThrows(IllegalArgumentException.class,
                                    () -> AgentConfigLoader.load(missing, jsonMapper));
        assertTrue(ex.getMessage().contains("does not exist"));
    }

    private Path resourcePath(String relativePath) throws Exception {
        final URL resource = getClass().getClassLoader().getResource(relativePath);
        assertNotNull(resource, "Test resource not found: " + relativePath);
        return Paths.get(resource.toURI());
    }
}
