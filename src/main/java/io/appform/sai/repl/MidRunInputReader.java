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
package io.appform.sai.repl;

import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.hooks.PendingMessagesQueue;

import io.appform.sai.term.Printer;

import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

/**
 * Reads raw terminal input while an agent run is in flight and queues each
 * complete line as an additional user message, so the model receives mid-run
 * feedback in the next request without the run being interrupted.
 *
 * <p>The reader polls the terminal's non-blocking input stream. Lines are
 * queued into the session {@link PendingMessagesQueue} (picked up by the
 * pending-message injector on the next model call) and acknowledged in the
 * terminal. Ctrl-C is NOT consumed here: it is handled by the interrupt monitor,
 * which cancels the run.
 *
 * <p>The reader is started when a run starts and stopped when it finishes, so
 * it never races with the JLine prompt reader between runs.
 */
@Slf4j
public class MidRunInputReader implements AutoCloseable {

    private final Printer printer;
    private final PendingMessagesQueue pendingMessages;
    private final String sessionId;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread readerThread;

    public MidRunInputReader(Printer printer, PendingMessagesQueue pendingMessages, String sessionId) {
        this.printer = printer;
        this.pendingMessages = pendingMessages;
        this.sessionId = sessionId;
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * Starts reading mid-run input on a daemon thread. No-op when already running.
     * Always safe to call: headless mode / dumb terminals are handled by the read
     * loop itself, which exits quietly when there is no usable terminal input.
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        readerThread = new Thread(this::readLoop, "sai-midrun-input");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * Stops the reader. Safe to call when not started.
     */
    public void stop() {
        running.set(false);
        final var thread = readerThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void queue(final String line) {
        pendingMessages.add(UserPrompt.text(sessionId,
                                            "run-" + UUID.randomUUID(),
                                            "(additional user input while you work) " + line,
                                            LocalDateTime.now()));
        printer.print(Printer.systemMessage(
                                            "📥 Queued for the model: " + line).withImportant(true));
        log.info("Queued mid-run user input ({} chars)", line.length());
    }

    private void readLoop() {
        final var terminal = printer.getTerminal();
        if (terminal == null || Terminal.TYPE_DUMB.equals(terminal.getType())) {
            // Headless mode or dumb terminal: nothing to read mid-run.
            return;
        }
        final var nonBlocking = terminal.reader();
        final var buffer = new StringBuilder();
        try {
            while (running.get()) {
                final int available;
                try {
                    available = nonBlocking.read(100L);
                }
                catch (IOException e) {
                    // Terminal I/O problem: stop reading quietly.
                    log.debug("Mid-run input reader I/O stopped: {}", e.getMessage());
                    return;
                }
                if (available == NonBlockingReader.EOF) {
                    // EOF on the terminal; stop reading quietly.
                    return;
                }
                if (available == NonBlockingReader.READ_EXPIRED) {
                    // Nothing available within the timeout; re-check the running flag.
                    continue;
                }
                if (available == '\r' || available == '\n') {
                    final var line = buffer.toString().trim();
                    buffer.setLength(0);
                    if (!line.isEmpty()) {
                        queue(line);
                    }
                }
                else if (available == 127 || available == 8) {
                    // Backspace / Ctrl-H: drop the last buffered char if any.
                    final var length = buffer.length();
                    if (length > 0) {
                        buffer.setLength(length - 1);
                    }
                }
                else if (available >= 32 && available < 127) {
                    buffer.append((char) available);
                }
                // Other control chars (arrow keys, Ctrl-C, etc.) are ignored: Ctrl-C
                // is handled by the interrupt monitor, which cancels the run.
            }
        }
        catch (Exception e) {
            if (running.get()) {
                log.warn("Mid-run input reader stopped: {}", e.getMessage());
            }
        }
    }

}
