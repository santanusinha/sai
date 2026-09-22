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
package io.appform.sai;

import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.filesystem.session.FileSystemSessionStore;
import com.phonepe.sentinelai.session.SessionSummary;

import io.appform.sai.config.AgentConfigLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves sessions for resume and restores saved model/persona choices.
 *
 * <p>Session lookup reads only each session's {@code summary.json} file. Message
 * data is never loaded during lookup. This keeps {@code sai -s} startup fast even
 * with many stored sessions.
 */
@Slf4j
public final class SessionResolver {

    /**
     * Model and persona values restored from a saved session. A field is {@code null}
     * when the CLI flag was set or nothing was saved.
     */
    public static final class RestoredSessionData {
        private String model;
        private String persona;

        public String getModel() {
            return Objects.requireNonNullElse(model, "");
        }

        public String getPersona() {
            return Objects.requireNonNullElse(persona, "");
        }

        public boolean hasModel() {
            return !Strings.isNullOrEmpty(model);
        }

        public boolean hasPersona() {
            return !Strings.isNullOrEmpty(persona);
        }
    }

    private SessionResolver() {
        // Utility class
    }

    /**
     * Restores model and persona saved in the session's extra data. CLI flags always win:
     * a value is restored only when the matching flag was not supplied on the command line.
     *
     * @param sessionId       the session to read from
     * @param sessionDataPath the sessions data directory
     * @param settings        current settings (used to check persona path resolvability)
     * @param cliModel        value of the {@code --model} flag (may be {@code null})
     * @param cliPersona      value of the {@code --persona} flag (may be {@code null})
     * @return restored values; fields stay {@code null} when nothing was restored
     */
    public static RestoredSessionData populateDataFromSession(@NonNull final String sessionId,
                                                              @NonNull final Path sessionDataPath,
                                                              @NonNull final Settings settings,
                                                              final String cliModel,
                                                              final String cliPersona) {
        final var restored = new RestoredSessionData();
        final var probeStore = probeStore(sessionDataPath);
        final var existingSession = probeStore.session(sessionId)
                .orElse(null);
        if (existingSession == null) {
            log.warn("No existing session found for session ID: {}", sessionId);
            return restored;
        }
        final var savedExtra = existingSession.getExtra();
        if (savedExtra == null) {
            return restored; // older session with no extra data — backwards compat
        }
        // Restore model only when --model was not supplied on the CLI
        if (Strings.isNullOrEmpty(cliModel)) {
            final var savedModel = (String) savedExtra.get("model");
            if (!Strings.isNullOrEmpty(savedModel)) {
                restored.model = savedModel;
            }
        }
        // Restore persona only when --persona was not supplied on the CLI,
        // and only if the persona file is still resolvable/readable.
        if (Strings.isNullOrEmpty(cliPersona)) {
            final var savedPersona = (String) savedExtra.get("persona");
            if (!Strings.isNullOrEmpty(savedPersona)) {
                try {
                    AgentConfigLoader.resolvePersonaPath(savedPersona, settings.getConfigDir());
                    restored.persona = savedPersona; // file still exists → restore
                }
                catch (Exception e) {
                    log.warn("Saved persona '{}' is no longer accessible, using default: {}",
                             savedPersona,
                             e.getMessage());
                    // persona stays null → resolveAgentConfig falls back to built-in default
                }
            }
        }
        return restored;
    }

    /**
     * Builds a read-only session store probe. Used for session lookups that must not
     * write extra data.
     *
     * @param sessionDataPath the sessions data directory
     * @return a probe store with a small cache
     */
    public static FileSystemSessionStore probeStore(@NonNull final Path sessionDataPath) {
        return FileSystemSessionStore.builder()
                .baseDir(sessionDataPath.toString())
                .mapper(JsonUtils.createMapper())
                .cacheSize(1)
                .build(); // no extraDataOperator — read-only probe
    }

    /**
     * Resolves the most recently updated session for the given working directory.
     *
     * <p>Reads only each session's {@code summary.json} directly instead of paging through the
     * session store, so no message data is touched. Sessions whose summary is missing or unreadable
     * are skipped.
     *
     * @param sessionDataPath the path to the sessions data directory
     * @param workDir         the current working directory to filter sessions by
     * @return the session ID of the most recent session in this directory, or {@code null} if none exists
     */
    public static String resolveLastSessionId(@NonNull final Path sessionDataPath,
                                              @NonNull final String workDir) {
        if (!Files.isDirectory(sessionDataPath)) {
            return null;
        }
        String bestSessionId = null;
        long bestUpdatedAt = Long.MIN_VALUE;
        try (final var sessionDirs = Files.list(sessionDataPath)) {
            for (final var dir : sessionDirs.filter(Files::isDirectory).toList()) {
                final var summaryFile = dir.resolve("summary.json");
                if (!Files.isRegularFile(summaryFile)) {
                    continue;
                }
                try {
                    final var summary = JsonUtils.createMapper()
                            .readValue(summaryFile.toFile(), SessionSummary.class);
                    final var extra = summary.getExtra();
                    final var workDirValue = extra == null ? null : extra.get("workDir");
                    if (workDirValue == null || !workDir.equals(workDirValue.toString())) {
                        continue;
                    }
                    if (summary.getUpdatedAt() > bestUpdatedAt) {
                        bestUpdatedAt = summary.getUpdatedAt();
                        bestSessionId = summary.getSessionId();
                    }
                }
                catch (Exception e) {
                    log.warn("Skipping unreadable session summary: {}", summaryFile, e);
                }
            }
        }
        catch (Exception e) {
            log.warn("Failed to list sessions under {}", sessionDataPath, e);
        }
        return bestSessionId;
    }
}
