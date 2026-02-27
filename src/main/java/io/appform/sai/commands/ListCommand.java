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

import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.filesystem.session.FileSystemSessionStore;
import com.phonepe.sentinelai.session.QueryDirection;
import com.phonepe.sentinelai.session.SessionSummary;

import io.appform.sai.SaiCommand;
import io.appform.sai.Settings;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Slf4j
@Command(name = "list", description = "List available sessions")
public class ListCommand implements Callable<Integer> {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @ParentCommand
    private SaiCommand parent;

    @Override
    @SneakyThrows
    public Integer call() {
        final var settings = Settings.builder()
                .dataDir(parent.getDataDir() != null ? parent.getDataDir() : Settings.DEFAULT_DATA_DIR)
                .build();

        final var dataDirPath = Paths.get(settings.getDataDir(), "sessions");
        if (!Files.exists(dataDirPath)) {
            System.out.println("No sessions found.");
            return 0;
        }

        final var mapper = JsonUtils.createMapper();
        final var sessionStore = new FileSystemSessionStore(dataDirPath.toAbsolutePath().normalize().toString(),
                                                            mapper,
                                                            1);

        // Fetch sessions (adjust count as needed, or add a limit option)
        final var sessionsScrollable = sessionStore.sessions(100, null, QueryDirection.NEWER);
        final var sessions = sessionsScrollable.getItems();

        if (sessions.isEmpty()) {
            System.out.println("No sessions found.");
            return 0;
        }

        System.out.printf("%-40s %-25s %-50s%n", "SESSION ID", "UPDATED AT", "TITLE");
        System.out.println("-".repeat(115));

        for (SessionSummary session : sessions) {
            String title = session.getTitle();
            if (Strings.isNullOrEmpty(title)) {
                title = session.getSummary(); // Fallback to summary
                if (title != null && title.length() > 50) {
                    title = title.substring(0, 47) + "...";
                }
            }
            if (Strings.isNullOrEmpty(title)) {
                title = "No Title";
            }

            System.out.printf("%-40s %-25s %-50s%n",
                              session.getSessionId(),
                              DATE_FORMATTER.format(Instant.ofEpochMilli(session.getUpdatedAt() / 1000)),
                              title);
        }

        return 0;
    }
}
