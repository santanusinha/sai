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
package io.appform.sai.repl;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import net.loomchild.segment.srx.SrxDocument;
import net.loomchild.segment.srx.SrxTextIterator;
import net.loomchild.segment.srx.io.Srx2StaxParser;

/**
 * Sentence segmentation for streamed text, based on the SRX {@code segment}
 * library with the bundled LanguageTool rule set ({@code segment.srx}). The
 * rules know about abbreviations ({@code Dr.}, {@code e.g.}, {@code No.}),
 * decimals ({@code 3.5}), URLs, initials and ordered-list markers
 * ({@code 1.}), so those do not cause false sentence breaks.
 *
 * <h3>Hold-back margin</h3> The class is stateless: the caller keeps the
 * un-emitted text (the residual) and passes it to {@link #emittablePrefix}
 * after each chunk. SRX rules for a boundary depend on the text that follows
 * it; for example {@code "Dr."} becomes a sentence end only when no more text
 * follows. {@link #emittablePrefix} therefore returns only text up to the
 * last sentence boundary that lies at least {@code margin} characters before
 * the end of the given text. A boundary inside the margin may still be
 * extended by more text, so the caller must hold it back.
 */
final class SrxSentenceSplitter {

    /** Default margin in characters held back after the last emitted boundary. */
    static final int MARGIN = 80;

    private static final String SRX_RESOURCE = "/segment.srx";

    /** Parsed once; SrxDocument is immutable after parsing. */
    private static volatile SrxDocument document;

    private final String languageCode;

    SrxSentenceSplitter() {
        this(Locale.getDefault());
    }

    SrxSentenceSplitter(Locale locale) {
        this.languageCode = locale != null ? locale.getLanguage() : "en";
    }

    private static SrxDocument document() {
        var local = document;
        if (local == null) {
            synchronized (SrxSentenceSplitter.class) {
                local = document;
                if (local == null) {
                    local = parseDocument();
                    document = local;
                }
            }
        }
        return local;
    }

    /**
     * Parse the bundled SRX rules with the StAX parser. Do not use
     * {@code SrxAnyParser} or {@code Srx2Parser}: those use JAXB, and the JAXB
     * runtime (jaxb-impl) needs its {@code Multi-Release: true} manifest entry
     * to reach the Java 9+ copy of {@code Injector}. The shade plugin drops
     * that entry, so JAXB fails at run time in the fat jar with
     * {@code NoSuchMethodException: sun.misc.Unsafe.defineClass}. The StAX
     * parser uses only the JDK StAX API and produces the same rules.
     */
    private static SrxDocument parseDocument() {
        try (Reader reader = new InputStreamReader(
                                                   Objects.requireNonNull(SrxSentenceSplitter.class.getResourceAsStream(
                                                                                                                        SRX_RESOURCE),
                                                                          "Missing bundled resource " + SRX_RESOURCE),
                                                   StandardCharsets.UTF_8)) {
            return new Srx2StaxParser().parse(reader);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + SRX_RESOURCE, e);
        }
    }

    /**
     * Return the prefix of {@code text} that is safe to emit now: all text up
     * to the last sentence boundary that lies at least {@code margin}
     * characters before the end of {@code text}. Return an empty string when
     * no boundary is safe yet; the caller must keep the whole text.
     */
    String emittablePrefix(String text, int margin) {
        if (text.length() <= margin) {
            return "";
        }
        final var safeLimit = text.length() - margin;
        final var boundary = lastSafeBoundary(text, safeLimit);
        return boundary == 0 ? "" : text.substring(0, boundary);
    }

    /**
     * Split a complete text into sentences. Useful for tests and for callers
     * that hold the whole text at once.
     */
    List<String> split(String text) {
        final var sentences = new ArrayList<String>();
        final var iterator = new SrxTextIterator(document(), languageCode, text);
        while (iterator.hasNext()) {
            final var sentence = iterator.next();
            if (sentence == null) {
                break;
            }
            sentences.add(sentence);
        }
        return sentences;
    }

    /**
     * Return the offset just past the last sentence boundary of {@code text}
     * that is not greater than {@code safeLimit}. A boundary beyond
     * {@code safeLimit} may still be extended by more text, so the text
     * before it must stay held.
     */
    private int lastSafeBoundary(String text, int safeLimit) {
        int boundary = 0;
        final var iterator = new SrxTextIterator(document(), languageCode, text);
        while (iterator.hasNext()) {
            final var sentence = iterator.next();
            if (sentence == null) {
                break;
            }
            final var end = boundary + sentence.length();
            if (end > safeLimit || end >= text.length()) {
                break;
            }
            boundary = end;
        }
        return boundary;
    }
}
