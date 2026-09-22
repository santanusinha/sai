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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.appform.sai.term.Printer;

import org.junit.jupiter.api.Test;

import java.util.List;

class CoreToolBoxToolFilterTest {

    private static final List<String> ALL_TOOLS = List.of("bash", "editFile", "readFile", "writeFile");

    @Test
    void blanksAreIgnored() {
        final var names = exposedNames(List.of("", "   ", "bash"));
        assertEquals(1, names.size());
        assertTrue(names.contains("bash"));
    }

    @Test
    void emptyListAllowsAllTools() {
        final var names = exposedNames(List.of());
        assertEquals(4, names.size());
        ALL_TOOLS.forEach(name -> assertTrue(names.contains(name), "Missing tool: " + name));
    }

    @Test
    void filtersToOnlyNamedTools() {
        final var names = exposedNames(List.of("bash"));
        assertEquals(1, names.size());
        assertTrue(names.contains("bash"));
        assertFalse(names.contains("readFile"));
    }

    @Test
    void filtersToSubset() {
        final var names = exposedNames(List.of("bash", "readFile"));
        assertEquals(2, names.size());
        assertTrue(names.contains("bash"));
        assertTrue(names.contains("readFile"));
        assertFalse(names.contains("editFile"));
        assertFalse(names.contains("writeFile"));
    }

    @Test
    void matchingIsCaseInsensitive() {
        final var names = exposedNames(List.of("Bash", "READFILE"));
        assertEquals(2, names.size());
        assertTrue(names.contains("bash"));
        assertTrue(names.contains("readFile"));
    }

    @Test
    void nullAllowsAllTools() {
        final var names = exposedNames(null);
        assertEquals(4, names.size());
        ALL_TOOLS.forEach(name -> assertTrue(names.contains(name), "Missing tool: " + name));
    }

    @Test
    void unknownNamesYieldEmptyMap() {
        final var names = exposedNames(List.of("does-not-exist"));
        assertTrue(names.isEmpty());
    }

    private java.util.Set<String> exposedNames(List<String> allowedTools) {
        return new CoreToolBox((Printer) null, allowedTools).tools().values().stream()
                .map(tool -> tool.getToolDefinition().getName())
                .collect(java.util.stream.Collectors.toSet());
    }
}
