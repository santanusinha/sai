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
package io.appform.sai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.filesystem.session.FileSystemSessionStore;
import com.phonepe.sentinelai.filesystem.skills.AgentSkillsExtension;
import com.phonepe.sentinelai.session.AgentSessionExtension;
import com.phonepe.sentinelai.session.QueryDirection;

import io.appform.sai.CommandProcessor.InputCommand;
import io.appform.sai.Printer.Update;
import io.appform.sai.agent.AgentFactory;
import io.appform.sai.cli.CliCommandRegistry;
import io.appform.sai.cli.handlers.ShellCommandHandler;
import io.appform.sai.cli.handlers.SlashCommandHandler;
import io.appform.sai.cli.slash.SlashCommandContext;
import io.appform.sai.cli.slash.SlashCommandDispatcher;
import io.appform.sai.models.Actor;
import io.appform.sai.models.Severity;
import io.appform.sai.tools.CoreToolBox;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;

/**
 * Runs the interactive read-eval loop: wires printers, events, and slash commands,
 * then feeds each user input to the {@link CommandProcessor}.
 *
 * <p>One instance serves one session. {@link #run()} blocks until the user exits,
 * supplies a one-shot input, or presses Ctrl-C/Ctrl-D at the prompt.
 */
@Slf4j
public class ReplRunner {

    private final Settings settings;
    private final ObjectMapper mapper;
    private final EventBus eventBus;
    private final ExecutorService executorService;
    private final FileSystemSessionStore sessionStore;
    private final AgentSessionExtension<String, String, SaiAgent> sessionExtension;
    private final AgentSkillsExtension<String, String, SaiAgent> agentSkillsExtension;
    private final AgentFactory agentFactory;
    private final AgentRuntimeBuilder.ResolvedModelDetails modelDetails;
    private final AgentConfig agentConfig;
    private final String effectiveSessionId;
    private final String effectiveInput;
    private final String modelPointer;
    private final String persona;
    private final boolean sessionIdProvided;

    /**
     * Builds a runner for one session.
     *
     * @param runtime assembled runtime components and flag values
     */
    public ReplRunner(final Runtime runtime) {
        this.settings = runtime.settings();
        this.mapper = runtime.mapper();
        this.eventBus = runtime.eventBus();
        this.executorService = runtime.executorService();
        this.sessionStore = runtime.sessionStore();
        this.sessionExtension = runtime.sessionExtension();
        this.agentSkillsExtension = runtime.agentSkillsExtension();
        this.agentFactory = runtime.agentFactory();
        this.modelDetails = runtime.modelDetails();
        this.agentConfig = runtime.agentConfig();
        this.effectiveSessionId = runtime.effectiveSessionId();
        this.effectiveInput = runtime.effectiveInput();
        this.modelPointer = runtime.modelPointer();
        this.persona = runtime.persona();
        this.sessionIdProvided = runtime.sessionIdProvided();
    }

    /**
     * Assembled runtime components. Built by {@code SaiCommand.call()} and handed to
     * {@link ReplRunner}.
     *
     * @param settings             resolved settings for this invocation
     * @param mapper               shared JSON mapper
     * @param eventBus             event bus the agent publishes to
     * @param executorService      shared executor (agent runs, printer queue, event bus)
     * @param sessionStore         session store for history replay
     * @param sessionExtension     session extension registered on the agent
     * @param agentSkillsExtension skills extension registered on the agent
     * @param agentFactory         builds and rebuilds agents
     * @param modelDetails         resolved provider/model/mode details
     * @param agentConfig          resolved persona config
     * @param effectiveSessionId   session ID in use
     * @param effectiveInput       one-shot input (may be {@code null} for interactive mode)
     * @param modelPointer         model string in {@code provider/model[/mode]} form
     * @param persona              persona path (may be {@code null})
     * @param sessionIdProvided    {@code true} when resuming an existing session
     */
    public record Runtime(
            Settings settings,
            ObjectMapper mapper,
            EventBus eventBus,
            ExecutorService executorService,
            FileSystemSessionStore sessionStore,
            AgentSessionExtension<String, String, SaiAgent> sessionExtension,
            AgentSkillsExtension<String, String, SaiAgent> agentSkillsExtension,
            AgentFactory agentFactory,
            AgentRuntimeBuilder.ResolvedModelDetails modelDetails,
            AgentConfig agentConfig,
            String effectiveSessionId,
            String effectiveInput,
            String modelPointer,
            String persona,
            boolean sessionIdProvided
    ) {
    }

    /**
     * Wires the printer, event bus, and slash commands, then runs the input loop.
     *
     * @throws Exception on terminal or agent failures
     */
    @SuppressWarnings("java:S106")
    public void run() throws Exception {
        final var agent = agentFactory.createAgent(modelDetails.provider(),
                                                   modelDetails.modelName(),
                                                   modelDetails.mode(),
                                                   agentConfig);
        final var agentRef = new AtomicReference<>(agent);

        try (final var printer = Printer.builder()
                .settings(settings)
                .executorService(executorService)
                .build()
                .start()) {
            // Setup rest of the connections
            agent.registerToolbox(new CoreToolBox(printer, agentConfig.getTools()));
            printer.updateContextInfo(agentConfig.getName(), modelPointer);
            final var eventPrinter = new EventPrinter(printer, mapper);
            eventBus.onEvent().connect(event -> {
                final var eventSessionId = event.getSessionId();
                // There might be events for other LLM events like for example compaction,
                // memory extraction etc, so we filter based on session id to avoid printing irrelevant events
                if (!Strings.isNullOrEmpty(eventSessionId) && effectiveSessionId.equals(eventSessionId)) {
                    event.accept(eventPrinter);
                }
            });

            final var slashContext = buildSlashContext(agentRef, printer);
            slashContext.setOnAgentRebuilt(newAgent -> {
                newAgent.registerToolbox(new CoreToolBox(printer,
                                                         slashContext.getCurrentAgentConfig().get().getTools()));
                printer.updateContextInfo(slashContext.getCurrentAgentConfig().get().getName(),
                                          slashContext.getCurrentModel().get());
            });

            final var commandProcessor = AgentRuntimeBuilder.buildCommandProcessor(agentRef.get(), settings, printer);
            final var interruptMonitor = new InterruptMonitor(commandProcessor, printer);
            try {
                printWelcomeAndBanner(printer, agent);
                if (sessionIdProvided) {
                    replayHistory(printer);
                }
                runLoop(printer, slashContext, agentRef, commandProcessor, interruptMonitor);
            }
            finally {
                interruptMonitor.close();
                commandProcessor.close();
            }
        }
    }

    private SlashCommandContext buildSlashContext(final AtomicReference<SaiAgent> agentRef,
                                                  final Printer printer) {
        final var slashContext = SlashCommandContext.builder()
                .currentModel(new AtomicReference<>(modelPointer))
                .currentMode(new AtomicReference<>(modelDetails.mode()))
                .currentAgentConfig(new AtomicReference<>(agentConfig))
                .currentAgent(agentRef)
                .agentFactory(agentFactory)
                .printer(printer)
                .settings(settings)
                .mapper(mapper)
                .agentSkillsExtension(agentSkillsExtension)
                .sessionExtension(sessionExtension)
                .build();
        return slashContext;
    }

    private void printWelcomeAndBanner(final Printer printer, final SaiAgent agent) {
        if (!settings.isHeadless()) {
            printer.print(Update.builder()
                    .actor(Actor.SYSTEM)
                    .severity(Severity.INFO)
                    .colour(Printer.Colours.BOLD_YELLOW)
                    .data("Welcome to SAI! Session ID: [%s] Type 'exit' to quit...."
                            .formatted(effectiveSessionId))
                    .build());
            StartupBanner.print(agent, agentSkillsExtension, printer);
        }
    }

    private void replayHistory(final Printer printer) {
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

    @SuppressWarnings("java:S106")
    private void runLoop(final Printer printer,
                         final SlashCommandContext slashContext,
                         final AtomicReference<SaiAgent> agentRef,
                         final CommandProcessor commandProcessor,
                         final InterruptMonitor interruptMonitor) {
        final var dispatcher = new SlashCommandDispatcher(slashContext);
        final var cliCommandRegistry = new CliCommandRegistry(List.of(new ShellCommandHandler(),
                                                                      new SlashCommandHandler(dispatcher)));
        printer.addCompleter(new SlashCommandCompleter(dispatcher.getCommandLine()));
        var userInput = effectiveInput;
        while (Strings.isNullOrEmpty(userInput) || !userInput.equalsIgnoreCase("exit")) {
            if (Strings.isNullOrEmpty(userInput)) {
                userInput = InputResolver.readInput(printer).orElse("exit");
            }
            else {
                // Check for client-side CLI commands (e.g. ! for shell, / for slash) before forwarding to agent
                if (cliCommandRegistry.tryHandle(userInput, printer)) {
                    if (slashContext.isAgentChanged()) {
                        // One processor serves the whole session; point it at the
                        // rebuilt agent so the interrupt monitor stays valid.
                        commandProcessor.setAgent(agentRef.get());
                        slashContext.resetAgentChanged();
                    }
                    userInput = !Strings.isNullOrEmpty(effectiveInput) ? "exit" : null;
                    continue;
                }
                final var parsedInput = MediaParser.parse(userInput);
                final var resolvedInput = InputResolver.resolveInput(parsedInput.getTextPrompt());
                final var inputCommand = new InputCommand("run-" + UUID.randomUUID().toString(),
                                                          resolvedInput,
                                                          parsedInput.getMedia());
                try {
                    interruptMonitor.runStarted();
                    commandProcessor.handleInput(inputCommand);
                }
                finally {
                    interruptMonitor.runFinished();
                    userInput = !Strings.isNullOrEmpty(effectiveInput) ? "exit" : null;
                }
            }
        }
        if (!settings.isHeadless() && !Strings.isNullOrEmpty(userInput) && userInput.equalsIgnoreCase("exit")) {
            printer.print(Printer.systemMessage("Resume: -s %s".formatted(effectiveSessionId)));
        }
    }
}
