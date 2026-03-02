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

import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.UUID;

import lombok.Builder;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.With;

@Value
@Builder
@With
public class Settings {
    public static final String DEFAULT_DATA_DIR_SUFFIX = "/.local/state/sai";

    @Builder.Default
    String appName = "sai";

    boolean debug;

    boolean headless;

    boolean noSession;

    @Builder.Default
    String sessionId = UUID.randomUUID().toString();

    @Builder.Default
    String user = Optional.ofNullable(System.getProperty("user.name"))
            .orElseGet(() -> System.getenv().getOrDefault("USER", "User"));

    @Builder.Default
    String dataDir = Optional.ofNullable(System.getProperty("user.home"))
            .or(() -> Optional.ofNullable(System.getenv("HOME")))
            .orElseThrow(() -> new IllegalStateException("Cannot determine user home directory"))
            + DEFAULT_DATA_DIR_SUFFIX;

    @Builder.Default
    String workDir = ensureWorkDir();

    @SneakyThrows
    private static String ensureWorkDir() {
        return Optional.ofNullable(System.getProperty("user.dir"))
                .orElseGet(() -> {
                    try {
                        return System.getenv()
                                .getOrDefault("PWD",
                                              Files.createTempDirectory("sai-workdir").toString());
                    }
                    catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                });
    }
}
