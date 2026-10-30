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
package io.appform.sai.repl;

import static io.appform.sai.render.Utils.elapsedTimeInSeconds;

import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelAttributes;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.utils.AgentUtils;

import io.appform.sai.CancellableExecutorService;
import io.appform.sai.SaiAgent;
import io.appform.sai.models.Actor;
import io.appform.sai.models.Severity;
import io.appform.sai.render.Utils;
import io.appform.sai.term.Printer;
import io.appform.sai.term.Printer.Update;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

/**
 * Runs one user input against the agent on the calling thread and tracks the
 * in-flight execution so it can be cancelled from a signal handler.
 *
 * <p>The future is registered <em>before</em> the calling thread blocks on it,
 * so a Ctrl-C that arrives between submission and blocking always finds a
 * cancellable reference (no lost-interrupt window).
 */
@Slf4j
public class CommandProcessor implements AutoCloseable {

    private final String sessionId;

    private final AtomicReference<SaiAgent> agent;
    private final Printer printer;
    private final CancellableExecutorService agentExecutor;
    private final OkHttpClient httpClient;

    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicReference<Future<?>> runningTask = new AtomicReference<>();
    private final String user = Optional.ofNullable(System.getProperty("user.name"))
            .orElseGet(() -> System.getenv().getOrDefault("USER", "User"));

    @Builder
    public CommandProcessor(
                            @NonNull final String sessionId,
                            @NonNull final SaiAgent agent,
                            @NonNull final Printer printer,
                            final CancellableExecutorService agentExecutor,
                            final OkHttpClient httpClient) {
        this.sessionId = sessionId;
        this.agentExecutor = agentExecutor;
        this.httpClient = httpClient;
        this.agent = new AtomicReference<>(agent);
        this.printer = printer;
    }

    public record InputCommand(
            String runId,
            String input,
            List<com.phonepe.sentinelai.core.agent.MediaInput> media
    ) {
    }

    /**
     * Cancels the in-flight agent execution, if any.
     *
     * @return {@code true} when a running execution was cancelled
     */
    public boolean cancelRunningTask() {
        final var task = runningTask.get();
        if (task == null || task.isDone()) {
            return false;
        }
        log.info("Cancelling running task");
        cancelled.set(true);
        // Order matters. First close the gate and interrupt every worker thread, so retries are rejected.
        // Then abort in-flight HTTP calls, because a socket read does not react to an interrupt.
        // Last, complete the caller's future so that handleInput stops waiting.
        if (agentExecutor != null) {
            agentExecutor.cancelAll();
        }
        if (httpClient != null) {
            httpClient.dispatcher().cancelAll();
        }
        try {
            task.cancel(true);
        }
        catch (CancellationException e) {
            log.info("Running task cancelled successfully");
        }
        return true;
    }

    @Override
    public void close() {
        cancelRunningTask();
        log.info("Command processor shut down");
    }

    /**
     * Executes one user input and blocks until the agent finishes or the
     * execution is cancelled.
     *
     * @param input the input to run
     */
    public void handleInput(final InputCommand input) {
        final var prompt = input.input();
        final var media = input.media();
        final var messages = new ArrayList<Update>();
        final var elapsedTimeCounter = Stopwatch.createStarted();
        var errorMessage = "";
        var errorActor = Actor.ASSISTANT;
        cancelled.set(false);
        if (agentExecutor != null) {
            agentExecutor.beginRun();
        }
        final var streamHandler = new AgentStreamConsumer(new BufferedOutputPrinter(printer), //Reasoning stream
                                                          new BufferedOutputPrinter(printer));//Content stream
        try {
            printer.print(Printer.raw(
                                      Printer.Colours.CYAN + "\u23F3 " + Printer.Colours.GRAY + "Processing "
                                              + Printer.Colours.WHITE + input.runId()
                                              + Printer.Colours.GRAY + "\u2026" + Printer.Colours.RESET));
            final var responseF = agent.get().executeAsyncTextStreaming(
                                                                        AgentInput.<String>builder()
                                                                                .requestMetadata(AgentRequestMetadata
                                                                                        .builder()
                                                                                        .sessionId(sessionId)
                                                                                        .runId(input.runId())
                                                                                        .userId(user)
                                                                                        .build())
                                                                                .request(prompt)
                                                                                .media(media)
                                                                                .build(),
                                                                        streamHandler);
            // Register the future before blocking so a Ctrl-C that arrives
            // between submission and get() still finds something to cancel.
            runningTask.set(responseF);
            final var response = responseF.get();
            if (cancelled.get()) {
                throw new CancellationException("Cancelled by user");
            }
            streamHandler.markDone();
            final var error = response.getError();
            if (error.getErrorType().equals(ErrorType.SUCCESS)) {
                log.info("Agent response: {}", response.getData());
                var infoMessage = Printer.Colours.WHITE + "%s %s.".formatted(
                                                                             Severity.SUCCESS
                                                                                     .getEmoji(),
                                                                             response.getError()
                                                                                     .getMessage());
                final var runUsage = response.getUsage();
                infoMessage += Printer.Colours.GRAY + " (Time taken: %.3f seconds)"
                        .formatted(elapsedTimeInSeconds(elapsedTimeCounter));
                messages.add(Printer.assistantMessage(infoMessage));
                final var tokenSummary = Utils.tokenSummary(runUsage);
                final var contextSummary = contextUsageSummary(agent.get(), runUsage);
                messages.add(Printer.assistantMessage(Printer.Colours.GRAY
                        + (contextSummary == null
                                ? tokenSummary
                                : tokenSummary + Printer.Colours.GRAY + "  " + contextSummary)));
            }
            else {
                errorMessage = "Sentinel error: [%s] %s".formatted(error
                        .getErrorType(), error.getMessage());
            }
        }
        catch (CancellationException e) {
            errorMessage = "Agent execution cancelled by user";
            log.info(errorMessage);
            errorActor = Actor.SYSTEM;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errorMessage = "Agent execution interrupted by user";
            log.info(errorMessage);
            errorActor = Actor.SYSTEM;
        }
        catch (Exception e) {
            errorMessage = AgentUtils.rootCause(e).getMessage();
            log.error("Error executing agent %s".formatted(errorMessage), e);
            errorActor = Actor.SYSTEM;
        }
        finally {
            // Always flush streamed residuals so a cancelled run does not
            // swallow the text that already arrived.
            awaitWorkersStopped();
            streamHandler.markDone();
            runningTask.set(null);
        }
        if (!Strings.isNullOrEmpty(errorMessage)) {
            errorMessage = Printer.Colours.RED + "%s Error sending request: %s"
                    .formatted(Severity.ERROR.getEmoji(), errorMessage);
            errorMessage += Printer.Colours.GRAY + " (Time taken: %.3f seconds)"
                    .formatted(elapsedTimeInSeconds(elapsedTimeCounter));
            messages.add(Update.builder()
                    .actor(errorActor)
                    .severity(Severity.ERROR)
                    .data(errorMessage)
                    .build());
        }
        messages.add(Printer.markIdleStatus());
        messages.add(Printer.empty());
        printer.print(messages);
    }

    /**
     * Points this processor at a rebuilt agent so one processor instance serves
     * the whole session. The interrupt monitor holds this processor, so a swap
     * here keeps Ctrl-C cancellation working after a {@code /model} or
     * {@code /persona} rebuild.
     *
     * @param agent the new active agent
     */
    public void setAgent(@NonNull final SaiAgent agent) {
        this.agent.set(agent);
    }

    private void awaitWorkersStopped() {
        if (agentExecutor == null || !cancelled.get()) {
            return;
        }
        try {
            if (!agentExecutor.awaitIdle(Duration.ofSeconds(5))) {
                log.warn("Agent work did not stop within 5 seconds of cancel");
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Estimates how full the model context window is after this run and renders
     * it as a summary segment (usage percent + auto-compaction threshold).
     *
     * <p>The estimate uses the provider-reported token usage of this run: input
     * tokens grow monotonically across a run's model calls, so the average
     * input tokens per request (plus the final output) approximates the context
     * size the model saw at the end of the run. This includes the system prompt
     * and skills content, which a session-history-based estimate cannot see
     * (system prompts are not persisted), and uses real provider counts instead
     * of local encoding estimates.
     *
     * @param agent    the active agent (post-rebuild safe)
     * @param runUsage usage stats of the just-finished run
     * @return the rendered segment, or {@code null} when it cannot be computed
     */
    private String contextUsageSummary(final SaiAgent agent, final ModelUsageStats runUsage) {
        if (agent == null || runUsage == null) {
            return null;
        }
        try {
            final AgentSetup setup = agent.getSetup();
            final var compactionSetup = setup.getAutoCompactionSetup();
            if (compactionSetup == null) {
                return null;
            }
            final var contextWindow = AgentUtils.getIfNotNull(setup.getModelSettings(),
                                                              ModelSettings::getModelAttributes,
                                                              ModelAttributes.DEFAULT_MODEL_ATTRIBUTES)
                    .getContextWindowSize();
            if (contextWindow <= 0) {
                return null;
            }
            final var requests = Math.max(1, runUsage.getRequestsForRun());
            // Context after this run ≈ last request's input tokens + its output.
            // Per-run stats are cumulative sums; input size grows monotonically
            // across a run, so average input/request + average output/request is a
            // good approximation of the final context size.
            final var approxContextTokens = (runUsage.getRequestTokens() + runUsage.getResponseTokens()) / requests;
            if (approxContextTokens <= 0) {
                return null;
            }
            final int percent = (int) Math.min(100, (approxContextTokens * 100L) / contextWindow);
            return Utils.contextUsageSummary(percent,
                                             compactionSetup.getCompactionTriggerThresholdPercentage());
        }
        catch (Exception e) {
            log.debug("Context usage estimation failed, skipping summary: {}", e.getMessage());
            return null;
        }
    }

}
