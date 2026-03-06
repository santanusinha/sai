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
package io.appform.sai.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.utils.JsonUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

class SkillParserTest {

    private Path tempDir;
    private SkillParser parser;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("skill-parser-test-");
        ObjectMapper mapper = JsonUtils.createMapper();
        parser = new SkillParser(mapper);
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(path -> {
            try {
                Files.delete(path);
            }
            catch (IOException e) {
                // Ignore
            }
        });
    }

    @Test
    void testParseMetadataOnly() throws IOException {
        Path skillDir = tempDir.resolve("test-skill");
        Files.createDirectory(skillDir);

        String skillContent = """
                ---
                name: test-skill
                description: A test skill
                ---

                # Instructions
                """;

        Files.writeString(skillDir.resolve("SKILL.md"), skillContent);

        SkillMetadata metadata = parser.parseMetadata(skillDir);

        assertNotNull(metadata);
        assertEquals("test-skill", metadata.getName());
        assertEquals("A test skill", metadata.getDescription());
    }

    @Test
    void testParseMissingFrontmatter() throws IOException {
        Path skillDir = tempDir.resolve("test-skill");
        Files.createDirectory(skillDir);

        String skillContent = """
                # No frontmatter here

                Just instructions.
                """;

        Files.writeString(skillDir.resolve("SKILL.md"), skillContent);

        assertThrows(IllegalArgumentException.class, () -> parser.parse(skillDir));
    }

    @Test
    void testParseNameMismatch() throws IOException {
        Path skillDir = tempDir.resolve("test-skill");
        Files.createDirectory(skillDir);

        String skillContent = """
                ---
                name: wrong-name
                description: Name doesn't match directory
                ---

                # Instructions
                """;

        Files.writeString(skillDir.resolve("SKILL.md"), skillContent);

        assertThrows(IllegalArgumentException.class, () -> parser.parse(skillDir));
    }

    @Test
    void testParseValidSkill() throws IOException {
        // Create a skill directory with SKILL.md
        Path skillDir = tempDir.resolve("test-skill");
        Files.createDirectory(skillDir);

        String skillContent = """
                ---
                name: test-skill
                description: A test skill for unit testing
                license: Apache-2.0
                ---

                # Test Skill

                This is the instruction body.
                """;

        Files.writeString(skillDir.resolve("SKILL.md"), skillContent);

        AgentSkill skill = parser.parse(skillDir);

        assertNotNull(skill);
        assertEquals("test-skill", skill.getName());
        assertEquals("A test skill for unit testing", skill.getDescription());
        assertTrue(skill.getInstructions().contains("This is the instruction body"));
    }

    @Test
    void testParseWithSubdirectories() throws IOException {
        Path skillDir = tempDir.resolve("test-skill");
        Files.createDirectories(skillDir.resolve("references"));
        Files.createDirectories(skillDir.resolve("scripts"));

        String skillContent = """
                ---
                name: test-skill
                description: Skill with resources
                ---

                # Instructions
                """;

        Files.writeString(skillDir.resolve("SKILL.md"), skillContent);
        Files.writeString(skillDir.resolve("references/doc.md"), "# Documentation");
        Files.writeString(skillDir.resolve("scripts/run.sh"), "#!/bin/bash");

        AgentSkill skill = parser.parse(skillDir);

        assertNotNull(skill.getReferenceFiles());
        assertNotNull(skill.getScriptFiles());
        assertTrue(skill.getReferenceFiles().containsKey("doc.md"));
        assertTrue(skill.getScriptFiles().containsKey("run.sh"));
    }
}
