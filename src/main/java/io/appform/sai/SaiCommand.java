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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.filesystem.session.FileSystemSessionStore;
import com.phonepe.sentinelai.filesystem.skills.AgentSkillsExtension;
import com.phonepe.sentinelai.session.AgentSessionExtension;
import com.phonepe.sentinelai.session.QueryDirection;
import com.phonepe.sentinelai.session.SessionExtraDataOperator;
import com.phonepe.sentinelai.session.SessionSummary;

import io.appform.sai.CommandProcessor.CommandType;
import io.appform.sai.CommandProcessor.InputCommand;
import io.appform.sai.Printer.Colours;
import io.appform.sai.Printer.Update;
import io.appform.sai.agent.AgentFactory;
import io.appform.sai.cli.CliCommandRegistry;
import io.appform.sai.cli.handlers.ShellCommandHandler;
import io.appform.sai.cli.handlers.SlashCommandHandler;
import io.appform.sai.cli.slash.SlashCommandContext;
import io.appform.sai.cli.slash.SlashCommandDispatcher;
import io.appform.sai.commands.CopilotCommand;
import io.appform.sai.commands.DeleteSessionsCommand;
import io.appform.sai.commands.ExportSessionCommand;
import io.appform.sai.commands.ListSessionsCommand;
import io.appform.sai.commands.PruneSessionsCommand;
import io.appform.sai.config.AgentConfigLoader;
import io.appform.sai.models.Actor;
import io.appform.sai.models.Severity;
import io.appform.sai.tools.CoreToolBox;
import io.appform.sai.transform.RequestTransformValidator;

import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Slf4j
@Getter
@Command(name = "sai", mixinStandardHelpOptions = true, version = "1.0", description = "Sai AI Agent", subcommands = {
        ListSessionsCommand.class,
        DeleteSessionsCommand.class,
        PruneSessionsCommand.class,
        ExportSessionCommand.class,
        io.appform.sai.commands.SummaryCommand.class,
        CopilotCommand.class
})
public class SaiCommand implements Callable<Integer> {
    @Option(names = {
            "-s", "--session-id"
    }, description = "Resume a specific session")
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
    }, description = "Model to use, in the format 'provider/model' (e.g. 'copilot/claude-haiku-4.5'). Overrides model specified in persona file.", arity = "0..1")
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
        return builder.build();
    }

    @Override
    @SuppressWarnings("java:S106")
    public Integer call() throws Exception {
        final var sessionIdProvided = !Strings.isNullOrEmpty(sessionId);
        final var effectiveSessionId = Objects.requireNonNullElseGet(sessionId,
                                                                     () -> UUID.randomUUID().toString());

        final var mapper = JsonUtils.createMapper();
        final var executorService = Executors.newCachedThreadPool();
        final var eventBus = new EventBus(executorService);

        final var okHttpClient = buildOkHttpClient();
        final var pipedInput = readPipedInput();
        // Resolve the effective input: explicit --input flag takes priority, then piped stdin.
        final var effectiveInput = !Strings.isNullOrEmpty(input) ? resolveInput(input)
                : !Strings.isNullOrEmpty(pipedInput) ? pipedInput
                : null;

        final var settings = buildSettings(effectiveSessionId, effectiveInput);

        final var sessionDataPath = Paths.get(settings.getDataDir(), "sessions");
        Files.createDirectories(sessionDataPath);

        // On resume: restore model and persona from the saved session extra data.
        // CLI flags (--model / --persona) always take priority over saved values.
        if (sessionIdProvided) {
            final var probeStore = FileSystemSessionStore.builder()
                    .baseDir(sessionDataPath.toString())
                    .mapper(mapper)
                    .cacheSize(1)
                    .build(); // no extraDataOperator — read-only probe
            probeStore.session(effectiveSessionId).ifPresent(saved -> {
                final var savedExtra = saved.getExtra();
                if (savedExtra == null) {
                    return; // older session with no extra data — backwards compat
                }
                // Restore model only when --model was not supplied on the CLI
                if (Strings.isNullOrEmpty(model)) {
                    final var savedModel = (String) savedExtra.get("model");
                    if (!Strings.isNullOrEmpty(savedModel)) {
                        model = savedModel;
                    }
                }
                // Restore persona only when --persona was not supplied on the CLI,
                // and only if the persona file is still resolvable/readable.
                if (Strings.isNullOrEmpty(persona)) {
                    final var savedPersona = (String) savedExtra.get("persona");
                    if (!Strings.isNullOrEmpty(savedPersona)) {
                        try {
                            AgentConfigLoader.resolvePersonaPath(savedPersona, settings.getConfigDir());
                            persona = savedPersona; // file still exists → restore
                        }
                        catch (Exception e) {
                            log.warn("Saved persona '{}' is no longer accessible, using default: {}",
                                     savedPersona,
                                     e.getMessage());
                            // persona stays null → resolveAgentConfig falls back to built-in default
                        }
                    }
                }
            });
        }

        AgentConfig agentConfig;
        try {
            agentConfig = resolveAgentConfig(persona, settings.getConfigDir(), mapper);
        }
        catch (Exception e) {
            log.error("Error loading persona: {}", persona, e);
            System.err.println("Error: Failed to load persona file: " + persona + " (" + e.getMessage() + ")");
            return 1;
        }
        try {
            if (agentConfig.getRequestTransforms() != null) {
                RequestTransformValidator.validate(agentConfig.getRequestTransforms());
            }
        }
        catch (IllegalArgumentException e) {
            log.error("Invalid requestTransforms in persona: {}", persona, e);
            System.err.println("Error: Invalid requestTransforms configuration: " + e.getMessage());
            return 1;
        }
        final var modelPointer = Strings.isNullOrEmpty(model)
                ? agentConfig.getModel()
                : model;
        final var parts = modelPointer.split("/");
        Preconditions.checkArgument(parts.length == 2,
                                    "Model name must be in the format 'provider/model'. Provided: " + modelPointer);
        final var provider = parts[0].toLowerCase();
        final var modelName = parts[1];
        log.info("Using model provider: {}, model name: {}", provider, modelName);
        final var modelProviderFactory = new ConfigurableProviderFactory(provider, mapper, okHttpClient);

        final var sessionStore = FileSystemSessionStore.builder()
                .baseDir(sessionDataPath.toString())
                .mapper(mapper)
                .cacheSize(1)
                .extraDataOperator(SessionExtraDataOperator.fixed(Map.of(
                                                                         "workDir",
                                                                         settings.getWorkDir(),
                                                                         "model",
                                                                         modelPointer,
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
        final var agentSkillsExtension = buildAgentSkillsExtension(settings, agentConfig);
        final var agentFactory = new AgentFactory(settings,
                                                  List.of(sessionExtension, agentSkillsExtension),
                                                  executorService,
                                                  modelProviderFactory,
                                                  mapper,
                                                  eventBus,
                                                  okHttpClient);

        final var agent = agentFactory.createAgent(modelName, agentConfig);
        final var agentRef = new AtomicReference<>(agent);

        try (final var printer = Printer.builder()
                .settings(settings)
                .executorService(executorService)
                .build()
                .start()) {
            // Setup rest of the connections
            agent.registerToolbox(new CoreToolBox(printer));
            printer.updateContextInfo(agentConfig.getName(), modelPointer);
            sessionExtension.onSessionSummarized()
                    .connect(sessionSummary -> printer.print(Printer.systemMessage(Colours.YELLOW
                            + "Session compacted with summary: " + Colours.WHITE
                            + sessionSummary.getTitle() + Colours.RESET)));
            final var eventPrinter = new EventPrinter(printer, mapper);
            eventBus.onEvent().connect(event -> {
                final var eventSessionId = event.getSessionId();
                // There might be events for other LLM events like for example compaction,
                // memory extraction etc, so we filter based on session id to avoid printing irrelevant events
                if (!Strings.isNullOrEmpty(eventSessionId) && effectiveSessionId.equals(eventSessionId)) {
                    event.accept(eventPrinter);
                }
            });

            final var slashContext = SlashCommandContext.builder()
                    .currentModel(new AtomicReference<>(modelPointer))
                    .currentAgentConfig(new AtomicReference<>(agentConfig))
                    .currentAgent(agentRef)
                    .agentFactory(agentFactory)
                    .printer(printer)
                    .settings(settings)
                    .mapper(mapper)
                    .agentSkillsExtension(agentSkillsExtension)
                    .build();
            slashContext.setOnAgentRebuilt(newAgent -> {
                newAgent.registerToolbox(new CoreToolBox(printer));
                printer.updateContextInfo(slashContext.getCurrentAgentConfig().get().getName(),
                                          slashContext.getCurrentModel().get());
                printer.print(Printer.markIdleStatus());
            });

            var commandProcessor = buildCommandProcessor(agentRef.get(), settings, printer);
            final var interruptMonitor = new InterruptMonitor(commandProcessor, printer);
            try {
                if (!settings.isHeadless()) {
                    printer.print(Update.builder()
                            .actor(Actor.SYSTEM)
                            .severity(Severity.INFO)
                            .colour(Printer.Colours.BOLD_YELLOW)
                            .data("Welcome to SAI! Session ID: [%s] Type 'exit' to quit...."
                                    .formatted(effectiveSessionId))
                            .build());
                }
                if (sessionIdProvided) {
                    if (!settings.isHeadless()) {
                        printer.print(Update.builder()
                                .actor(Actor.SYSTEM)
                                .severity(Severity.INFO)
                                .colour(Printer.Colours.BOLD_YELLOW)
                                .data("Resumed with \u2014 model: %s, persona: %s"
                                        .formatted(modelPointer,
                                                   Strings.isNullOrEmpty(persona) ? "(default)" : persona))
                                .build());
                    }
                    final var response = sessionStore.readMessages(effectiveSessionId,
                                                                   Integer.MAX_VALUE,
                                                                   true,
                                                                   null,
                                                                   QueryDirection.OLDER);
                    final var messagePrinter = new MessagePrinter(printer, mapper, true);
                    response.getItems().forEach(message -> {
                        final var updates = message.accept(messagePrinter);
                        printer.print(updates);
                    });
                }

                var userInput = effectiveInput;
                final var dispatcher = new SlashCommandDispatcher(slashContext);
                final var cliCommandRegistry = new CliCommandRegistry(
                                                                      List.of(new ShellCommandHandler(),
                                                                              new SlashCommandHandler(dispatcher)));
                printer.addCompleter(new SlashCommandCompleter(dispatcher.getCommandLine()));
                while (Strings.isNullOrEmpty(userInput) || !userInput.equalsIgnoreCase("exit")) {
                    if (Strings.isNullOrEmpty(userInput)) {
                        userInput = readInput(printer).orElse("exit");
                    }
                    else {
                        // Check for client-side CLI commands (e.g. ! for shell, / for slash) before forwarding to agent
                        if (cliCommandRegistry.tryHandle(userInput, printer)) {
                            if (slashContext.isAgentChanged()) {
                                commandProcessor.close();
                                commandProcessor = buildCommandProcessor(agentRef.get(), settings, printer);
                                slashContext.resetAgentChanged();
                            }
                            userInput = !Strings.isNullOrEmpty(effectiveInput) ? "exit" : null;
                            continue;
                        }
                        final var resolvedInput = resolveInput(userInput);
                        final var command = CommandProcessor.Command.builder()
                                .command(CommandType.INPUT)
                                .input(new InputCommand("run-" + UUID.randomUUID()
                                        .toString(), resolvedInput))
                                .build();
                        try {
                            commandProcessor.handle(command);
                        }
                        finally {
                            userInput = !Strings.isNullOrEmpty(effectiveInput) ? "exit" : null;
                        }
                    }
                }
                if (!settings.isHeadless() && !Strings.isNullOrEmpty(userInput) && userInput.equalsIgnoreCase("exit")) {
                    printer.print(Printer.systemMessage("Resume: -s %s".formatted(effectiveSessionId)));
                }
            }
            finally {
                interruptMonitor.close();
                commandProcessor.close();
            }
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
        }
        return 0;
    }

    @SneakyThrows
    private AgentSkillsExtension<String, String, SaiAgent> buildAgentSkillsExtension(final Settings settings,
                                                                                     AgentConfig agentConfig) {
        if (!Strings.isNullOrEmpty(skill)) {
            //Single skill specified. pass ojnly this ane remove other stuff
            return AgentSkillsExtension.<String, String, SaiAgent>withSingleSkill()
                    .baseDir(Paths.get(settings.getConfigDir(), "skills").toString())
                    .singleSkill(skill)
                    .build();
        }
        else {
            var skillDirs = agentConfig.getSkillDirectories();
            if (skillDirs == null || skillDirs.isEmpty()) {
                final var path = Paths.get(settings.getConfigDir(), "skills");
                Files.createDirectories(path);
                skillDirs = List.of(path.toString());
            }
            var skillNames = Objects.requireNonNullElseGet(agentConfig.getSkillNames(), List::<String>of);
            return AgentSkillsExtension.<String, String, SaiAgent>withMultipleSkills()
                    .baseDir(Paths.get(settings.getConfigDir(), "skills").toString())
                    .skillsDirectories(skillDirs)
                    .skillsToLoad(skillNames)
                    .build();
        }


    }

    private CommandProcessor buildCommandProcessor(SaiAgent saiAgent, Settings currentSettings, Printer printer) {
        return CommandProcessor.builder()
                .sessionId(currentSettings.getSessionId())
                .agent(saiAgent)
                .printer(printer)
                .build()
                .start();
    }

    /**
     * Builds the shared {@link OkHttpClient} with project-standard timeouts.
     *
     * @return a configured {@code OkHttpClient}
     */
    private OkHttpClient buildOkHttpClient() {
        return new OkHttpClient.Builder()
                .readTimeout(Duration.ofSeconds(300))
                .callTimeout(Duration.ofSeconds(300))
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Constructs the {@link Settings} object for this invocation, applying CLI overrides and
     * routing to a temporary data directory when a one-shot {@code effectiveInput} is provided.
     *
     * @param effectiveSessionId the resolved session ID to embed in settings
     * @param effectiveInput     the resolved input string (may be {@code null} for interactive mode)
     * @return a fully-built {@code Settings} instance
     * @throws java.io.IOException if a temporary data directory cannot be created
     */
    @SneakyThrows
    private Settings buildSettings(String effectiveSessionId, String effectiveInput) {
        final var settingsBuilder = Settings.builder()
                .sessionId(effectiveSessionId)
                .debug(debug)
                .headless(headless || !Strings.isNullOrEmpty(effectiveInput))
                .noSession(!Strings.isNullOrEmpty(effectiveInput));
        if (!Strings.isNullOrEmpty(configDir)) {
            settingsBuilder.configDir(configDir);
        }
        if (Strings.isNullOrEmpty(effectiveInput)) {
            if (!Strings.isNullOrEmpty(dataDir)) {
                settingsBuilder.dataDir(dataDir);
            }
        }
        else {
            // If input is provided (via --input or piped stdin), we don't care about session persistence,
            // so we can skip setting up data dir. However we do care about compaction etc so we provide
            // the session extension a temporary directory
            final var tempDataDir = Files.createTempDirectory("sai-data-")
                    .toAbsolutePath()
                    .normalize()
                    .toString();
            settingsBuilder.dataDir(tempDataDir);
        }
        return settingsBuilder.build();
    }

    private Optional<String> readInput(final Printer printer) {
        var prompt = Printer.Colours.YELLOW + "> " + Printer.Colours.RESET;
        try {
            return Optional.of(printer.getLineReader().readLine(prompt));
        }
        catch (EndOfFileException | UserInterruptException e) {
            return Optional.empty();
        }
    }

    /**
     * Reads all of {@code System.in} when stdin is piped (non-interactive, no explicit
     * {@code --input} flag) and returns the content as a single string. Returns {@code null} when
     * running interactively, in headless mode, or when {@code --input} was already specified.
     *
     * @return the piped stdin content, or {@code null} if not applicable
     * @throws IllegalStateException if stdin appears to be piped but is empty, or on read failure
     */
    private String readPipedInput() {
        if (!headless && System.console() == null && Strings.isNullOrEmpty(input)) {
            try {
                if (System.in.available() == 0) {
                    throw new IllegalStateException("No TTY detected and no input provided. " +
                            "Please run interactively with a TTY, pipe input via stdin, or use the --input flag.");
                }
                return new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))
                        .lines()
                        .collect(Collectors.joining("\n"))
                        .strip();
            }
            catch (IOException e) {
                throw new IllegalStateException("Failed to read from standard input", e);
            }
        }
        return null;
    }

    private AgentConfig resolveAgentConfig(String persona, String configDir, ObjectMapper mapper) {
        if (Strings.isNullOrEmpty(persona)) {
            return AgentConfig.builder()
                    .agentId("sai-agent")
                    .name("Sai Agent")
                    .description("An AI agent that can execute tasks and answer questions.")
                    .model("copilot/claude-haiku-4.5")
                    .build();
        }
        final var resolvedPath = AgentConfigLoader.resolvePersonaPath(persona, configDir);
        return AgentConfigLoader.load(resolvedPath, mapper);
    }

    @SneakyThrows
    private String resolveInput(String input) {
        if (input.startsWith("@")) {
            final var filePath = input.substring(1);
            if (Strings.isNullOrEmpty(filePath)) {
                throw new IllegalArgumentException("--input '@' requires a file path");
            }
            return Files.readString(Paths.get(filePath), StandardCharsets.UTF_8);
        }
        return input.replaceAll("@(\\S+)", "$1");
    }
}
