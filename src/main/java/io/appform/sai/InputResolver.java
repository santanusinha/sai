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
package io.appform.sai;

import com.google.common.base.Strings;

import io.appform.sai.term.Printer;

import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * Reads and resolves user input from the prompt, piped stdin, and {@code @file} references.
 *
 * <p>Media tokens ({@code @image:}, {@code @image-url:}, {@code @audio:}) are handled by
 * {@link MediaParser}; this class handles everything else.
 */
@Slf4j
public final class InputResolver {

    private InputResolver() {
        // Utility class
    }

    /**
     * Reads one line from the interactive prompt. Ctrl-C and Ctrl-D at the prompt
     * return {@link Optional#empty()}, which the REPL loop treats as an exit request.
     *
     * @param printer the active printer (owns the JLine terminal)
     * @return the entered line, or empty on interrupt/EOF
     */
    public static Optional<String> readInput(final Printer printer) {
        try {
            printer.getTerminal().writer().print("\007");
            printer.getTerminal().writer().flush();
            return Optional.of(printer.getLineReader().readLine(printer.buildPrompt()));
        }
        catch (EndOfFileException | UserInterruptException e) {
            return Optional.empty();
        }
    }

    /**
     * Reads all of {@code System.in} when stdin is piped (non-interactive, no explicit
     * {@code --input} flag) and returns the content as a single string. Returns {@code null} when
     * running interactively, in headless mode, or when {@code --input} was already specified.
     *
     * @param explicitInput value of the {@code --input} flag (may be {@code null})
     * @param headless      {@code true} when running in headless mode
     * @return the piped stdin content, or {@code null} if not applicable
     * @throws IllegalStateException if stdin appears to be piped but is empty, or on read failure
     */
    public static String readPipedInput(final String explicitInput, final boolean headless) {
        if (!headless && System.console() == null && Strings.isNullOrEmpty(explicitInput)) {
            try {
                if (System.in.available() == 0) {
                    throw new IllegalStateException("No TTY detected and no input provided. " +
                            "Please run interactively with a TTY, pipe input via stdin, or use the --input flag.");
                }
                return new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))
                        .lines()
                        .collect(Collectors.joining("\n"))
                        .strip();
            }
            catch (IOException e) {
                throw new IllegalStateException("Failed to read from standard input", e);
            }
        }
        return null;
    }

    /**
     * Resolves {@code @file} references in the text prompt. A leading {@code @} reads the
     * named file; other {@code @token} occurrences are stripped. Media tokens pass through
     * untouched for {@link MediaParser}.
     *
     * @param input the text prompt after media parsing
     * @return the resolved prompt text
     */
    public static String resolveInput(final String input) {
        if (input.startsWith("@") && !input.startsWith("@image:") && !input.startsWith("@image-url:") && !input
                .startsWith("@audio:")) {
            final var filePath = input.substring(1);
            if (Strings.isNullOrEmpty(filePath)) {
                throw new IllegalArgumentException("--input '@' requires a file path");
            }
            try {
                return Files.readString(Paths.get(filePath), StandardCharsets.UTF_8);
            }
            catch (IOException e) {
                throw new IllegalArgumentException("Cannot read input file: " + filePath, e);
            }
        }
        return input.replaceAll("@(?!image:|image-url:|audio:)(\\S+)", "$1");
    }
}
