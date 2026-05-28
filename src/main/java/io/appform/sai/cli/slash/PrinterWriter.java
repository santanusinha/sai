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
package io.appform.sai.cli.slash;

import io.appform.sai.Printer;

import java.io.Writer;

import lombok.RequiredArgsConstructor;

/**
 * Adapter that bridges a {@link java.io.Writer} interface (required by picocli's
 * {@link java.io.PrintWriter}) to the SAI {@link Printer} abstraction. All characters written to
 * this writer are forwarded to {@link Printer#raw(String)}.
 */
@RequiredArgsConstructor
public class PrinterWriter extends Writer {

    private final Printer printer;

    @Override
    public void close() {
        // no-op: lifecycle is owned by the enclosing Printer
    }

    @Override
    public void flush() {
        // no-op: Printer output is unbuffered
    }

    @Override
    public void write(char[] cbuf, int off, int len) {
        final var str = new String(cbuf, off, len);
        if (!str.isEmpty()) {
            printer.print(Printer.raw(str));
        }
    }
}
