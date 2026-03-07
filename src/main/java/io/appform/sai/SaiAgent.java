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

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.tools.ExecutableTool;

import java.util.List;
import java.util.Map;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SaiAgent extends Agent<String, String, SaiAgent> {
    private static final String TERM_PRINT_EXTRA = """
                your responses will be printed on a terminal, so please add color coding in the response for better readability. Use green color for important information, yellow for warnings, and red for errors. You can also use blue for general information and cyan for examples. Remember to reset the color after each colored section to avoid affecting the rest of the text. Apply some syntax highlighting to code snippets in your responses, using cyan for keywords, green for strings, and yellow for comments and so on.
            """;

    private final String name;

    public SaiAgent(
                    final String name,
                    final AgentConfig config,
                    final Settings settings,
                    @NonNull AgentSetup setup,
                    final String systemPrompt,
                    List<AgentExtension<String, String, SaiAgent>> extensions,
                    Map<String, ExecutableTool> knownTools) {
        super(
              String.class,
              augmentPrompt(systemPrompt, config, settings),
              setup,
              extensions,
              knownTools
        );
        this.name = name;
    }

    private static String augmentPrompt(String systemPrompt, final AgentConfig config, final Settings settings) {
        return systemPrompt
                + """
                        Extra instructions to get your work done:

                        - Your name is %s.
                        - You can use /tmp/sai/< session id>/scrath/ directory to store any temporary files you need during the conversation.No need to clean up the files, they will be automatically deleted after the session ends.
                        - If working on a coding project, first look for and read any AGENTS.md file in the project directory for any specific instructions or guidelines related to the project. If such a file exists, make sure to follow the instructions provided in it while working on the project.
                        - The current working directory is: %s
                        """
                        .formatted(config.getName(), settings.getWorkDir());
    }

    @Override
    public String name() {
        return name;
    }

}
