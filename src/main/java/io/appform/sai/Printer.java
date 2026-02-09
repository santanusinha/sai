/*
 * Copyright 2026 authors
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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import org.jline.reader.LineReader;
import org.jline.reader.LineReader.Option;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.Status;

import io.appform.sai.models.Actor;
import io.appform.sai.models.Severity;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Printer implements AutoCloseable {

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
        public static final String WHITE_ON_DARK_GRAY_BACKGROUND = "\u001B[37;100m";
        public static final String GRAY_ON_BLACK_BACKGROUND = "\u001B[90;40m";
        public static final String RED_ON_BLACK_BACKGROUND = "\u001B[31;40m";
        public static final String YELLOW_ON_BLACK_BACKGROUND = "\u001B[33;40m";
    }

    @Value
    @Builder
    public static class Update {
        @NonNull
        Actor actor;
        @NonNull
        Severity severity;
        String colour;
        String data;
        boolean statusUpdate;
        boolean raw;
    }

    @NonNull
    private final Settings settings;

    @NonNull
    private final ExecutorService executorService;

    private final Terminal terminal = createTerminal();

    @Getter
    private final LineReader lineReader;

    private final Status status;

    private final LinkedBlockingQueue<List<Update>> printingQueue = new LinkedBlockingQueue<>();
    private Future<?> printerTask = null;

    @Builder
    public Printer(@NonNull Settings settings,
                   @NonNull ExecutorService executorService,
                   PrintWriter outputStream,
                   LineReader lineReader) {
        this.settings = settings;
        this.executorService = executorService;
        this.lineReader = Objects.requireNonNullElseGet(lineReader,
                                                        () -> LineReaderBuilder
                                                                .builder()
                                                                .terminal(terminal)
                                                                .appName(settings
                                                                        .getAppName())
                                                                .option(Option.ERASE_LINE_ON_FINISH,
                                                                        true)
                                                                .build());
        this.status = Status.getStatus(terminal);
    }

    public Printer start() {
        printerTask = executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    final var printables = printingQueue.take();
                    if(null != printables) {
                        printables.forEach(printable -> {
                            if(printable.isStatusUpdate()) {
                                status.update(List.of(new AttributedString(printable.getData())));
                            }
                            else {
                                if(printable.isRaw()) {
                                    lineReader.printAbove("%s%s".formatted(printable.getData(), Colours.RESET));
                                }
                                else {
                                    final var colour = Objects.requireNonNullElseGet(printable.getColour(), () -> defaultColour(printable.getSeverity()));
                                    lineReader.printAbove("%s %s%s%s".formatted(printable.getActor().getEmoji(), colour, printable.getData(), Colours.RESET));
                                }
                            }
                        });
                    }
                }
                catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("Shutting down printer");
                }
            }
        });
        return this;
    }

    public void print(Update update) {
        print(List.of(update));
    }

    public void print(List<Update> updates) {
        try {
            printingQueue.put(updates);
        }
        catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Failed to print updates: {}", updates, e);
        }
    }

    @Override
    public void close() throws IOException {
        if(null != printerTask) {
            printerTask.cancel(true);
        }
        terminal.close();
        log.info("Printer closed");
    }

    @SneakyThrows
    private static Terminal createTerminal() {
        return TerminalBuilder.builder().system(true).build();
    }

    private static String defaultColour(final Severity severity) {
        return switch(severity) {
            case DEBUG -> Colours.GRAY;
            case ERROR -> Colours.RED;
            case INFO -> Colours.GRAY;
            case SUCCESS -> Colours.GREEN;
            case WARNING -> Colours.YELLOW;
            case NORMAL -> Colours.WHITE;
        };
    }


}
