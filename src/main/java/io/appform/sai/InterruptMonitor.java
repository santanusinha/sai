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
 * Monitors for Ctrl-C interrupts during agent execution.
 */
@Slf4j
public class InterruptMonitor implements AutoCloseable {
    private final CommandProcessor commandProcessor;
    private final Printer printer;

    public InterruptMonitor(CommandProcessor commandProcessor, Printer printer) {
        this.commandProcessor = commandProcessor;
        this.printer = printer.registerSignalHandler(Signal.INT, this::handleCtrlC);
        log.info("Interrupt monitor initialized");
    }

    @Override
    public void close() {
        printer.unregisterSignalHandler(Signal.INT);
    }

    public void handleCtrlC(Signal signal) {
        log.info("Ctrl-C detected during execution");
        printer.print(Printer.systemMessage(Printer.Colours.YELLOW +
                "\n⚠️  Interrupting agent execution..." + Printer.Colours.RESET));
        commandProcessor.cancelRunningTask();
    }
}
