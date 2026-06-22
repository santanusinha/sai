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

import io.appform.sai.models.Actor;
import io.appform.sai.models.Severity;

import org.jline.keymap.KeyMap;
import org.jline.reader.Binding;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.LineReader.Option;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Reference;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.Terminal.Signal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.InfoCmp.Capability;
import org.jline.utils.Status;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.With;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Printer implements AutoCloseable {

    @UtilityClass
    public static final class Colours {
        public static final String WHITE = "\u001B[37m";
        public static final String RED = "\u001B[31m";
        public static final String GREEN = "\u001B[32m";
        public static final String YELLOW = "\u001B[33m";
        public static final String BLUE = "\u001B[34m";
        public static final String PURPLE = "\u001B[35m";
        public static final String CYAN = "\u001B[36m";
        public static final String GRAY = "\u001B[90m";
        public static final String RESET = "\u001B[0m";
        public static final String BOLD_WHITE_ON_GRAY_BACKGROUND = "\u001B[1;37;100m";
        public static final String BOLD_WHITE_ON_RED_BACKGROUND = "\u001B[1;37;41m";
        public static final String BOLD_WHITE_ON_BLACK_BACKGROUND = "\u001B[1;37;40m";
        public static final String WHITE_ON_BLACK_BACKGROUND = "\u001B[37;40m";
        public static final String WHITE_ON_DARK_GRAY_BACKGROUND = "\u001B[97;100m";
        public static final String GRAY_ON_BLACK_BACKGROUND = "\u001B[90;40m";
        public static final String RED_ON_BLACK_BACKGROUND = "\u001B[31;40m";
        public static final String YELLOW_ON_BLACK_BACKGROUND = "\u001B[33;40m";
        public static final String BOLD_YELLOW = "\u001B[1;33m";
    }

    @Value
    @Builder
    @With
    public static class Update {
        @NonNull
        Actor actor;
        @NonNull
        Severity severity;
        String colour;
        String data;
        boolean statusUpdate;
        boolean raw;
        boolean debug;
        boolean important; // Gets printed even in headless mode
    }

    @NonNull
    private final Settings settings;

    @NonNull
    private final ExecutorService executorService;

    @Getter
    private final Terminal terminal;

    @Getter
    private final LineReader lineReader;

    private final Status status;

    private final LinkedBlockingQueue<List<Update>> printingQueue = new LinkedBlockingQueue<>();
    private final Map<Signal, Consumer<Signal>> signalHandlers = new ConcurrentHashMap<>();
    private final AtomicReference<String> contextInfo = new AtomicReference<>("");
    private Future<?> printerTask = null;

    @Builder
    @SneakyThrows
    public Printer(
                   @NonNull Settings settings,
                   @NonNull ExecutorService executorService,
                   PrintWriter outputStream,
                   LineReader lineReader
    ) {
        this.settings = settings;
        this.executorService = executorService;
        final var terminalBuilder = TerminalBuilder.builder()
                .system(true);
        if (settings.isHeadless()) {
            terminalBuilder.jna(false)
                    .jni(false)
                    .ffm(false)
                    .jansi(false)
                    .type(Terminal.TYPE_DUMB)
                    .dumb(true)
                    .systemOutput(TerminalBuilder.SystemOutput.ForcedSysOut);
            System.setProperty("jline.terminal.type", "dumb");
            System.setProperty("org.jline.terminal.dumb.color", "false");
            log.info("Running in headless mode with dumb terminal");
        }
        else {
            terminalBuilder.signalHandler(signal -> {
                final var handler = signalHandlers.get(signal);
                if (null != handler) {
                    handler.accept(signal);
                }
            });
        }
        this.terminal = terminalBuilder.build();
        this.lineReader = Objects.requireNonNullElseGet(lineReader,
                                                        () -> {
                                                            DefaultParser parser = new DefaultParser();
                                                            parser.setQuoteChars(new char[]{
                                                                    '\''
                                                            });
                                                            parser.setEscapeChars(new char[]{
                                                                    '\\'
                                                            });
                                                            parser.setEofOnUnclosedBracket(DefaultParser.Bracket.CURLY,
                                                                                           DefaultParser.Bracket.ROUND,
                                                                                           DefaultParser.Bracket.SQUARE);
                                                            parser.setEofOnEscapedNewLine(true);
                                                            return LineReaderBuilder
                                                                    .builder()
                                                                    .terminal(terminal)
                                                                    .appName(settings.getAppName())
                                                                    .parser(parser)
                                                                    .completer(new AtFileCompleter())
                                                                    .variable(LineReader.SECONDARY_PROMPT_PATTERN,
                                                                              Colours.GRAY + "- " + Colours.RESET)
                                                                    .option(Option.ERASE_LINE_ON_FINISH, true)
                                                                    .build();
                                                        });
        this.status = Status.getStatus(terminal);
        bindAltEnter(this.lineReader);
    }

    public Printer start() {
        if (!settings.isHeadless()) {
            lineReader.getTerminal().puts(Capability.clear_screen);
            lineReader.getTerminal().flush();
        }

        printerTask = executorService.submit(this::processPrintingQueue);
        this.print(markIdleStatus());
        return this;
    }

    public Printer registerSignalHandler(Signal signal, Consumer<Signal> handler) {
        signalHandlers.put(signal, handler);
        return this;
    }

    public Printer unregisterSignalHandler(Signal signal) {
        signalHandlers.remove(signal);
        return this;
    }

    /**
     * Prepend {@code completer} to the active JLine completer chain. The supplied completer is
     * wrapped together with the existing completer in an {@link AggregateCompleter} so that both
     * participate in TAB-completion. A no-op when the underlying {@link LineReader} is not a
     * {@link LineReaderImpl} (e.g. in headless / test mode where a stub reader is injected).
     *
     * @param completer the {@link Completer} to prepend
     */
    /**
     * Binds Alt+Enter to {@code self-insert-unmeta} in the emacs and viins keymaps so that
     * pressing Alt+Enter inserts a literal newline into the input buffer instead of submitting
     * the line. This complements the existing backslash-continuation support.
     */
    private static void bindAltEnter(LineReader lineReader) {
        if (!(lineReader instanceof LineReaderImpl impl)) {
            return;
        }
        final Map<String, KeyMap<Binding>> keyMaps = impl.getKeyMaps();
        final var altEnter = KeyMap.alt('\r');
        final var insertNewline = new Reference(LineReader.SELF_INSERT_UNMETA);
        for (final var keyMapName : List.of(LineReader.EMACS, LineReader.VIINS)) {
            final var keyMap = keyMaps.get(keyMapName);
            if (keyMap != null) {
                keyMap.bind(insertNewline, altEnter);
            }
        }
    }

    /**
     * Prepend {@code completer} to the active JLine completer chain. The supplied completer is
     * wrapped together with the existing completer in an {@link AggregateCompleter} so that both
     * participate in TAB-completion. A no-op when the underlying {@link LineReader} is not a
     * {@link LineReaderImpl} (e.g. in headless / test mode where a stub reader is injected).
     *
     * @param completer the {@link Completer} to prepend
     */
    public void addCompleter(Completer completer) {
        if (lineReader instanceof LineReaderImpl impl) {
            impl.setCompleter(new AggregateCompleter(completer, impl.getCompleter()));
        }
    }

    @SuppressWarnings("java:S106")
    private void processPrintingQueue() {
        while (!Thread.currentThread().isInterrupted())
            try {
                final var printables = printingQueue.take();
                if (null != printables) {
                    printables.forEach(printable -> {
                        if (!settings.isDebug() && printable.isDebug()) {
                            return;
                        }
                        if (settings.isHeadless()) {
                            if (printable.isStatusUpdate()) {
                                return;
                            }
                            if (settings.isDebug()
                                    || printable.isImportant()) {
                                final var clearString = AttributedString.fromAnsi(printable.getData()).toString();
                                lineReader.printAbove(clearString);
                            }
                            return;
                        }
                        if (printable.isStatusUpdate()) {
                            status.update(List.of(AttributedString.EMPTY));
                            status.update(List.of(new AttributedString(buildStatusLine(printable.getData()))));
                        }
                        else {
                            if (printable.isRaw()) {
                                lineReader.printAbove("%s".formatted(printable.getData()));
                            }
                            else {
                                final var colour = Objects.requireNonNullElseGet(printable.getColour(),
                                                                                 () -> defaultColour(printable
                                                                                         .getSeverity()));
                                lineReader.printAbove("%s %s%s%s".formatted(printable.getActor().getEmoji(),
                                                                            colour,
                                                                            printable.getData(),
                                                                            Colours.RESET));
                            }
                        }
                    });
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Shutting down printer");
            }
    }

    public static Update markIdleStatus() {
        return statusUpdate(" Idle " + Colours.GRAY + "(Waiting for input)");
    }

    /**
     * Updates the context information displayed on the right side of the status bar.
     * Should be called whenever the active persona or model changes.
     *
     * @param personaName display name of the active persona
     * @param model       current model string (e.g. {@code copilot/claude-haiku-4.5})
     */
    public void updateContextInfo(String personaName, String model) {
        final var info = Colours.GRAY + "\uD83D\uDC64 " + Colours.WHITE + personaName
                + Colours.GRAY + " | " + Colours.CYAN + "\uD83E\uDD16 " + model + " "
                + Colours.RESET;
        contextInfo.set(info);
    }

    /**
     * Builds a full-width status-bar line: left-side {@code statusText} padded with spaces to push
     * the context info (persona + model) to the right edge of the terminal.
     */
    private String buildStatusLine(String statusText) {
        final var right = contextInfo.get();
        if (right == null || right.isEmpty()) {
            return statusText;
        }
        final var leftVisible = AttributedString.fromAnsi(statusText).length();
        final var rightVisible = AttributedString.fromAnsi(right).length();
        final var termWidth = terminal.getWidth();
        if (termWidth <= 0) {
            return statusText;
        }
        final var padding = Math.max(0, termWidth - leftVisible - rightVisible);
        return statusText + " ".repeat(padding) + right;
    }

    public static Update statusUpdate(String status) {
        return Update.builder()
                .actor(Actor.SYSTEM)
                .severity(Severity.NORMAL)
                .data(status + Colours.RESET)
                .statusUpdate(true)
                .build();
    }

    public static Update empty() {
        return raw("" + Colours.RESET);
    }

    public static Update debug(Actor actor, String data) {
        return Update.builder()
                .actor(actor)
                .severity(Severity.DEBUG)
                .data(data)
                .debug(true)
                .build();
    }

    public static Update systemMessage(String data) {
        return Update.builder()
                .actor(Actor.SYSTEM)
                .severity(Severity.NORMAL)
                .data(data)
                .build();
    }

    public static Update userMessage(String data) {
        return Update.builder()
                .actor(Actor.USER)
                .severity(Severity.NORMAL)
                .colour(Printer.Colours.BOLD_WHITE_ON_BLACK_BACKGROUND)
                .data(data)
                .build();
    }

    public static Update assistantMessage(String data) {
        return Update.builder()
                .actor(Actor.ASSISTANT)
                .severity(Severity.NORMAL)
                .colour(Printer.Colours.WHITE)
                .data(data)
                .build();
    }

    public static Update raw(String data) {
        return Update.builder()
                .actor(Actor.SYSTEM)
                .severity(Severity.NORMAL)
                .data(data)
                .raw(true)
                .build();
    }

    public void print(Update update) {
        print(List.of(update));
    }

    public void print(List<Update> updates) {
        try {
            printingQueue.put(updates);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Failed to print updates: {}", updates, e);
        }
    }

    @Override
    public void close() throws IOException {
        if (null != printerTask) {
            printerTask.cancel(true);
        }
        if (!settings.isHeadless()) {
            lineReader.printAbove(Colours.RESET);
            terminal.close();
        }
        log.info("Printer closed");
    }

    @SneakyThrows
    private static Terminal createTerminal() {
        return TerminalBuilder.builder().system(true).build();
    }

    private static String defaultColour(final Severity severity) {
        return switch (severity) {
            case DEBUG -> Colours.GRAY;
            case ERROR -> Colours.RED;
            case INFO -> Colours.GRAY;
            case SUCCESS -> Colours.GREEN;
            case WARNING -> Colours.YELLOW;
            case NORMAL -> Colours.WHITE;
        };
    }
}
