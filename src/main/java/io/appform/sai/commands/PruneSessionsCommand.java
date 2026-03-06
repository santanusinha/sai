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

import org.apache.commons.io.FileUtils;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Slf4j
@Command(name = "prune-sessions", description = "Prune older sessions. Provide a duration string like '1d', '3h', '30m'")
@SuppressWarnings("java:S106")
public class PruneSessionsCommand implements Callable<Integer> {

    @ParentCommand
    private SaiCommand parent;

    @Parameters(index = "0", description = "The duration string to keep. e.g., '1d' (1 day), '3h' (3 hours), '30m' (30 minutes)")
    private String durationString;

    @Override
    @SneakyThrows
    public Integer call() {
        final var durationOpt = parseDuration(durationString);
        if (durationOpt == null) {
            System.err.println("Invalid duration format: " + durationString + ". Valid formats: '1d', '3h', '30m'");
            return -1;
        }

        final var cutoffEpochMicros = Instant.now().minus(durationOpt).toEpochMilli() * 1000L;

        final var settingsBuilder = Settings.builder();
        if (!Strings.isNullOrEmpty(parent.getDataDir())) {
            settingsBuilder.dataDir(parent.getDataDir());
        }
        final var settings = settingsBuilder.build();

        final var dataDirPath = Paths.get(settings.getDataDir(), "sessions");
        if (!Files.exists(dataDirPath)) {
            System.out.println("No sessions found to prune.");
            return 0;
        }

        final var mapper = JsonUtils.createMapper();
        final var sessionStore = new FileSystemSessionStore(dataDirPath.toAbsolutePath().normalize().toString(),
                                                            mapper,
                                                            1);

        final var sessionsScrollable = sessionStore.sessions(1000, null, QueryDirection.NEWER);
        final var sessions = sessionsScrollable.getItems();

        if (sessions.isEmpty()) {
            System.out.println("No sessions found to prune.");
            return 0;
        }

        int prunedCount = 0;
        for (SessionSummary session : sessions) {
            if (session.getUpdatedAt() < cutoffEpochMicros) {
                final var sessionDirPath = Paths.get(settings.getDataDir(), "sessions", session.getSessionId());
                try {
                    FileUtils.deleteDirectory(sessionDirPath.toFile());
                    System.out.println("Deleted session: " + session.getSessionId());
                    prunedCount++;
                }
                catch (Exception e) {
                    System.err.println("Failed to delete session: " + session.getSessionId() + " (" + e.getMessage()
                            + ")");
                }
            }
        }

        System.out.println("Pruned " + prunedCount + " session(s) older than " + durationString + ".");
        return 0;
    }

    private Duration parseDuration(String s) {
        if (s == null || s.isEmpty()) return null;
        s = s.trim().toLowerCase();
        try {
            if (s.endsWith("d")) {
                return Duration.ofDays(Long.parseLong(s.substring(0, s.length() - 1)));
            }
            else if (s.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(s.substring(0, s.length() - 1)));
            }
            else if (s.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(s.substring(0, s.length() - 1)));
            }
        }
        catch (NumberFormatException e) {
            return null;
        }
        return null;
    }
}
