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

import org.jline.terminal.Terminal;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

/**
 * Monitors for Ctrl-C interrupts during agent execution.
 * Uses JLine Terminal to detect Ctrl-C in a portable, non-blocking way.
 */
@Slf4j
public class InterruptMonitor implements AutoCloseable {
    private final CommandProcessor commandProcessor;
    private final Printer printer;
    private final Terminal terminal;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean monitoring = new AtomicBoolean(false);
    private Thread monitorThread;

    public InterruptMonitor(CommandProcessor commandProcessor, Printer printer, Terminal terminal) {
        this.commandProcessor = commandProcessor;
        this.printer = printer;
        this.terminal = terminal;
    }

    @Override
    public void close() {
        stopMonitoring();
    }

    /**
     * Start monitoring for Ctrl-C interrupts
     */
    public void startMonitoring() {
        if (monitoring.compareAndSet(false, true)) {
            running.set(true);
            monitorThread = new Thread(this::monitorLoop, "interrupt-monitor");
            monitorThread.setDaemon(true);
            monitorThread.start();
            log.debug("Interrupt monitor started");
        }
    }

    /**
     * Stop monitoring for Ctrl-C interrupts
     */
    public void stopMonitoring() {
        if (monitoring.compareAndSet(true, false)) {
            running.set(false);
            if (monitorThread != null) {
                monitorThread.interrupt();
                try {
                    monitorThread.join(100);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            log.debug("Interrupt monitor stopped");
        }
    }

    private void monitorLoop() {
        try {
            while (running.get()) {
                if (terminal.reader().peek(50) > 0) {
                    int ch = terminal.reader().read();
                    // Ctrl-C is character 3 (ETX - End of Text)
                    if (ch == 3) {
                        log.info("Ctrl-C detected during execution");
                        printer.print(Printer.systemMessage(Printer.Colours.YELLOW +
                                "\n⚠️  Interrupting agent execution..." + Printer.Colours.RESET));
                        commandProcessor.cancelRunningTask();
                        // Consume any additional input to prevent multiple interrupts
                        while (terminal.reader().peek(10) > 0) {
                            terminal.reader().read();
                        }
                        break;
                    }
                }
                Thread.sleep(50);
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("Interrupt monitor thread interrupted");
        }
        catch (IOException e) {
            log.debug("Error reading terminal input: {}", e.getMessage());
        }
    }
}
