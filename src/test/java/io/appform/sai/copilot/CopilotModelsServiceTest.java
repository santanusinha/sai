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
package io.appform.sai.copilot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

class CopilotModelsServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // -------------------------------------------------------------------------
    // CopilotModel deserialization
    // -------------------------------------------------------------------------

    private static OkHttpClient mockHttpClientWithBody(int statusCode, String body) {
        final var httpClient = mock(OkHttpClient.class);
        final var call = mock(Call.class);
        try {
            final var request = new Request.Builder()
                    .url("https://api.githubcopilot.com/models")
                    .build();
            final var response = new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(statusCode)
                    .message(statusCode == 200 ? "OK" : "Error")
                    .body(ResponseBody.create(body,
                                              MediaType.parse("application/json")))
                    .build();
            when(httpClient.newCall(any(Request.class))).thenReturn(call);
            when(call.execute()).thenReturn(response);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        return httpClient;
    }

    @Test
    void baseUrlAndPathConstantsAreCorrect() {
        assertEquals("https://api.githubcopilot.com", CopilotModelsService.COPILOT_API_BASE_URL);
        assertEquals("/models", CopilotModelsService.MODELS_PATH);
    }

    // -------------------------------------------------------------------------
    // ModelsResponse deserialization
    // -------------------------------------------------------------------------

    @Test
    void copilotModelDeserializesAllFields() throws Exception {
        final var json = """
                {
                  "id": "gpt-4o",
                  "name": "GPT-4o",
                  "vendor": "OpenAI",
                  "preview": false,
                  "model_picker_enabled": true,
                  "capabilities": {
                    "type": "chat",
                    "family": "gpt-4o",
                    "limits": {
                      "max_context_window_tokens": 128000,
                      "max_output_tokens": 4096,
                      "max_prompt_tokens": 100000
                    },
                    "supports": {
                      "tool_calls": true,
                      "parallel_tool_calls": true
                    }
                  }
                }
                """;

        final var model = MAPPER.readValue(json, CopilotModel.class);

        assertEquals("gpt-4o", model.getId());
        assertEquals("GPT-4o", model.getName());
        assertEquals("OpenAI", model.getVendor());
        assertTrue(model.isModelPickerEnabled());

        final var caps = model.getCapabilities();
        assertNotNull(caps);
        assertEquals("chat", caps.getType());
        assertEquals("gpt-4o", caps.getFamily());
        assertEquals(128000, caps.getLimits().getMaxContextWindowTokens());
        assertTrue(caps.getSupports().isToolCalls());
    }

    @Test
    void copilotModelIgnoresUnknownFields() throws Exception {
        final var json = """
                {"id": "x", "unknown_field": "ignored"}
                """;
        final var model = MAPPER.readValue(json, CopilotModel.class);
        assertEquals("x", model.getId());
    }

    // -------------------------------------------------------------------------
    // CopilotModelsService.listModels – successful call
    // -------------------------------------------------------------------------

    @Test
    void listModelsReturnsEmptyListWhenDataIsNull() throws Exception {
        final var httpClient = mockHttpClientWithBody(200, "{\"object\":\"list\"}");
        final var service = new CopilotModelsService(httpClient, MAPPER);

        final var models = service.listModels("fake-copilot-token");

        assertNotNull(models);
        assertTrue(models.isEmpty());
    }

    @Test
    void listModelsReturnsParsedModels() throws Exception {
        final var responseJson = """
                {
                  "data": [
                    {"id": "gpt-4o", "name": "GPT-4o", "vendor": "OpenAI"},
                    {"id": "claude-haiku", "name": "Claude Haiku", "vendor": "Anthropic"}
                  ]
                }
                """;
        final var httpClient = mockHttpClientWithBody(200, responseJson);
        final var service = new CopilotModelsService(httpClient, MAPPER);

        final var models = service.listModels("fake-copilot-token");

        assertEquals(2, models.size());
        assertEquals("gpt-4o", models.get(0).getId());
        assertEquals("Anthropic", models.get(1).getVendor());
    }

    // -------------------------------------------------------------------------
    // CopilotModelsService.listModels – error handling
    // -------------------------------------------------------------------------

    @Test
    void listModelsThrowsOnNonSuccessfulHttpStatus() {
        final var httpClient = mockHttpClientWithBody(401, "Unauthorized");
        final var service = new CopilotModelsService(httpClient, MAPPER);

        assertThrows(IOException.class, () -> service.listModels("bad-token"));
    }

    // -------------------------------------------------------------------------
    // CopilotModelsService constants
    // -------------------------------------------------------------------------

    @Test
    void modelsResponseDeserializesDataArray() throws Exception {
        final var json = """
                {
                  "object": "list",
                  "data": [
                    {"id": "gpt-4o", "name": "GPT-4o", "vendor": "OpenAI"},
                    {"id": "claude-sonnet", "name": "Claude Sonnet", "vendor": "Anthropic"}
                  ]
                }
                """;
        final var resp = MAPPER.readValue(json, CopilotModelsService.ModelsResponse.class);
        assertEquals(2, resp.getData().size());
        assertEquals("gpt-4o", resp.getData().get(0).getId());
    }

    // -------------------------------------------------------------------------
    // CopilotTokenExchanger constants
    // -------------------------------------------------------------------------

    @Test
    void modelsResponseEmptyDataReturnedAsEmptyList() throws Exception {
        final var json = """
                {"object": "list", "data": []}
                """;
        final var resp = MAPPER.readValue(json, CopilotModelsService.ModelsResponse.class);
        assertNotNull(resp.getData());
        assertTrue(resp.getData().isEmpty());
    }

    @Test
    void tokenExchangerParsesTokenFromResponse() throws Exception {
        final var httpClient = mockHttpClientWithBody(200, "{\"token\":\"ghs_copilot_abc\",\"expires_at\":9999}");
        final var token = CopilotTokenExchanger.fetchCopilotToken("github_token", httpClient, MAPPER);
        assertEquals("ghs_copilot_abc", token);
    }

    @Test
    void tokenExchangerThrowsOnNonSuccessfulHttpStatus() {
        final var httpClient = mockHttpClientWithBody(403, "Forbidden");
        assertThrows(IOException.class,
                     () -> CopilotTokenExchanger.fetchCopilotToken("token", httpClient, MAPPER));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @Test
    void tokenExchangerUrlConstantsAreCorrect() {
        assertEquals("https://api.github.com", CopilotTokenExchanger.GITHUB_API_BASE_URL);
        assertEquals("/copilot_internal/v2/token", CopilotTokenExchanger.TOKEN_PATH);
    }
}
