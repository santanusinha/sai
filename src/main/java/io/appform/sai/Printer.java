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

import java.io.PrintStream;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Printer {

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

    @Builder.Default
    private boolean headless = false;

    @Builder.Default
    private final PrintStream outputStream = System.out;

    public void success(String message) {
        println(Colours.GREEN, message);
    }

    public void warning(String message) {
        println(Colours.YELLOW, message);
    }

    public void info(String message) {
        println(Colours.BLUE, message);
    }

    public void debug(String message) {
        println(Colours.GRAY, message);
    }

    public void print(String message) {
        outputStream.println(message);
    }

    public void print(String colour, String message) {
        outputStream.print(colour + message + Colours.RESET);
        outputStream.flush();
    }

    public void println(String colour, String message) {
        outputStream.println(colour + message + Colours.RESET);
        outputStream.println();
        outputStream.flush();
    }

}
