/*
 * Copyright (c) 2026 Original Author(s)
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
package io.appform.sai.cli.slash;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SlashCommandDispatcherTest {

    // ── tokenize() ───────────────────────────────────────────────────────────

    @Test
    void tokenizeBlankReturnsEmpty() {
        assertArrayEquals(new String[0], SlashCommandDispatcher.tokenize("   "));
    }

    @Test
    void tokenizeDoubleQuotedArg() {
        assertArrayEquals(
                          new String[]{
                                  "persona", "/path/with spaces/file.yaml"
                          },
                          SlashCommandDispatcher.tokenize("persona \"/path/with spaces/file.yaml\""));
    }

    @Test
    void tokenizeEmptyStringReturnsEmpty() {
        assertEquals(0, SlashCommandDispatcher.tokenize("").length);
    }

    @Test
    void tokenizeLeadingAndTrailingSpaces() {
        assertArrayEquals(new String[]{
                "help"
        }, SlashCommandDispatcher.tokenize("  help  "));
    }

    @Test
    void tokenizeMultipleSpacesBetweenTokens() {
        assertArrayEquals(
                          new String[]{
                                  "model", "copilot-proxy/gpt-4o"
                          },
                          SlashCommandDispatcher.tokenize("model   copilot-proxy/gpt-4o"));
    }

    @Test
    void tokenizeNullReturnsEmpty() {
        assertArrayEquals(new String[0], SlashCommandDispatcher.tokenize(null));
    }

    @Test
    void tokenizeSimpleArgs() {
        assertArrayEquals(
                          new String[]{
                                  "model", "copilot-proxy/claude-haiku-4.5"
                          },
                          SlashCommandDispatcher.tokenize("model copilot-proxy/claude-haiku-4.5"));
    }

    @Test
    void tokenizeSingleQuotedArg() {
        assertArrayEquals(
                          new String[]{
                                  "persona", "/path/with spaces/file.yaml"
                          },
                          SlashCommandDispatcher.tokenize("persona '/path/with spaces/file.yaml'"));
    }

    @Test
    void tokenizeSingleToken() {
        assertArrayEquals(new String[]{
                "help"
        }, SlashCommandDispatcher.tokenize("help"));
    }
}
