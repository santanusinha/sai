/*
 * Copyright 2026 authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.appform.sai.tools;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.UtilityClass;
import lombok.extern.jackson.Jacksonized;

@UtilityClass
public class ToolIO {

    @Value
    @JsonClassDescription("Input for the bash tool.")
    @Builder
    @Jacksonized
    public static class BashRequest {
        @JsonPropertyDescription("Reason for requesting the tool. This is shown to the user for informational purposes.")
        String requestReason;
        @JsonPropertyDescription("The bash command to execute. This should be a single line command. Multi-line commands are not supported.")
        String command;
        @JsonPropertyDescription("The timeout for the bash command execution in seconds. If the command does not complete within this time, it will be terminated. Default is 30 seconds. Adjust this if you expect the command to take longer to execute, but be cautious as setting it too high may lead to hanging processes.")
        int timeoutSeconds;
    }

    @Value
    @Builder
    @Jacksonized
    public static class BashResponse {
        @JsonPropertyDescription("The status code of the bash command execution. A status code of 0 indicates success, while any non-zero value indicates an error.")
        int statusCode;
        @JsonPropertyDescription("The output of the bash command execution. This includes both stdout and stderr.")
        String output;
    }
}
