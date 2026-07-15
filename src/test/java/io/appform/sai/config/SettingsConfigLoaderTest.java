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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class SettingsConfigLoaderTest {

    @Test
    void loadDirectoryStringReturnsEmptyWhenFileAbsent(@TempDir Path tempDir) {
        final var config = SettingsConfigLoader.load(tempDir.toString());
        assertNotNull(config);
        assertTrue(config.isEmpty());
    }

    @Test
    void loadEmptyYamlReturnsEmpty(@TempDir Path tempDir) throws Exception {
        final var file = tempDir.resolve("settings.yaml");
        Files.writeString(file, "");
        final var config = SettingsConfigLoader.load(file);
        assertNotNull(config);
        assertTrue(config.isEmpty());
    }

    @Test
    void loadFileNotExistsReturnsEmpty(@TempDir Path tempDir) {
        final var missing = tempDir.resolve("settings.yaml");
        final var config = SettingsConfigLoader.load(missing);
        assertNotNull(config);
        assertTrue(config.isEmpty());
    }

    @Test
    void loadMalformedYamlReturnsEmpty(@TempDir Path tempDir) throws Exception {
        final var file = tempDir.resolve("settings.yaml");
        Files.writeString(file,
                          String.join("\n",
                                      "providers:",
                                      "  openai:",
                                      "    type: openai",
                                      "    endpoint: [unclosed bracket",
                                      ""));
        final var config = SettingsConfigLoader.load(file);
        assertNotNull(config);
        assertTrue(config.isEmpty());
    }

    @Test
    void loadValidYamlWithProviders(@TempDir Path tempDir) throws Exception {
        final var file = tempDir.resolve("settings.yaml");
        Files.writeString(file,
                          String.join("\n",
                                      "providers:",
                                      "  openai:",
                                      "    type: openai",
                                      "    endpoint: https://api.openai.com/v1",
                                      "    apiKey: sk-test-key",
                                      "    tuning:",
                                      "      temperature: 0.3",
                                      "    models:",
                                      "      gpt-4o:",
                                      "        tuning:",
                                      "          maxTokens: 4096",
                                      "        modes:",
                                      "          coding:",
                                      "            tuning:",
                                      "              temperature: 0.2",
                                      ""));
        final var config = SettingsConfigLoader.load(file);
        assertNotNull(config);
        final var openai = config.getProvider("openai").orElse(null);
        assertNotNull(openai);
        assertEquals("openai", openai.getType());
        assertEquals("https://api.openai.com/v1", openai.getEndpoint());
        assertEquals("sk-test-key", openai.getApiKey());
        assertNotNull(openai.getTuning());
        assertEquals(0.3f, openai.getTuning().getTemperature());
        final var gpt4o = openai.getModel("gpt-4o");
        assertNotNull(gpt4o);
        assertEquals(4096, gpt4o.getTuning().getMaxTokens());
        final var coding = gpt4o.getMode("coding");
        assertNotNull(coding);
        assertEquals(0.2f, coding.getTuning().getTemperature());
    }

    @Test
    void loadYamlWithAzureProviderAndApiVersion(@TempDir Path tempDir) throws Exception {
        final var file = tempDir.resolve("settings.yaml");
        Files.writeString(file,
                          String.join("\n",
                                      "providers:",
                                      "  azure:",
                                      "    type: azure",
                                      "    endpoint: https://my-resource.openai.azure.com",
                                      "    apiKey: azure-key",
                                      "    apiVersion: 2024-10-21",
                                      ""));
        final var config = SettingsConfigLoader.load(file);
        assertNotNull(config);
        final var azure = config.getProvider("azure").orElse(null);
        assertNotNull(azure);
        assertEquals("azure", azure.getType());
        assertEquals("https://my-resource.openai.azure.com", azure.getEndpoint());
        assertEquals("azure-key", azure.getApiKey());
        assertEquals("2024-10-21", azure.getApiVersion());
    }

    @Test
    void loadYamlWithDefaultInterpolation(@TempDir Path tempDir) throws Exception {
        final var file = tempDir.resolve("settings.yaml");
        Files.writeString(file,
                          String.join("\n",
                                      "providers:",
                                      "  openai:",
                                      "    type: openai",
                                      "    endpoint: ${NONEXISTENT_VAR:-https://fallback.example.com/v1}",
                                      "    apiKey: test-key",
                                      ""));
        final var config = SettingsConfigLoader.load(file);
        assertNotNull(config);
        final var openai = config.getProvider("openai").orElse(null);
        assertNotNull(openai);
        assertEquals("https://fallback.example.com/v1", openai.getEndpoint());
    }

    @Test
    void loadYamlWithEnvVarInterpolation(@TempDir Path tempDir) throws Exception {
        final var pathValue = System.getenv("PATH");
        assertNotNull(pathValue, "PATH env var must be set for this test");

        final var file = tempDir.resolve("settings.yaml");
        Files.writeString(file,
                          String.join("\n",
                                      "providers:",
                                      "  openai:",
                                      "    type: openai",
                                      "    endpoint: ${OPENAI_ENDPOINT:-https://api.openai.com/v1}",
                                      "    apiKey: ${PATH}",
                                      ""));
        final var config = SettingsConfigLoader.load(file);
        assertNotNull(config);
        final var openai = config.getProvider("openai").orElse(null);
        assertNotNull(openai);
        assertEquals("https://api.openai.com/v1", openai.getEndpoint());
        assertEquals(pathValue, openai.getApiKey());
    }

    @Test
    void loadYamlWithExtraHeadersMap(@TempDir Path tempDir) throws Exception {
        final var file = tempDir.resolve("settings.yaml");
        Files.writeString(file,
                          String.join("\n",
                                      "providers:",
                                      "  openai:",
                                      "    type: openai",
                                      "    endpoint: https://api.openai.com/v1",
                                      "    apiKey: test-key",
                                      "    extraHeaders:",
                                      "      Helicone-Auth: Bearer sk-helicone-xyz",
                                      "      X-Custom-Header: my-value",
                                      ""));
        final var config = SettingsConfigLoader.load(file);
        assertNotNull(config);
        final var openai = config.getProvider("openai").orElse(null);
        assertNotNull(openai);
        assertNotNull(openai.getExtraHeaders());
        assertEquals("Bearer sk-helicone-xyz", openai.getExtraHeaders().get("Helicone-Auth"));
        assertEquals("my-value", openai.getExtraHeaders().get("X-Custom-Header"));
    }

    @Test
    void loadYamlWithOpenRouterSlashModelId(@TempDir Path tempDir) throws Exception {
        final var file = tempDir.resolve("settings.yaml");
        Files.writeString(file,
                          String.join("\n",
                                      "providers:",
                                      "  openrouter:",
                                      "    type: openai",
                                      "    endpoint: https://openrouter.ai/api/v1",
                                      "    apiKey: or-key",
                                      "    models:",
                                      "      \"anthropic/claude-3.5-sonnet\":",
                                      "        tuning:",
                                      "          temperature: 0.7",
                                      "          contextWindowSize: 200000",
                                      ""));
        final var config = SettingsConfigLoader.load(file);
        assertNotNull(config);
        final var openrouter = config.getProvider("openrouter").orElse(null);
        assertNotNull(openrouter);
        final var model = openrouter.getModel("anthropic/claude-3.5-sonnet");
        assertNotNull(model);
        assertEquals(0.7f, model.getTuning().getTemperature());
        assertEquals(200000, model.getTuning().getContextWindowSize());
    }

    @Test
    void loadYamlWithUnknownFieldsIgnored(@TempDir Path tempDir) throws Exception {
        final var file = tempDir.resolve("settings.yaml");
        Files.writeString(file,
                          String.join("\n",
                                      "providers:",
                                      "  openai:",
                                      "    type: openai",
                                      "    endpoint: https://api.openai.com/v1",
                                      "    apiKey: test-key",
                                      "    unknownField: should-be-ignored",
                                      "    futureField: true",
                                      ""));
        final var config = SettingsConfigLoader.load(file);
        assertNotNull(config);
        final var openai = config.getProvider("openai").orElse(null);
        assertNotNull(openai);
        assertEquals("openai", openai.getType());
    }

    @Test
    void loadYamlWithUnsetVarPreservesPlaceholder(@TempDir Path tempDir) throws Exception {
        final var file = tempDir.resolve("settings.yaml");
        Files.writeString(file,
                          String.join("\n",
                                      "providers:",
                                      "  openai:",
                                      "    type: openai",
                                      "    endpoint: ${SAI_TEST_NONEXISTENT_12345}",
                                      "    apiKey: test-key",
                                      ""));
        final var config = SettingsConfigLoader.load(file);
        assertNotNull(config);
        final var openai = config.getProvider("openai").orElse(null);
        assertNotNull(openai);
        assertEquals("${SAI_TEST_NONEXISTENT_12345}", openai.getEndpoint());
    }

    @Test
    void substituteEnvVarsEmptyString() {
        assertEquals("", SettingsConfigLoader.substituteEnvVars(""));
    }

    @Test
    void substituteEnvVarsMultipleVars() {
        final var pathValue = System.getenv("PATH");
        assertNotNull(pathValue);
        final var result = SettingsConfigLoader
                .substituteEnvVars("a: ${PATH}\nb: ${SAI_NONEXISTENT:-fallback}\nc: ${SAI_NONEXISTENT2}");
        assertTrue(result.contains("a: " + pathValue));
        assertTrue(result.contains("b: fallback"));
        assertTrue(result.contains("c: ${SAI_NONEXISTENT2}"));
    }

    @Test
    void substituteEnvVarsNoPlaceholders() {
        final var input = "plain text with no placeholders";
        assertEquals(input, SettingsConfigLoader.substituteEnvVars(input));
    }

    @Test
    void substituteEnvVarsSimpleReplacement() {
        final var pathValue = System.getenv("PATH");
        assertNotNull(pathValue);
        final var result = SettingsConfigLoader.substituteEnvVars("key: ${PATH}");
        assertEquals("key: " + pathValue, result);
    }

    @Test
    void substituteEnvVarsUnsetReturnsPlaceholder() {
        final var result = SettingsConfigLoader.substituteEnvVars("key: ${SAI_NONEXISTENT_VAR_99999}");
        assertEquals("key: ${SAI_NONEXISTENT_VAR_99999}", result);
    }

    @Test
    void substituteEnvVarsWithDefault() {
        final var result = SettingsConfigLoader
                .substituteEnvVars("endpoint: ${SAI_NONEXISTENT_VAR_67890:-https://default.example.com}");
        assertEquals("endpoint: https://default.example.com", result);
    }
}
