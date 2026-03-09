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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agent.Fact;
import com.phonepe.sentinelai.core.agent.FactList;
import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ProcessingMode;
import com.phonepe.sentinelai.core.agent.SystemPrompt;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.utils.ToolUtils;

import io.appform.sai.SaiAgent;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

/**
 * Extension that provides Agent Skills capabilities to SAI
 */
@Slf4j
public class AgentSkillsExtension implements AgentExtension<String, String, SaiAgent> {

    private final SkillRegistry registry;
    private final Map<String, ExecutableTool> tools;
    private final boolean singleSkillMode;

    @Builder
    public AgentSkillsExtension(SkillRegistry registry, ObjectMapper mapper, boolean singleSkillMode) {
        this.registry = registry;
        this.singleSkillMode = singleSkillMode;
        this.tools = Map.copyOf(ToolUtils.readTools(this));
    }

    @Tool("Activate a skill by name to access its instructions and capabilities")
    public String activateSkill(
                                @JsonPropertyDescription("Name of the skill to activate") String skillName) {

        log.info("Activating skill: {}", skillName);

        final var skillOpt = registry.loadSkill(skillName);
        if (skillOpt.isEmpty()) {
            return "Error: Skill '" + skillName + "' not found or failed to load.";
        }

        final var skill = skillOpt.get();

        final var response = new StringBuilder();
        response.append("# Skill Activated: ").append(skill.getName()).append("\n\n");
        response.append(skill.getInstructions());

        // Add information about available resources
        if (skill.getReferenceFiles() != null && !skill.getReferenceFiles().isEmpty()) {
            response.append("\n\n## Available Reference Files\n");
            skill.getReferenceFiles().keySet().forEach(ref -> response.append("- ").append(ref).append("\n"));
        }

        if (skill.getScriptFiles() != null && !skill.getScriptFiles().isEmpty()) {
            response.append("\n\n## Available Scripts\n");
            skill.getScriptFiles().keySet().forEach(script -> response.append("- ").append(script).append("\n"));
        }

        if (skill.getAssetFiles() != null && !skill.getAssetFiles().isEmpty()) {
            response.append("\n\n## Available Assets\n");
            skill.getAssetFiles().keySet().forEach(asset -> response.append("- ").append(asset).append("\n"));
        }

        log.debug("Skill '{}' activated successfully", skillName);
        return response.toString();
    }

    @Override
    public ExtensionPromptSchema additionalSystemPrompts(
                                                         String request,
                                                         AgentRunContext<String> metadata,
                                                         SaiAgent agent,
                                                         ProcessingMode processingMode) {

        final var tasks = new ArrayList<SystemPrompt.Task>();

        // In single-skill mode, don't add skill discovery - just inject the skill directly
        if (!singleSkillMode && registry.hasSkills()) {
            // Add task for skill discovery and activation
            tasks.add(SystemPrompt.Task.builder()
                    .objective("Check if any available skills are relevant to the user's request")
                    .instructions("""
                            Before proceeding with the main task:
                            1. Review the available skills catalog below
                            2. If a skill seems relevant to the user's request, use the activate_skill tool
                            3. Once activated, follow the skill's instructions carefully
                            4. You can activate multiple skills if needed
                            """)
                    .tool(tools.values().stream()
                            .map(tool -> SystemPrompt.ToolSummary.builder()
                                    .name(tool.getToolDefinition().getId())
                                    .description(tool.getToolDefinition().getDescription())
                                    .build())
                            .toList())
                    .build());
        }

        final var hints = new ArrayList<>();

        // Add skills catalog as a hint
        if (!singleSkillMode && registry.hasSkills()) {
            hints.add(registry.formatCatalog());
        }

        return new ExtensionPromptSchema(tasks, hints);
    }

    @Override
    public List<FactList> facts(String request, AgentRunContext<String> context, SaiAgent agent) {

        final var facts = new ArrayList<FactList>();

        // In single-skill mode, inject the loaded skill directly as facts
        if (singleSkillMode) {
            registry.getSkillNames().stream()
                    .findFirst()
                    .flatMap(registry::getLoadedSkill)
                    .ifPresent(skill -> {
                        facts.add(new FactList(
                                               "Active Skill: " + skill.getName(),
                                               List.of(new Fact("Instructions", skill.getInstructions()))));
                    });
        }

        return facts;
    }

    @Tool("List all available skills with their descriptions")
    public String listSkills() {
        if (!registry.hasSkills()) {
            return "No skills are currently available.";
        }

        return registry.formatCatalog();
    }

    @Override
    public String name() {
        return "agent-skills";
    }

    @Override
    public Optional<ModelOutputDefinition> outputSchema(ProcessingMode processingMode) {
        return Optional.empty();
    }

    @Tool("Read a reference file from an activated skill")
    public String readSkillReference(
                                     @JsonPropertyDescription("Name of the skill") String skillName,
                                     @JsonPropertyDescription("Path to the reference file within the skill's references/ directory") String referenceFile) {

        final var skillOpt = registry.getLoadedSkill(skillName);
        if (skillOpt.isEmpty()) {
            return "Error: Skill '" + skillName + "' is not loaded. Activate it first.";
        }

        final var skill = skillOpt.get();
        if (skill.getReferenceFiles() == null) {
            return "Error: Skill '" + skillName + "' has no reference files.";
        }

        if (!skill.getReferenceFiles().containsKey(referenceFile)) {
            return "Error: Reference file '"
                    + referenceFile
                    + "' not found in skill '"
                    + skillName
                    + "'.";
        }

        try {
            return Files.readString(skill.getReferenceFiles().get(referenceFile));
        }
        catch (Exception e) {
            log.error("Failed to read reference file {}: {}", referenceFile, e.getMessage());
            return "Error reading reference file: " + e.getMessage();
        }
    }

    @Override
    public Map<String, ExecutableTool> tools() {
        return singleSkillMode ? Collections.emptyMap() : tools;
    }
}
