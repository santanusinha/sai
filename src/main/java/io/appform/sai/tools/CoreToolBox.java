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

import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.core.utils.AgentUtils;

import io.appform.sai.Printer;
import io.appform.sai.models.Actor;
import io.appform.sai.models.Severity;

import java.time.Duration;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class CoreToolBox implements ToolBox {

    private final Printer printer;

    @Tool("Run bash commands on the system where the agent is running. This is the core tool and should be used for any command execution needs. Use this tool to run any bash command, including those that interact with the file system, network, or other system resources. Be cautious while using this tool, as it can execute any command on the system. Do not operate on files mentioned in .gitignore")
    public ToolIO.BashResponse bash(ToolIO.BashRequest request) {
        // We run the command in the same thread and print the update to the printer. We can do streaming later if needed.
        // once completed we return the status code and the output as response.
        if (null != printer) {
            printer.print(Printer.Update.builder()
                    .actor(Actor.SYSTEM)
                    .severity(Severity.INFO)
                    .data("Executing bash command: " + request.getCommand())
                    .build());
        }
        log.info("Executing bash command: {}", request.getCommand());
        try {
            final var commandOutput = new BashCommandRunner(request.getCommand(),
                                                            Duration.ofSeconds(request.getTimeoutSeconds()))
                    .call();

            final var statusCode = commandOutput.getStatusCode();
            log.info("Bash command execution completed with status code: {}", statusCode);
            return new ToolIO.BashResponse(statusCode, commandOutput.getStdout(), commandOutput.getStderr());
        }
        catch (Exception e) {
            final var errorMessage = "Error executing bash command: " + AgentUtils.rootCause(e).getMessage();
            log.error(errorMessage, e);
            return new ToolIO.BashResponse(-1, "", errorMessage);
        }
    }

    @Override
    public String name() {
        return "core";
    }

}
