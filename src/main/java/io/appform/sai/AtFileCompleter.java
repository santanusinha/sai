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

import org.jline.builtins.Completers.FileNameCompleter;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.ArrayList;
import java.util.List;

/**
 * A JLine {@link Completer} that delegates to {@link FileNameCompleter} only when
 * the word being completed starts with {@code @}. The {@code @} prefix is stripped
 * before path resolution and re-added to every returned candidate so the completed
 * value keeps the {@code @} prefix in the input line.
 */
public class AtFileCompleter implements Completer {

    private static final List<String> MEDIA_PREFIXES = List.of("image:", "image-url:", "audio:");

    /**
     * Wraps a {@link ParsedLine} replacing the current word with a stripped version
     * (i.e. without the leading {@code @}) so that {@link FileNameCompleter} sees a
     * plain path fragment.
     */
    private static final class AtParsedLine implements ParsedLine {

        private final ParsedLine delegate;
        private final String strippedWord;

        AtParsedLine(ParsedLine delegate, String strippedWord) {
            this.delegate = delegate;
            this.strippedWord = strippedWord;
        }

        @Override
        public int cursor() {
            return delegate.cursor();
        }

        @Override
        public String line() {
            return delegate.line();
        }

        @Override
        public String word() {
            return strippedWord;
        }

        @Override
        public int wordCursor() {
            // Shift the cursor by the number of stripped characters so that it stays
            // within the stripped word. FileNameCompleter does word().substring(0, wordCursor())
            // and throws when wordCursor exceeds the stripped word length.
            final var stripLength = delegate.word().length() - strippedWord.length();
            final var originalCursor = delegate.wordCursor();
            return originalCursor > stripLength ? originalCursor - stripLength : 0;
        }

        @Override
        public int wordIndex() {
            return delegate.wordIndex();
        }

        @Override
        public List<String> words() {
            return delegate.words();
        }
    }

    private final FileNameCompleter delegate = new FileNameCompleter();

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        final var word = line.word();
        if (!word.startsWith("@")) {
            return;
        }

        // Check for media type prefix: @image:, @image-url:, @audio:
        String mediaPrefix = null;
        for (final var prefix : MEDIA_PREFIXES) {
            if (word.startsWith("@" + prefix)) {
                mediaPrefix = prefix;
                break;
            }
        }

        if (mediaPrefix != null) {
            // Strip "@prefix:" and complete the file path. Guard against partial input like
            // "@image" or "@image:" so substring stays in bounds while the user types.
            final var prefixLength = 1 + mediaPrefix.length() + 1; // '@' + prefix + ':'
            final var pathFragment = prefixLength <= word.length()
                    ? word.substring(prefixLength)
                    : "";
            final var strippedLine = new AtParsedLine(line, pathFragment);
            final var delegateCandidates = new ArrayList<Candidate>();
            delegate.complete(reader, strippedLine, delegateCandidates);

            for (final var c : delegateCandidates) {
                candidates.add(new Candidate(
                                             "@" + mediaPrefix + c.value(),
                                             "@" + mediaPrefix + c.displ(),
                                             c.group(),
                                             c.descr(),
                                             c.suffix(),
                                             c.key(),
                                             c.complete()
                ));
            }
            return;
        }

        // Suggest media prefixes when user types just "@"
        if (word.equals("@")) {
            for (final var prefix : MEDIA_PREFIXES) {
                candidates.add(new Candidate(
                                             "@" + prefix,
                                             "@" + prefix,
                                             "media",
                                             "Media: " + prefix,
                                             null,
                                             "media-" + prefix,
                                             false
                ));
            }
        }


        final var pathFragment = word.substring(1);
        final var strippedLine = new AtParsedLine(line, pathFragment);
        final var delegateCandidates = new ArrayList<Candidate>();
        delegate.complete(reader, strippedLine, delegateCandidates);

        for (final var c : delegateCandidates) {
            candidates.add(new Candidate(
                                         "@" + c.value(),
                                         "@" + c.displ(),
                                         c.group(),
                                         c.descr(),
                                         c.suffix(),
                                         c.key(),
                                         c.complete()
            ));
        }
    }
}
