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
package io.appform.sai.term;

import com.google.common.base.Strings;
import com.phonepe.sentinelai.filesystem.skills.AgentSkillsExtension;

import io.appform.sai.SaiAgent;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * Prints the startup banner: tool and skill names as two aligned columns.
 *
 * <p>Skill names come from the {@link AgentSkillsExtension#listSkills()} catalog text.
 * The extension exposes no metadata API, so the catalog lines
 * ({@code - **name**: description}, emitted by {@code SkillRegistry.formatCatalog()})
 * are parsed here. This is the only place that knows the catalog format.
 */
@Slf4j
public final class StartupBanner {

    private StartupBanner() {
        // Utility class
    }

    /**
     * Prints the banner. Shown on new and resumed sessions when not headless.
     *
     * @param agent           the active agent (tool names come from its toolboxes)
     * @param skillsExtension the skills extension (may be {@code null})
     * @param printer         the active printer
     */
    public static void print(final SaiAgent agent,
                             final AgentSkillsExtension<String, String, SaiAgent> skillsExtension,
                             final Printer printer) {
        final var C = Printer.Colours.CYAN;
        final var G = Printer.Colours.GRAY;
        final var W = Printer.Colours.WHITE;
        final var R = Printer.Colours.RESET;

        final var toolNames = agent.tools().values().stream()
                .map(t -> t.getToolDefinition().getName())
                .sorted()
                .toList();

        final var skillNames = extractSkillNames(skillsExtension);

        // Render two aligned columns: tools on the left, skills on the right
        final var sb = new StringBuilder("\n");
        final int rows = Math.max(toolNames.size(), skillNames.size());
        final int colWidth = toolNames.stream().mapToInt(String::length).max().orElse(0) + 4;

        final var toolHeader = "\uD83D\uDEE0  Tools";
        final var skillHeader = skillNames.isEmpty() ? "" : "\uD83C\uDFA8  Skills";
        // header — emoji adds 1 extra visual char, compensate with -1 padding
        sb.append(C).append(toolHeader).append(R);
        if (!skillHeader.isEmpty()) {
            // pad to column width (emoji counts as 1 char in length but 2 visually, so -1)
            final int pad = colWidth - toolHeader.length() + 1;
            sb.append(" ".repeat(Math.max(1, pad))).append(C).append(skillHeader).append(R);
        }
        sb.append("\n");

        for (int i = 0; i < rows; i++) {
            final var tool = i < toolNames.size() ? "  \u2022 " + toolNames.get(i) : "";
            final var skil = i < skillNames.size() ? "  \u2022 " + skillNames.get(i) : "";
            sb.append(G).append(tool).append(R);
            if (!skil.isEmpty()) {
                final int pad = colWidth - tool.length();
                sb.append(" ".repeat(Math.max(1, pad))).append(G).append(skil).append(R);
            }
            sb.append("\n");
        }
        sb.append("\n");
        printer.print(Printer.raw(sb.toString()));
    }

    /**
     * Extracts skill names from the catalog text. Catalog lines look like
     * {@code - **name**: description}. Returns an empty list when the extension is
     * absent or no skills exist.
     *
     * @param skillsExtension the skills extension (may be {@code null})
     * @return sorted skill names
     */
    private static List<String> extractSkillNames(
                                                  final AgentSkillsExtension<String, String, SaiAgent> skillsExtension) {
        final var names = new ArrayList<String>();
        if (skillsExtension == null) {
            return names;
        }
        try {
            final var catalog = skillsExtension.listSkills();
            if (!Strings.isNullOrEmpty(catalog) && !catalog.startsWith("No skills")) {
                catalog.lines()
                        .filter(l -> l.startsWith("- **"))
                        .map(l -> l.replaceFirst("^- \\*\\*(.+?)\\*\\*.*", "$1"))
                        .sorted()
                        .forEach(names::add);
            }
        }
        catch (Exception e) {
            log.warn("Failed to read skills catalog for banner: {}", e.getMessage());
        }
        return names;
    }
}
