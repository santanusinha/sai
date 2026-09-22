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

import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.filesystem.session.FileSystemSessionStore;
import com.phonepe.sentinelai.session.AgentSessionExtension;
import com.phonepe.sentinelai.session.SessionExtraDataOperator;
import com.phonepe.sentinelai.session.SessionSummary;

import io.appform.sai.agent.AgentFactory;
import io.appform.sai.commands.CopilotCommand;
import io.appform.sai.commands.DeleteSessionsCommand;
import io.appform.sai.commands.ExportSessionCommand;
import io.appform.sai.commands.ListProvidersCommand;
import io.appform.sai.commands.ListSessionsCommand;
import io.appform.sai.commands.PruneSessionsCommand;
import io.appform.sai.transform.MdcSessionInterceptor;

import org.slf4j.MDC;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Slf4j
@Getter
@Command(name = "sai", mixinStandardHelpOptions = true, version = "1.0", description = "Sai AI Agent", subcommands = {
        ListSessionsCommand.class,
        DeleteSessionsCommand.class,
        PruneSessionsCommand.class,
        ExportSessionCommand.class,
        io.appform.sai.commands.SessionSummaryCommand.class,
        CopilotCommand.class,
        ListProvidersCommand.class
})
public class SaiCommand implements Callable<Integer> {

    @Option(names = {
            "-s", "--session"
    }, description = "Resume a specific session. Without a parameter, resumes the last session in the current directory.", arity = "0..1")
    private String sessionId;

    @Option(names = {
            "-d", "--debug"
    }, description = "Enable debug mode")
    private boolean debug;

    @Option(names = {
            "--headless"
    }, description = "Run in headless mode")
    private boolean headless;

    @Option(names = {
            "--data-dir"
    }, description = "Override data directory")
    private String dataDir;

    @Option(names = {
            "--config-dir"
    }, description = "Override config directory")
    private String configDir;

    @Option(names = {
            "-i", "--input"
    }, description = "Execute a single input and exit. If the value starts with '@', read input from the specified file.")
    private String input;

    @Option(names = {
            "-p", "--persona"
    }, description = "Path to AgentConfig persona file (.yaml/.yml/.json)")
    private String persona;

    @Option(names = {
            "--skill"
    }, description = "Path to a single skill directory to load. When specified, only this skill is loaded and skill discovery is disabled.")
    private String skill;

    @Option(names = {
            "-m", "--model"
    }, description = "Model to use, in the format 'provider/model[/mode]' (e.g. 'copilot/claude-haiku-4.5'). Overrides model specified in persona file.", arity = "0..1")
    private String model;

    /**
     * Resolves a {@link Settings} instance from a parent {@link SaiCommand}, applying the
     * {@code --data-dir} override if provided.
     *
     * @param parent the parent picocli command carrying global option values
     * @return a fully-built {@code Settings} object
     */
    public static Settings resolveSettings(SaiCommand parent) {
        final var builder = Settings.builder();
        if (!Strings.isNullOrEmpty(parent.getDataDir())) {
            builder.dataDir(parent.getDataDir());
        }
        if (!Strings.isNullOrEmpty(parent.getConfigDir())) {
            builder.configDir(parent.getConfigDir());
        }
        return builder.build();
    }

    @Override
    @SuppressWarnings("java:S106")
    public Integer call() throws Exception {
        // If -s was passed without a parameter, resolve the last session in the current directory.
        if (isSessionFlagPresent() && Strings.isNullOrEmpty(sessionId)) {
            final var tempSettings = resolveSettings(this);
            final var sessionDataPath = Paths.get(tempSettings.getDataDir(), "sessions");
            final var resolvedSessionId = SessionResolver.resolveLastSessionId(sessionDataPath,
                                                                               tempSettings.getWorkDir());
            if (resolvedSessionId == null) {
                System.err.println("Error: No previous session found in the current directory."
                        + " Use 'sai list-sessions --all' to see available sessions.");
                return 1;
            }
            sessionId = resolvedSessionId;
        }

        final var sessionIdProvided = !Strings.isNullOrEmpty(sessionId);
        final var effectiveSessionId = Objects.requireNonNullElseGet(sessionId,
                                                                     () -> UUID.randomUUID().toString());
        MDC.put(MdcSessionInterceptor.MDC_SESSION_ID_KEY, effectiveSessionId);

        final var mapper = JsonUtils.createMapper();
        final var executorService = new MdcPropagatingExecutorService(Executors.newCachedThreadPool());
        final var eventBus = new EventBus(executorService);

        final var pipedInput = InputResolver.readPipedInput(input, headless);
        // Resolve the effective input: explicit --input flag takes priority, then piped stdin.
        // Note: raw input is passed as-is; media parsing (@image:, @audio:) and text
        // resolution (@file refs) are handled in the input loop via MediaParser + InputResolver.
        final var effectiveInput = !Strings.isNullOrEmpty(input)
                ? input
                : !Strings.isNullOrEmpty(pipedInput)
                        ? pipedInput
                : null;

        final var settings = AgentRuntimeBuilder.buildSettings(dataDir,
                                                               configDir,
                                                               debug,
                                                               headless,
                                                               effectiveSessionId,
                                                               effectiveInput);

        final var sessionDataPath = Paths.get(settings.getDataDir(), "sessions");
        Files.createDirectories(sessionDataPath);

        Files.createDirectories(Paths.get("/tmp", "sai", effectiveSessionId, "scratch"));

        // On resume: restore model and persona from the saved session extra data.
        // CLI flags (--model / --persona) always take priority over saved values.
        if (sessionIdProvided) {
            final var restored = SessionResolver.populateDataFromSession(effectiveSessionId,
                                                                         sessionDataPath,
                                                                         settings,
                                                                         model,
                                                                         persona);
            if (restored.hasModel()) {
                model = restored.getModel();
            }
            if (restored.hasPersona()) {
                persona = restored.getPersona();
            }
        }

        final var settingsConfig = AgentRuntimeBuilder.loadSettings(settings.getConfigDir(), mapper);

        AgentConfig agentConfig;
        try {
            agentConfig = AgentRuntimeBuilder.resolveAgentConfig(persona, settings.getConfigDir(), mapper);
        }
        catch (Exception e) {
            log.error("Error loading persona: {}", persona, e);
            System.err.println("Error: Failed to load persona file: " + persona + " (" + e.getMessage() + ")");
            return 1;
        }

        final var modelPointer = Strings.isNullOrEmpty(model)
                ? agentConfig.getModel()
                : model;
        final var modelDetails = AgentRuntimeBuilder.resolveModelFactory(modelPointer,
                                                                         mapper,
                                                                         settingsConfig);
        log.info("Settings path: {}, data path: {}, persona: {}, model: {}, mode: {}",
                 settings.getConfigDir(),
                 settings.getDataDir(),
                 persona,
                 modelPointer,
                 modelDetails.mode());

        final var sessionStore = FileSystemSessionStore.builder()
                .baseDir(sessionDataPath.toString())
                .mapper(mapper)
                .cacheSize(1)
                .extraDataOperator(SessionExtraDataOperator.fixed(Map.of(
                                                                         "workDir",
                                                                         settings.getWorkDir(),
                                                                         "model",
                                                                         modelPointer,
                                                                         "mode",
                                                                         Objects.requireNonNullElse(modelDetails.mode(),
                                                                                                    ""),
                                                                         "persona",
                                                                         Objects.requireNonNullElse(persona, ""))))
                .build();
        if (settings.isNoSession()) {
            sessionStore.saveSession(SessionSummary.builder()
                    .sessionId(effectiveSessionId)
                    .title("Temporary session for single input execution")
                    .updatedAt(System.currentTimeMillis())
                    .build());
        }
        final var sessionExtension = AgentSessionExtension.<String, String, SaiAgent>builder()
                .sessionStore(sessionStore)
                .mapper(mapper)
                .build();
        final var agentSkillsExtension = AgentRuntimeBuilder.buildAgentSkillsExtension(settings,
                                                                                       agentConfig,
                                                                                       skill);
        final var agentFactory = new AgentFactory(settings,
                                                  List.of(sessionExtension, agentSkillsExtension),
                                                  executorService,
                                                  modelDetails.factory(),
                                                  mapper,
                                                  eventBus,
                                                  modelDetails.httpClient(),
                                                  settingsConfig);

        try {
            new ReplRunner(new ReplRunner.Runtime(settings,
                                                  mapper,
                                                  eventBus,
                                                  executorService,
                                                  sessionStore,
                                                  sessionExtension,
                                                  agentSkillsExtension,
                                                  agentFactory,
                                                  modelDetails,
                                                  agentConfig,
                                                  effectiveSessionId,
                                                  effectiveInput,
                                                  modelPointer,
                                                  persona,
                                                  sessionIdProvided)).run();
        }
        catch (Exception e) {
            log.error("Error processing input", e);
            return 1;
        }
        finally {
            executorService.shutdown();
            try {
                executorService.awaitTermination(1, TimeUnit.SECONDS);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (settings.isNoSession()) {
                sessionStore.deleteSession(effectiveSessionId);
            }
            MDC.remove(MdcSessionInterceptor.MDC_SESSION_ID_KEY);
        }
        return 0;
    }

    /**
     * Returns {@code true} when the {@code -s}/{@code --session} flag was present on the
     * command line, even if no parameter value was supplied.
     *
     * @return {@code true} if the flag was present
     */
    private boolean isSessionFlagPresent() {
        return sessionId != null;
    }
}
