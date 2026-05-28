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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Tests for {@link SlashCommandCompleter}.
 *
 * <p>Covers the four required scenarios:
 * <ol>
 * <li>No-op when word lacks the {@code /} prefix.</li>
 * <li>Empty prefix ({@code /}) returns all registered commands.</li>
 * <li>Partial prefix filters correctly to matching commands only.</li>
 * <li>Every completed candidate value has {@code /} prepended.</li>
 * </ol>
 */
class SlashCommandCompleterTest {

    @Command(name = "help", description = "Show help")
    private static class HelpCommand implements Runnable {
        @Override
        public void run() {
        }
    }

    @Command(name = "model", description = "Switch the active model")
    private static class ModelCommand implements Runnable {
        @Override
        public void run() {
        }
    }

    @Command(name = "persona", description = "Switch the active persona")
    private static class PersonaCommand implements Runnable {
        @Override
        public void run() {
        }
    }

    /** Stub root command — sub-commands are what the completer inspects. */
    @Command(name = "root")
    private static class RootCommand implements Runnable {
        @Override
        public void run() {
        }
    }

    private SlashCommandCompleter completer;
    private LineReader lineReader;

    /** Minimal {@link ParsedLine} backed by fixed word value. */
    private static ParsedLine parsedLine(String word) {
        return new ParsedLine() {
            @Override
            public int cursor() {
                return word.length();
            }

            @Override
            public String line() {
                return word;
            }

            @Override
            public String word() {
                return word;
            }

            @Override
            public int wordCursor() {
                return word.length();
            }

            @Override
            public int wordIndex() {
                return 0;
            }

            @Override
            public List<String> words() {
                return List.of(word);
            }
        };
    }

    @Test
    void allCandidateValuesHaveSlashPrefix() {
        final var candidates = new ArrayList<Candidate>();
        completer.complete(lineReader, parsedLine("/"), candidates);

        assertFalse(candidates.isEmpty());
        for (final var candidate : candidates) {
            assertTrue(
                       candidate.value().startsWith("/"),
                       "candidate value must start with '/'");
            assertTrue(
                       candidate.displ().startsWith("/"),
                       "candidate display must start with '/'");
        }
    }

    // -------------------------------------------------------------------------
    // Gate: word without '/' → no candidates produced
    // -------------------------------------------------------------------------

    @Test
    void emptyWordProducesNoCandidates() {
        final var candidates = new ArrayList<Candidate>();
        completer.complete(lineReader, parsedLine(""), candidates);
        assertTrue(candidates.isEmpty());
    }

    @Test
    void fullCommandNameStillCompletesWithSlash() {
        final var candidates = new ArrayList<Candidate>();
        completer.complete(lineReader, parsedLine("/model"), candidates);

        final var values = candidates.stream().map(Candidate::value).toList();
        assertEquals(List.of("/model"), values);
    }

    // -------------------------------------------------------------------------
    // Empty prefix ('/') → all commands returned
    // -------------------------------------------------------------------------

    @Test
    void noSlashPrefixProducesNoCandidates() {
        final var candidates = new ArrayList<Candidate>();
        completer.complete(lineReader, parsedLine("model"), candidates);
        assertTrue(candidates.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Partial prefix → only matching commands returned
    // -------------------------------------------------------------------------

    @Test
    void partialPrefixFiltersToMatchingCommand() {
        final var candidates = new ArrayList<Candidate>();
        completer.complete(lineReader, parsedLine("/mo"), candidates);

        final var values = candidates.stream().map(Candidate::value).toList();
        assertEquals(List.of("/model"), values);
    }

    @Test
    void partialPrefixNoMatchProducesNoCandidates() {
        final var candidates = new ArrayList<Candidate>();
        completer.complete(lineReader, parsedLine("/xyz"), candidates);
        assertTrue(candidates.isEmpty());
    }

    @Test
    void partialPrefixPersonaFiltersCorrectly() {
        final var candidates = new ArrayList<Candidate>();
        completer.complete(lineReader, parsedLine("/per"), candidates);

        final var values = candidates.stream().map(Candidate::value).toList();
        assertEquals(List.of("/persona"), values);
    }

    // -------------------------------------------------------------------------
    // All candidate values must carry '/' prefix
    // -------------------------------------------------------------------------

    @BeforeEach
    void setUp() throws IOException {
        System.setProperty("jline.terminal.type", "dumb");
        final var terminal = TerminalBuilder.builder()
                .dumb(true)
                .system(false)
                .build();
        lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build();

        final var commandLine = new CommandLine(new RootCommand());
        commandLine.addSubcommand("model", new ModelCommand());
        commandLine.addSubcommand("persona", new PersonaCommand());
        commandLine.addSubcommand("help", new HelpCommand());

        completer = new SlashCommandCompleter(commandLine);
    }

    @Test
    void slashAloneReturnsAllCommands() {
        final var candidates = new ArrayList<Candidate>();
        completer.complete(lineReader, parsedLine("/"), candidates);

        final var values = candidates.stream().map(Candidate::value).toList();
        assertEquals(3, values.size());
        assertTrue(values.containsAll(List.of("/model", "/persona", "/help")));
    }
}
