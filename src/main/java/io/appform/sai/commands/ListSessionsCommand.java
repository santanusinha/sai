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
package io.appform.sai.commands;

import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.filesystem.session.FileSystemSessionStore;
import com.phonepe.sentinelai.session.QueryDirection;
import com.phonepe.sentinelai.session.SessionSummary;

import io.appform.sai.SaiCommand;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * {@code list-sessions} — lists sessions with ID, timestamp, and title.
 * By default only sessions from the current working directory are shown.
 * Use {@code --all} to list sessions from all directories.
 */
@Slf4j
@Command(name = "list-sessions", description = "List available sessions")
@SuppressWarnings("java:S106")
public class ListSessionsCommand implements Callable<Integer> {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @ParentCommand
    private SaiCommand parent;

    @Option(names = {
            "--all", "-a"
    }, description = "List sessions from all directories (default: current directory only)")
    private boolean all;

    private static String abbreviate(String s, int maxLen) {
        if (s.length() <= maxLen) {
            return s;
        }
        return "..." + s.substring(s.length() - (maxLen - 3));
    }

    private static String resolveTitle(SessionSummary session) {
        String title = session.getTitle();
        if (Strings.isNullOrEmpty(title)) {
            title = session.getSummary();
            if (title != null && title.length() > 50) {
                title = title.substring(0, 47) + "...";
            }
        }
        if (Strings.isNullOrEmpty(title)) {
            title = "No Title";
        }
        return title;
    }

    private static String sessionWorkDir(SessionSummary session) {
        final var extra = session.getExtra();
        if (extra == null || extra.isEmpty()) {
            return null;
        }
        final var val = extra.get("workDir");
        return val != null ? val.toString() : null;
    }

    @Override
    @SneakyThrows
    public Integer call() {
        final var settings = SaiCommand.resolveSettings(parent);

        final var dataDirPath = Paths.get(settings.getDataDir(), "sessions");
        if (!Files.exists(dataDirPath)) {
            System.out.println("No sessions found.");
            return 0;
        }

        final var mapper = JsonUtils.createMapper();
        final var sessionStore = FileSystemSessionStore.builder()
                .baseDir(dataDirPath.toAbsolutePath().normalize().toString())
                .mapper(mapper)
                .cacheSize(1)
                .build();

        final var sessionsScrollable = sessionStore.sessions(Integer.MAX_VALUE, null, QueryDirection.NEWER);
        final var allSessions = sessionsScrollable.getItems();

        if (allSessions.isEmpty()) {
            System.out.println("No sessions found.");
            return 0;
        }

        final List<SessionSummary> sessions;
        if (all) {
            sessions = allSessions;
        }
        else {
            final var currentWorkDir = settings.getWorkDir();
            sessions = allSessions.stream()
                    .filter(s -> currentWorkDir.equals(sessionWorkDir(s)))
                    .toList();
        }

        if (sessions.isEmpty()) {
            System.out.printf("No sessions found in current directory. Use --all to list all sessions.%n");
            return 0;
        }

        if (all) {
            System.out.printf("%-40s %-25s %-35s %-50s%n", "SESSION ID", "UPDATED AT", "DIRECTORY", "TITLE");
            System.out.println("-".repeat(150));
        }
        else {
            System.out.printf("%-40s %-25s %-50s%n", "SESSION ID", "UPDATED AT", "TITLE");
            System.out.println("-".repeat(115));
        }

        for (SessionSummary session : sessions) {
            final var title = resolveTitle(session);
            final var timestamp = DATE_FORMATTER.format(Instant.ofEpochMilli(session.getUpdatedAt() / 1000));

            if (all) {
                final var workDir = Objects.requireNonNullElse(sessionWorkDir(session), "(unknown)");
                System.out.printf("%-40s %-25s %-35s %-50s%n",
                                  session.getSessionId(),
                                  timestamp,
                                  abbreviate(workDir, 33),
                                  title);
            }
            else {
                System.out.printf("%-40s %-25s %-50s%n",
                                  session.getSessionId(),
                                  timestamp,
                                  title);
            }
        }

        return 0;
    }
}
