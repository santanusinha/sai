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

import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.hooks.PendingMessagesQueue;

import io.appform.sai.term.Printer;

import org.jline.keymap.KeyMap;
import org.jline.reader.Binding;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.Reference;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

/**
 * Waits for a pause key (Ctrl-Q or Esc) while an agent run is in flight, then
 * opens a temporary prompt for additional user input and pauses the run at the
 * next model call via the {@link PauseGate}.
 *
 * <p>While a run is in flight, the reader thread polls the terminal's
 * non-blocking input for the pause key. Silent line typing is NOT supported:
 * the prompt is the only way to send mid-run input. When the pause key is
 * pressed, the gate opens (the next model call blocks) and the prompt
 * {@code ✏️ additional input>} is shown on the shared JLine line reader, so
 * editing, history, and Alt+Enter multi-line entry work like the main prompt.
 * The prompt ends when:
 *
 * <ul>
 * <li>Enter is pressed — the text is queued as an additional user message
 * and the gate is released, so the paused model call picks it up.</li>
 * <li>Esc or Ctrl-G is pressed, or Enter is pressed on an empty line —
 * nothing is queued and the gate is released.</li>
 * <li>Ctrl-C is pressed — the interrupt monitor cancels the run; the stop
 * path releases the gate so the paused model call ends promptly.</li>
 * </ul>
 *
 * <p>The reader is started when a run starts and stopped when it finishes, so
 * it never races with the JLine prompt reader between runs.
 */
@Slf4j
public class MidRunInputReader implements AutoCloseable {

    private static final String ADDITIONAL_INPUT_PROMPT = "\u270F\uFE0F additional input> ";
    private static final int CTRL_Q = 0x11;

    private final Printer printer;
    private final PendingMessagesQueue pendingMessages;
    private final String sessionId;
    private final PauseGate pauseGate;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread readerThread;

    public MidRunInputReader(Printer printer,
                             PendingMessagesQueue pendingMessages,
                             String sessionId,
                             PauseGate pauseGate) {
        this.printer = printer;
        this.pendingMessages = pendingMessages;
        this.sessionId = sessionId;
        this.pauseGate = pauseGate;
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * Starts waiting for the pause key on a daemon thread. No-op when already
     * running. Always safe to call: headless mode / dumb terminals are handled
     * by the read loop itself, which exits quietly when there is no usable
     * terminal input.
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
     * Stops the reader and releases a pending pause, so a stopped reader can
     * never leave a model call blocked on the gate.
     *
     * <p>Waits (bounded) for the reader thread to exit so it stops consuming
     * terminal input before the JLine prompt reader takes over. Without this
     * wait, a keystroke typed just as a run ends can be consumed by the still
     * running reader thread and lost.
     */
    public void stop() {
        running.set(false);
        pauseGate.release();
        final var thread = readerThread;
        if (thread != null) {
            thread.interrupt();
            try {
                // The poll loop checks the running flag every 100 ms, so the
                // thread exits within one poll cycle. Join with a margin.
                thread.join(250);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private record CancelBindings(
            Map<String, Binding> emacs,
            Map<String, Binding> viins
    ) {
    }

    private Map<String, Binding> bindCancel(final KeyMap<Binding> keyMap) {
        if (keyMap == null) {
            return Map.of();
        }
        final var previous = new HashMap<String, Binding>();
        for (final var key : List.of(KeyMap.esc(), KeyMap.ctrl('G'))) {
            previous.put(key, keyMap.getBound(key));
            keyMap.bind(new Reference(LineReader.SEND_BREAK), key);
        }
        return previous;
    }

    /**
     * Binds Esc and Ctrl-G to the {@code abort} widget in the emacs and viins
     * key maps for the duration of the prompt. The widget clears the buffer,
     * so the prompt returns an empty line, which the caller treats as a
     * cancel. Earlier bindings are returned so they can be restored.
     */
    private CancelBindings installCancelBindings(final LineReader lineReader) {
        if (!(lineReader instanceof LineReaderImpl impl)) {
            return new CancelBindings(Map.of(), Map.of());
        }
        final var keyMaps = impl.getKeyMaps();
        return new CancelBindings(bindCancel(keyMaps.get(LineReader.EMACS)),
                                  bindCancel(keyMaps.get(LineReader.VIINS)));
    }

    /**
     * Opens the additional-input prompt on the shared line reader. The gate is
     * opened first, so the next model call pauses before the user types; it is
     * released when the prompt ends, whatever the outcome.
     */
    private void openPrompt() {
        if (!running.get()) {
            return;
        }
        pauseGate.open();
        printer.print(Printer.systemMessage("\u23F8 Paused for your input")
                .withColour(Printer.Colours.BOLD_YELLOW)
                .withImportant(true));
        final String text = readPrompt(printer.getLineReader());
        pauseGate.release();
        printer.print(Printer.systemMessage("\u25B6 Resumed").withImportant(true));
        if (!Strings.isNullOrEmpty(text)) {
            queue(text);
        }
    }

    private void queue(final String line) {
        pendingMessages.add(UserPrompt.text(sessionId,
                                            "run-" + UUID.randomUUID(),
                                            "(additional user input while you work) " + line,
                                            LocalDateTime.now()));
        printer.print(Printer.systemMessage(
                                            "\uD83D\uDCE5 Queued for the model: " + line).withImportant(true));
        log.info("Queued mid-run user input ({} chars)", line.length());
    }

    private void readLoop() {
        final var terminal = printer.getTerminal();
        if (terminal == null || Terminal.TYPE_DUMB.equals(terminal.getType())) {
            // Headless mode or dumb terminal: nothing to read mid-run.
            return;
        }
        final var nonBlocking = terminal.reader();
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
                if (available == CTRL_Q || available == KeyMap.esc().charAt(0)) {
                    openPrompt();
                }
                // All other keys are ignored: the prompt is the only way to
                // send mid-run input, and Ctrl-C is handled by the interrupt
                // monitor, which cancels the run.
            }
        }
        catch (Exception e) {
            if (running.get()) {
                log.warn("Mid-run input reader stopped: {}", e.getMessage());
            }
        }
    }

    /**
     * Reads the additional input on the shared line reader with Esc and Ctrl-G
     * bound to prompt cancellation, and their earlier bindings restored after.
     */
    private String readPrompt(final LineReader lineReader) {
        final var cancelBindings = installCancelBindings(lineReader);
        try {
            return lineReader.readLine(ADDITIONAL_INPUT_PROMPT);
        }
        catch (UserInterruptException e) {
            // Ctrl-C at the prompt: the run is being cancelled by the interrupt
            // monitor; drop the text and let the cancel path proceed.
            return null;
        }
        catch (EndOfFileException e) {
            return null;
        }
        finally {
            restoreBindings(lineReader, cancelBindings);
        }
    }

    private void restore(final KeyMap<Binding> keyMap, final Map<String, Binding> previous) {
        if (keyMap == null) {
            return;
        }
        previous.forEach((key, binding) -> {
            if (binding == null) {
                keyMap.unbind(key);
            }
            else {
                keyMap.bind(binding, key);
            }
        });
    }

    private void restoreBindings(final LineReader lineReader, final CancelBindings cancelBindings) {
        if (!(lineReader instanceof LineReaderImpl impl)) {
            return;
        }
        final var keyMaps = impl.getKeyMaps();
        restore(keyMaps.get(LineReader.EMACS), cancelBindings.emacs());
        restore(keyMaps.get(LineReader.VIINS), cancelBindings.viins());
    }
}
