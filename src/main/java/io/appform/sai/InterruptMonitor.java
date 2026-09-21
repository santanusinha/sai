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

import org.jline.terminal.Terminal.Signal;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Single owner of SIGINT/SIGCONT behaviour outside the input prompt.
 *
 * <p>While an agent run is in flight, Ctrl-C cancels the run. While the REPL
 * is idle (waiting at the prompt), the JLine {@code LineReader} owns SIGINT
 * and raises {@code UserInterruptException}, which the REPL loop treats as an
 * exit request. This monitor registers its INT handler only while a run is
 * active, so the two paths never race.
 *
 * <p>On SIGCONT (resume after Ctrl-Z) the terminal is refreshed back to idle.
 */
@Slf4j
public class InterruptMonitor implements AutoCloseable {
    private final CommandProcessor commandProcessor;
    private final Printer printer;
    @Getter
    private volatile boolean runInFlight;

    public InterruptMonitor(CommandProcessor commandProcessor, Printer printer) {
        this.commandProcessor = commandProcessor;
        this.printer = printer;
        printer.registerSignalHandler(Signal.CONT, this::handleCont);
        log.info("Interrupt monitor initialized");
    }

    @Override
    public void close() {
        printer.unregisterSignalHandler(Signal.INT);
        printer.unregisterSignalHandler(Signal.CONT);
    }

    /**
     * Unregisters the INT handler after a run completes so Ctrl-C at the
     * prompt falls back to the JLine {@code UserInterruptException} path.
     */
    public void runFinished() {
        printer.unregisterSignalHandler(Signal.INT);
        runInFlight = false;
    }

    /**
     * Registers the INT handler for the duration of one agent run.
     *
     * <p>Call before the run starts and pair with {@link #runFinished()} in a
     * {@code finally} block. While the handler is registered, Ctrl-C cancels
     * the in-flight run instead of exiting.
     */
    public void runStarted() {
        runInFlight = true;
        printer.registerSignalHandler(Signal.INT, this::handleCtrlC);
    }

    private void handleCont(Signal signal) {
        log.info("SIGCONT received \u2014 resumed");
        printer.print(Printer.markIdleStatus());
    }

    private void handleCtrlC(Signal signal) {
        log.info("Ctrl-C detected during execution");
        printer.print(Printer.systemMessage(Printer.Colours.YELLOW
                + "\n\u26a0\ufe0f  Interrupting agent execution..." + Printer.Colours.RESET));
        commandProcessor.cancelRunningTask();
    }
}
