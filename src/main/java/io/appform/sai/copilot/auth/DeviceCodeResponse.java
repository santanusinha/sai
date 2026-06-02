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
package io.appform.sai.copilot.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * Response from GitHub's device code endpoint.
 * Used in the OAuth Device Flow to get the device code and user code
 * that the user needs to enter at the verification URI.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceCodeResponse {
    @JsonProperty("device_code")
    private String deviceCode;

    @JsonProperty("user_code")
    private String userCode;

    @JsonProperty("verification_uri")
    private String verificationUri;

    @JsonProperty("expires_in")
    private int expiresIn;

    @JsonProperty("interval")
    private int interval;
}
