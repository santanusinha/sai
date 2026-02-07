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
        public static final String RED = "\u001B[31m";
        public static final String GREEN = "\u001B[32m";
        public static final String YELLOW = "\u001B[33m";
        public static final String BLUE = "\u001B[34m";
        public static final String PURPLE = "\u001B[35m";
        public static final String CYAN = "\u001B[36m";
        public static final String GRAY = "\u001B[90m";
        public static final String RESET = "\u001B[0m";
    }

    @Builder.Default
    private boolean headless = false;

    @Builder.Default
    private final PrintStream outputStream = System.out;

    public void success(String message) {
        printWithColour(Colours.GREEN, message);
    }

    public void warning(String message) {
        printWithColour(Colours.YELLOW, message);
    }

    public void info(String message) {
        printWithColour(Colours.BLUE, message);
    }

    public void debug(String message) {
        printWithColour(Colours.GRAY, message);
    }

    public void print(String message) {
        outputStream.println(message);
    }

    public void printWithColour(String colour, String message) {
        outputStream.println(colour + message + Colours.RESET);
    }

}
