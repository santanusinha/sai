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
package io.appform.sai;

import java.util.UUID;

import lombok.Builder;
import lombok.Value;
import lombok.With;

@Value
@Builder
@With
public class Settings {
    private static final String DEFAULT_DATA_DIR = System.getProperty("user.home") + "/.local/state/sai";

    @Builder.Default
    String appName = "sai";

    @Builder.Default
    String dataDir = DEFAULT_DATA_DIR;
    boolean debug;
    boolean headless;
    @Builder.Default
    String sessionId = UUID.randomUUID().toString();
}
