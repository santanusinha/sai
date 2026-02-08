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
import java.util.Objects;

import org.jline.reader.LineReader;
import org.jline.reader.LineReader.Option;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.Status;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;

@Value
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

    @NonNull
    final Settings settings;

    final Terminal terminal = createTerminal();

    private final PrintWriter outputStream;

    @Getter
    private final LineReader lineReader;

    private final Status status;

    @Builder
    public Printer(@NonNull Settings settings,
                   PrintWriter outputStream,
                   LineReader lineReader) {
        this.settings = settings;
        this.outputStream = Objects.requireNonNullElseGet(outputStream,
                                                          terminal::writer);
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

    public void println(String message) {
        println(Colours.RESET + message);
    }

    /* public void print(String colour, String message) {
        lineReader.printAbove(colour + message + Colours.RESET);
        //outputStream.flush();
    } */

    public void println(String colour, String message) {
        lineReader.printAbove(colour + message + Colours.RESET + System
                .lineSeparator() + System.lineSeparator());
        // outputStream.flush();
    }

    @Override
    public void close() throws IOException {
        terminal.close();
    }

    @SneakyThrows
    private static Terminal createTerminal() {
        return TerminalBuilder.builder().system(true).build();
    }

}
