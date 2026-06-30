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

import lombok.extern.slf4j.Slf4j;

/**
 * Monitors for Ctrl-C (SIGINT) and Ctrl-Z (SIGTSTP) during agent execution.
 *
 * <p>Ctrl-C cancels any in-flight agent task. Ctrl-Z cancels the task and then
 * re-raises SIGTSTP so the shell can background the process; on resume (SIGCONT)
 * the terminal is refreshed back to idle.
 */
@Slf4j
public class InterruptMonitor implements AutoCloseable {
    private final CommandProcessor commandProcessor;
    private final Printer printer;

    public InterruptMonitor(CommandProcessor commandProcessor, Printer printer) {
        this.commandProcessor = commandProcessor;
        this.printer = printer
                .registerSignalHandler(Signal.INT, this::handleCtrlC)
                .registerSignalHandler(Signal.CONT, this::handleCont);
        log.info("Interrupt monitor initialized");
    }

    @Override
    public void close() {
        printer.unregisterSignalHandler(Signal.INT);
        printer.unregisterSignalHandler(Signal.CONT);
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
