/*
 * Copyright 2026 authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.appform.sai;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.utils.AgentUtils;

import io.appform.sai.models.DisaplyMessage;
import io.appform.sai.models.Severity;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CommandProcessor implements AutoCloseable {

    enum CommandType {
        INPUT
    }

    public record InputCommand(String runId, String input) {
    }

    @Builder
    public record Command(CommandType command, InputCommand input) {
    }

    private final String sessionId;
    private final SaiAgent agent;
    private final ExecutorService executorService;
    private final DisplayMessageHandler displayMessageHandler;
    // private final Status status;
    private final LinkedBlockingQueue<Command> inputQueue = new LinkedBlockingQueue<>();
    private Future<?> runningTask;
    private final String user = Objects.requireNonNullElse(System.getProperty("USER"), "User");

    @Builder
    public CommandProcessor(
                       @NonNull final String sessionId,
                       @NonNull final SaiAgent agent,
                       @NonNull final ExecutorService executorService,
                       @NonNull final DisplayMessageHandler displayMessageHandler/* ,
                       @NonNull final Status status */) {
        this.sessionId = sessionId;
        this.agent = agent;
        this.executorService = executorService;
        this.displayMessageHandler = displayMessageHandler;
        // this.status = status;
    }

    public CommandProcessor start() {
        // status.update(List.of(new AttributedString("Idle")));
        runningTask = executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                waitForInput();
            }
        });
        return this;
    }

    @Override
    public void close() {
        if (runningTask != null) {
            runningTask.cancel(true);
        }
        log.info("Command processor shut down");
    }

    public void handle(Command command) {
        try {
            inputQueue.put(command);
        }
        catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Queue put interrupted");
        }
    }

    private void waitForInput() {
        try {
            final var item = inputQueue.take();
            if (item != null) {
                try {
                    //status.update(List.of(new AttributedString(Printer.Colours.YELLOW + "Processing input...")));
                    switch (item.command) {
                        case INPUT -> handleInput(item.input);
                    }
                }
                finally {
                    // status.update(List.of(new AttributedString("Idle")));
                }
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Agent runner interrupted. Exiting.");
        }
            
            
    }

    private float elapsedTimeInSeconds(final Stopwatch stopwatch) {
        return (float) stopwatch.elapsed().toMillis() / 1000.0f;
    }

    private void handleInput(final InputCommand input) {
        final var messages = new ArrayList<DisaplyMessage>();
        final var elapsedTimeCoounter = Stopwatch.createStarted();
        var message = "";
        try {
            final var responseF = agent.executeAsync(AgentInput
                    .<String>builder()
                    .requestMetadata(AgentRequestMetadata.builder()
                            .sessionId(sessionId)
                            .userId(user)
                            .build())
                    .request(input.input())
                    .build());
            final var response = responseF.get();
            final var error = response.getError();
            if (error.getErrorType().equals(ErrorType.SUCCESS)) {
                messages.add(DisaplyMessage.modelSuccess(sessionId,
                                                         input.runId(),
                                                         response.getData()));
                var infoMessage = Printer.Colours.WHITE + "%s %s.".formatted(
                                                                     Severity.SUCCESS
                                                                             .getEmoji(),
                                                                     response.getError()
                                                                             .getMessage());
                infoMessage += Printer.Colours.GRAY + " (Time taken: %.3f seconds, Tokens used: %d)"
                                                                                     .formatted(elapsedTimeInSeconds(elapsedTimeCoounter),
                                                                                                response.getUsage()
                                                                                                        .getTotalTokens());

                messages.add(DisaplyMessage.info(sessionId, input.runId(), infoMessage));
            }
            else {
                message = "Sentinel error: [%s] %s".formatted(error
                        .getErrorType(), error.getMessage());
                displayMessageHandler.handle(DisaplyMessage.modelError(sessionId,
                                                                       input.runId(),
                                                                       message));
            }
        }
        catch (Exception e) {
            message = AgentUtils.rootCause(e).getMessage();
            log.error("Error executing agent %s".formatted(message), e);
        }
        if (!Strings.isNullOrEmpty(message)) {
            message = Printer.Colours.RED +
                          "%s Error sending request:".formatted(Severity.ERROR
                                  .getEmoji(), message);
            message += Printer.Colours.GRAY +
                            " (Time taken: %.3f seconds)".formatted(
                                                                    elapsedTimeInSeconds(elapsedTimeCoounter));

        }
        displayMessageHandler.handle(messages);
    }

}
