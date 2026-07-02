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
package io.appform.sai.cli.slash.commands;

import io.appform.sai.Printer;
import io.appform.sai.cli.slash.SlashRootCommand;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * {@code /compact} — manually trigger session compaction.
 *
 * <p>Delegates to {@link com.phonepe.sentinelai.session.AgentSessionExtension#forceCompaction(String)}
 * to summarise the conversation history for the current session. When compaction succeeds the
 * sentinel session extension fires its {@code onSessionSummarized} callback (wired in
 * {@code SaiCommand}) which pretty-prints the summary via {@link io.appform.sai.CompactionSummaryFormatter}.
 * This command only needs to initiate the operation and report errors.
 */
@Command(name = "compact", description = "Manually compact the session history")
public class CompactCommand implements Runnable {

    @ParentCommand
    private SlashRootCommand parent;

    @Override
    public void run() {
        final var context = parent.getContext();
        final var printer = context.getPrinter();
        final var sessionExtension = context.getSessionExtension();

        if (sessionExtension == null) {
            printer.print(Printer.systemMessage(
                                                Printer.Colours.YELLOW
                                                        + "No session extension is configured for this session."
                                                        + Printer.Colours.RESET));
            return;
        }

        final var sessionId = context.getSettings().getSessionId();
        printer.print(Printer.raw(
                                  Printer.Colours.CYAN + "\u23F3 " + Printer.Colours.GRAY
                                          + "Compacting session " + Printer.Colours.WHITE + sessionId
                                          + Printer.Colours.GRAY + "\u2026" + Printer.Colours.RESET));
        try {
            final var result = sessionExtension.forceCompaction(sessionId).join();
            if (result.isEmpty()) {
                printer.print(Printer.systemMessage(
                                                    Printer.Colours.YELLOW
                                                            + "Compaction completed but no summary was produced (the session may be empty)."
                                                            + Printer.Colours.RESET));
            }
            // Success case: the onSessionSummarized signal fires and pretty-prints the summary.
        }
        catch (Exception e) {
            printer.print(Printer.systemMessage(
                                                Printer.Colours.RED + "\u274C Compaction failed: "
                                                        + Printer.Colours.WHITE + e.getMessage()
                                                        + Printer.Colours.RESET));
        }
    }
}
