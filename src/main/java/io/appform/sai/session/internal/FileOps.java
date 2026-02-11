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
package io.appform.sai.session.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.utils.AgentUtils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class FileOps {
    public static boolean atomicWriteJson(Path target, Object data, ObjectMapper mapper) {
        final var tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        try (final var os = Files.newOutputStream(tmp)) {
            mapper.writeValue(os, data);
        }
        catch (IOException e) {
            return false;
        }
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        }
        catch (IOException e) {
            // Fallback if atomic move not supported
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
            catch (IOException ex) {
                final var root = AgentUtils.rootCause(ex);
                log.error("Could not create file %s: %s".formatted(target, root.getMessage()), root);
                return false;
            }
        }
    }

    public static String readLineAtOffset(Path file, long offset) throws IOException {
        try (final var raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(offset);
            final var baos = new ByteArrayOutputStream();
            var bytesRead = -1;
            while ((bytesRead = raf.read()) != -1) {
                if (bytesRead == 10) break; // newline character\n
                baos.write(bytesRead);
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
