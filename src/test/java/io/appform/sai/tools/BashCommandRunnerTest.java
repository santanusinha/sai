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
package io.appform.sai.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.time.Duration;

@DisabledOnOs(OS.WINDOWS)
class BashCommandRunnerTest {

    private static BashCommandRunner.CommandOutput run(String command, Duration timeout) {
        return new BashCommandRunner(command, timeout, line -> line).call();
    }

    @Test
    void backgroundProcessHoldingPipeDoesNotLoseOutput() {
        final var result = assertTimeoutPreemptively(Duration.ofSeconds(8),
                                                     () -> run("sleep 6 & echo started",
                                                               Duration.ofSeconds(20)));
        assertEquals(0, result.getStatusCode());
        assertEquals("started", result.getStdout());
    }

    @Test
    void commandReadingStdinGetsEofInsteadOfHanging() {
        final var result = assertTimeoutPreemptively(Duration.ofSeconds(5),
                                                     () -> run("cat; echo after", Duration.ofSeconds(30)));
        assertEquals(0, result.getStatusCode());
        assertEquals("after", result.getStdout());
    }

    @Test
    void failingCommandReturnsOutputAsStderr() {
        final var result = run("echo oops; exit 3", Duration.ofSeconds(5));
        assertEquals(3, result.getStatusCode());
        assertEquals("oops", result.getStderr());
    }

    @Test
    void noTimeoutValueStillCompletes() {
        final var result = assertTimeoutPreemptively(Duration.ofSeconds(5),
                                                     () -> run("echo hi", Duration.ofMillis(Long.MAX_VALUE)));
        assertEquals(0, result.getStatusCode());
        assertEquals("hi", result.getStdout());
    }

    @Test
    void timeoutKillsChildProcessesAndKeepsPartialOutput() {
        final var result = assertTimeoutPreemptively(Duration.ofSeconds(8),
                                                     () -> run("echo start; sleep 30; echo end",
                                                               Duration.ofSeconds(1)));
        assertEquals(-1, result.getStatusCode());
        assertEquals("start", result.getStdout());
        assertTrue(result.getStderr().contains("timed out"));
    }
}
