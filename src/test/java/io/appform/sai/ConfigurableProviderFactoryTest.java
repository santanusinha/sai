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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.appform.sai.config.ProviderEntry;
import io.appform.sai.config.SettingsConfig;

import org.junit.jupiter.api.Test;

import java.util.Map;

import okhttp3.OkHttpClient;

/**
 * Tests for {@link ConfigurableProviderFactory} focusing on the config-driven vs env-var fallback
 * resolution logic (§2.5 fix: openai/azure providers should check settings.yaml before env vars).
 *
 * <p>These tests verify that:
 * <ul>
 * <li>When a provider is defined in settings.yaml, the factory builds from config (not env vars).</li>
 * <li>When no config entry exists, the factory falls back to env-var behavior (and throws if
 * env vars are missing).</li>
 * <li>Unknown providers without config throw with a helpful message.</li>
 * <li>Config entries with missing type or unsupported type throw appropriately.</li>
 * </ul>
 *
 * <p>Building {@code SimpleOpenAI}/{@code SimpleOpenAIAzure} does not require network access —
 * the builder just constructs the client object. So we can verify config-driven construction
 * succeeds even without env vars set.
 */
class ConfigurableProviderFactoryTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient httpClient = new OkHttpClient.Builder().build();

    private static void assertEquals(String expected, String actual) {
        assertTrue(expected.equals(actual), "Expected '" + expected + "' but got '" + actual + "'");
    }

    @Test
    void getAzureFromConfigSucceedsWithoutEnvVars() {
        final var config = SettingsConfig.builder()
                .providers(Map.of("azure",
                                  ProviderEntry.builder()
                                          .type("azure")
                                          .endpoint("https://my-resource.openai.azure.com")
                                          .apiKey("azure-key")
                                          .apiVersion("2024-10-21")
                                          .build()))
                .build();
        final var factory = new ConfigurableProviderFactory("azure", mapper, httpClient, config);
        final var services = factory.get("my-deployment");
        assertNotNull(services);
    }

    @Test
    void getAzureWithoutConfigAndWithoutEnvThrows() {
        final var config = SettingsConfig.builder().build();
        final var factory = new ConfigurableProviderFactory("azure", mapper, httpClient, config);
        final var ex = assertThrows(IllegalArgumentException.class, () -> factory.get("gpt-4o"));
        assertTrue(ex.getMessage().contains("AZURE_ENDPOINT"));
    }

    @Test
    void getConfigEntryWithMissingTypeThrows() {
        final var config = SettingsConfig.builder()
                .providers(Map.of("openai",
                                  ProviderEntry.builder()
                                          .endpoint("https://api.openai.com/v1")
                                          .apiKey("sk-key")
                                          .build()))
                .build();
        final var factory = new ConfigurableProviderFactory("openai", mapper, httpClient, config);
        final var ex = assertThrows(IllegalArgumentException.class, () -> factory.get("gpt-4o"));
        assertTrue(ex.getMessage().contains("no 'type' field"));
    }

    @Test
    void getConfigEntryWithUnsupportedTypeThrows() {
        final var config = SettingsConfig.builder()
                .providers(Map.of("myprov",
                                  ProviderEntry.builder()
                                          .type("unsupported")
                                          .endpoint("https://example.com")
                                          .apiKey("key")
                                          .build()))
                .build();
        final var factory = new ConfigurableProviderFactory("myprov", mapper, httpClient, config);
        final var ex = assertThrows(IllegalArgumentException.class, () -> factory.get("model-x"));
        assertTrue(ex.getMessage().contains("Unsupported provider type"));
    }

    @Test
    void getCustomProviderFromConfigSucceeds() {
        final var config = SettingsConfig.builder()
                .providers(Map.of("openrouter",
                                  ProviderEntry.builder()
                                          .type("openai")
                                          .endpoint("https://openrouter.ai/api/v1")
                                          .apiKey("or-key")
                                          .build()))
                .build();
        final var factory = new ConfigurableProviderFactory("openrouter", mapper, httpClient, config);
        final var services = factory.get("anthropic/claude-3.5-sonnet");
        assertNotNull(services);
    }

    @Test
    void getNullConfigUsesEnvVarFallback() {
        final var factory = new ConfigurableProviderFactory("unknown-prov",
                                                            mapper,
                                                            httpClient,
                                                            null);
        final var ex = assertThrows(IllegalArgumentException.class, () -> factory.get("model"));
        assertTrue(ex.getMessage().contains("Unsupported provider"));
    }

    @Test
    void getOpenAiFromConfigSucceedsWithoutEnvVars() {
        final var config = SettingsConfig.builder()
                .providers(Map.of("openai",
                                  ProviderEntry.builder()
                                          .type("openai")
                                          .endpoint("https://api.openai.com/v1")
                                          .apiKey("sk-test-key")
                                          .build()))
                .build();
        final var factory = new ConfigurableProviderFactory("openai", mapper, httpClient, config);
        final var services = factory.get("gpt-4o");
        assertNotNull(services);
    }

    @Test
    void getOpenAiFromConfigWithExtraHeadersSucceeds() {
        final var config = SettingsConfig.builder()
                .providers(Map.of("openai",
                                  ProviderEntry.builder()
                                          .type("openai")
                                          .endpoint("https://api.openai.com/v1")
                                          .apiKey("sk-test-key")
                                          .extraHeaders(Map.of("Helicone-Auth", "Bearer sk-helicone"))
                                          .build()))
                .build();
        final var factory = new ConfigurableProviderFactory("openai", mapper, httpClient, config);
        final var services = factory.get("gpt-4o");
        assertNotNull(services);
    }

    @Test
    void getOpenAiFromConfigWithOrgAndProjectSucceeds() {
        final var config = SettingsConfig.builder()
                .providers(Map.of("openai",
                                  ProviderEntry.builder()
                                          .type("openai")
                                          .endpoint("https://api.openai.com/v1")
                                          .apiKey("sk-test-key")
                                          .organizationId("org-123")
                                          .projectId("proj-456")
                                          .build()))
                .build();
        final var factory = new ConfigurableProviderFactory("openai", mapper, httpClient, config);
        final var services = factory.get("gpt-4o");
        assertNotNull(services);
    }

    @Test
    void getOpenAiWithoutConfigAndWithoutEnvThrows() {
        final var config = SettingsConfig.builder().build();
        final var factory = new ConfigurableProviderFactory("openai", mapper, httpClient, config);
        final var ex = assertThrows(IllegalArgumentException.class, () -> factory.get("gpt-4o"));
        assertTrue(ex.getMessage().contains("OPENAI_API_KEY"));
    }

    @Test
    void getProviderNameAccessibleViaGetter() {
        final var factory = new ConfigurableProviderFactory("openai", mapper, httpClient, null);
        assertEquals("openai", factory.getProvider());
    }

    @Test
    void getUnknownProviderWithoutConfigThrows() {
        final var config = SettingsConfig.builder().build();
        final var factory = new ConfigurableProviderFactory("unknown-provider",
                                                            mapper,
                                                            httpClient,
                                                            config);
        final var ex = assertThrows(IllegalArgumentException.class, () -> factory.get("model-x"));
        assertTrue(ex.getMessage().contains("Unsupported provider"));
        assertTrue(ex.getMessage().contains("settings.yaml"));
    }
}
