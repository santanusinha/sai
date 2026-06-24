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
package io.appform.sai.cli.slash;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.filesystem.skills.AgentSkillsExtension;

import io.appform.sai.AgentConfig;
import io.appform.sai.Printer;
import io.appform.sai.SaiAgent;
import io.appform.sai.Settings;
import io.appform.sai.agent.AgentFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Mutable session context shared by all slash-commands within an interactive session. Holds
 * references to the current agent, model string, agent configuration, and supporting services.
 *
 * <p>Slash-commands that change the model or persona call {@link #rebuildAgent()} to create a new
 * {@link SaiAgent} and notify the REPL loop via the {@code onAgentRebuilt} callback.
 *
 * <p>The {@link #agentSkillsExtension} field is optional; it is {@code null} in test contexts that
 * do not exercise skill-related commands.
 */
@Slf4j
@Data
@Builder
public class SlashCommandContext {

    /** Current {@code provider/model[/mode]} string (e.g., {@code copilot/claude-haiku-4.5}). */
    @NonNull
    private final AtomicReference<String> currentModel;

    /**
     * Current mode name (may be {@code null}). Updated by {@code /mode} and embedded in the
     * model string when present.
     */
    @Nullable
    private final AtomicReference<String> currentMode;

    /** Active agent configuration. Updated when a new persona is loaded. */
    @NonNull
    private final AtomicReference<AgentConfig> currentAgentConfig;

    /** Live agent instance. Replaced by {@link #rebuildAgent()} on model/persona change. */
    @NonNull
    private final AtomicReference<SaiAgent> currentAgent;

    /** Factory used to create new agent instances. */
    @NonNull
    private final AgentFactory agentFactory;

    /** Printer for terminal output. */
    @NonNull
    private final Printer printer;

    /** Immutable runtime settings. */
    @NonNull
    private final Settings settings;

    /** Jackson mapper for YAML/JSON deserialization (used by {@code /persona}). */
    @NonNull
    private final ObjectMapper mapper;

    /**
     * Skills extension for the current session; may be {@code null} in test contexts or when no skills are configured.
     */
    @Nullable
    private final AgentSkillsExtension<String, String, SaiAgent> agentSkillsExtension;

    /**
     * Callback invoked after {@link #rebuildAgent()} creates a new agent. The REPL loop uses this
     * to register toolboxes on the new instance (e.g., {@code CoreToolBox}).
     */
    private Consumer<SaiAgent> onAgentRebuilt;

    /**
     * Flag set to {@code true} by {@link #rebuildAgent()} so the REPL loop knows to replace its
     * {@code CommandProcessor} with one pointing at the new agent.
     */
    @Builder.Default
    private final AtomicBoolean agentChanged = new AtomicBoolean(false);

    /** @return {@code true} if the agent has been rebuilt since the last call to {@link #resetAgentChanged()} */
    public boolean isAgentChanged() {
        return agentChanged.get();
    }

    /**
     * Create a new {@link SaiAgent} from the current model and config, replace the reference in
     * {@link #currentAgent}, set {@link #agentChanged} to {@code true}, and invoke
     * {@link #onAgentRebuilt} if one is registered.
     */
    public void rebuildAgent() {
        final var modelStr = currentModel.get();
        final var parts = modelStr.split("/", 3);
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            log.warn("Cannot rebuild agent: invalid model string '{}'", modelStr);
            printer.print(Printer.systemMessage(
                                                Printer.Colours.RED + "Invalid model format '" + modelStr
                                                        + "'. Expected 'provider/model[/mode]'"
                                                        + Printer.Colours.RESET));
            return;
        }
        final var provider = parts[0];
        final var modelName = parts[1];
        final var mode = parts.length == 3 ? parts[2] : currentMode.get();
        currentMode.set(mode);
        final var newAgent = agentFactory.createAgent(provider, modelName, mode, currentAgentConfig.get());
        currentAgent.set(newAgent);
        agentChanged.set(true);
        if (onAgentRebuilt != null) {
            onAgentRebuilt.accept(newAgent);
        }
    }

    /** Reset the {@link #agentChanged} flag after the REPL loop has handled the rebuild. */
    public void resetAgentChanged() {
        agentChanged.set(false);
    }
}
