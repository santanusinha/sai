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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Tests for {@link AtFileCompleter}.
 *
 * <p>Covers the '@' prefix gate (no candidates when word lacks '@'), candidate
 * re-prefixing (all returned values retain '@'), and the zero-guard in
 * {@code AtParsedLine.wordCursor()} so that completion does not throw when the
 * cursor is immediately after '@'.
 *
 * <p>Note: {@link org.jline.builtins.Completers.FileNameCompleter} lists all
 * non-hidden entries in the resolved directory and does <em>not</em> filter by
 * the trailing name fragment — that filtering is done by the JLine
 * {@link LineReader} display layer. Tests therefore assert on prefix
 * re-attachment and non-emptiness rather than exact candidate counts.
 */
class AtFileCompleterTest {

    private LineReader lineReader;
    private Path tempDir;
    private AtFileCompleter completer;

    /** Minimal {@link ParsedLine} backed by fixed values. */
    private static ParsedLine parsedLine(String word, int wordCursor) {
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
                return wordCursor;
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
    void atAloneAfterCursor() {
        // word = "@", wordCursor = 1  →  AtParsedLine reduces to 0 (guarded)
        final var candidates = new ArrayList<Candidate>();
        completer.complete(lineReader, parsedLine("@", 1), candidates);
        // No assertion on count — depends on cwd listing; just must not throw.
        assertTrue(true, "Completing '@' alone must not throw");
    }

    @Test
    void atAloneZeroCursor() {
        // Edge: wordCursor = 0 should guard to 0 without going negative
        final var candidates = new ArrayList<Candidate>();
        completer.complete(lineReader, parsedLine("@", 0), candidates);
        assertTrue(true, "Completing '@' with wordCursor=0 must not throw");
    }

    // -------------------------------------------------------------------------
    // Gate tests — no '@' → no candidates
    // -------------------------------------------------------------------------

    @Test
    void atPrefixCandidatesRetainAt() {
        final var word = "@" + tempDir.toString() + "/";
        final var candidates = new ArrayList<Candidate>();
        completer.complete(lineReader, parsedLine(word, word.length()), candidates);

        assertFalse(candidates.isEmpty(), "Expected at least one candidate from temp dir");
        for (final var c : candidates) {
            assertTrue(c.value().startsWith("@"),
                       "Candidate value must retain '@', got: " + c.value());
            assertTrue(c.displ().startsWith("@"),
                       "Candidate display must retain '@', got: " + c.displ());
        }
    }

    @Test
    void atPrefixKnownFilesInCandidates() {
        final var word = "@" + tempDir.toString() + "/";
        final var candidates = new ArrayList<Candidate>();
        completer.complete(lineReader, parsedLine(word, word.length()), candidates);

        final var values = candidates.stream().map(Candidate::value).toList();
        assertTrue(values.stream().anyMatch(v -> v.endsWith("alpha.txt")),
                   "Expected alpha.txt in candidates; got: " + values);
        assertTrue(values.stream().anyMatch(v -> v.endsWith("beta.txt")),
                   "Expected beta.txt in candidates; got: " + values);
        assertTrue(values.stream().anyMatch(v -> v.endsWith("gamma.md")),
                   "Expected gamma.md in candidates; got: " + values);
    }

    @Test
    void atPrefixSubdirInCandidates() {
        final var word = "@" + tempDir.toString() + "/";
        final var candidates = new ArrayList<Candidate>();
        completer.complete(lineReader, parsedLine(word, word.length()), candidates);

        final var values = candidates.stream().map(Candidate::value).toList();
        // Directories are suffixed with "/" by FileNameCompleter
        assertTrue(values.stream().anyMatch(v -> v.contains("subdir")),
                   "Expected subdir in candidates; got: " + values);
    }

    // -------------------------------------------------------------------------
    // Re-prefix tests — all candidates must carry '@'
    // -------------------------------------------------------------------------

    @Test
    void emptyWordNoCandidates() {
        final var candidates = new ArrayList<Candidate>();
        completer.complete(lineReader, parsedLine("", 0), candidates);
        assertTrue(candidates.isEmpty(), "Expected no candidates for empty word");
    }

    @Test
    void noAtNoCandidates() {
        final var candidates = new ArrayList<Candidate>();
        completer.complete(lineReader, parsedLine("alpha", 5), candidates);
        assertTrue(candidates.isEmpty(), "Expected no candidates when word has no '@' prefix");
    }

    @Test
    void plainPathNoCandidates() {
        final var word = tempDir.toString() + "/";
        final var candidates = new ArrayList<Candidate>();
        completer.complete(lineReader, parsedLine(word, word.length()), candidates);
        assertTrue(candidates.isEmpty(), "Expected no candidates for plain path without '@'");
    }

    // -------------------------------------------------------------------------
    // Cursor-offset safety — wordCursor immediately after '@' must not throw
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
        tempDir = Files.createTempDirectory("at-completer-test-");
        Files.createFile(tempDir.resolve("alpha.txt"));
        Files.createFile(tempDir.resolve("beta.txt"));
        Files.createFile(tempDir.resolve("gamma.md"));
        Files.createDirectory(tempDir.resolve("subdir"));
        completer = new AtFileCompleter();
    }

    @AfterEach
    void tearDown() throws IOException {
        try (var walk = Files.walk(tempDir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        }
                        catch (IOException e) {
                            // best-effort cleanup
                        }
                    });
        }
    }
}
