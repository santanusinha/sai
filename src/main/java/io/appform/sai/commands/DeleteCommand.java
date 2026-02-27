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
package io.appform.sai.commands;

import io.appform.sai.SaiCommand;
import io.appform.sai.Settings;

import org.apache.commons.io.FileUtils;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Slf4j
@Command(name = "delete", description = "Delete a session")
public class DeleteCommand implements Callable<Integer> {

    @ParentCommand
    private SaiCommand parent;

    @Parameters(index = "0", description = "The session ID to delete")
    private String sessionId;

    @Override
    @SneakyThrows
    public Integer call() {
        final var settings = Settings.builder()
                .dataDir(parent.getDataDir() != null ? parent.getDataDir() : Settings.DEFAULT_DATA_DIR)
                .build();

        final var sessionDirPath = Paths.get(settings.getDataDir(), "sessions", sessionId);
        if (!Files.exists(sessionDirPath)) {
            System.err.println("Session not found: " + sessionId);
            return -1;
        }

        try {
            FileUtils.deleteDirectory(sessionDirPath.toFile());
            System.out.println("Session deleted: " + sessionId);
        }
        catch (Exception e) {
            System.err.println("Failed to delete session: " + e.getMessage());
            return -1;
        }

        return 0;
    }
}
