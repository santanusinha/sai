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
package io.appform.sai.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.apache.commons.text.StringSubstitutor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads {@code settings.yaml} from the configuration directory with {@code ${ENV}} and
 * {@code ${VAR:-default}} interpolation.
 *
 * <p>If the file does not exist, an empty {@link SettingsConfig} is returned — this preserves
 * backward compatibility with env-var-only setups.
 */
@Slf4j
@UtilityClass
public final class SettingsConfigLoader {

    private static final String SETTINGS_FILE_NAME = "settings.yaml";

    /**
     * Loads the settings configuration from the given path.
     *
     * @param settingsPath the path to the settings file
     * @return the loaded {@link SettingsConfig}, or an empty config if the file is absent
     */
    public static SettingsConfig load(Path settingsPath) {
        if (!Files.exists(settingsPath)) {
            log.debug("Settings file not found at {}; using empty config (env-var fallback)", settingsPath);
            return SettingsConfig.builder().build();
        }
        try {
            final var raw = Files.readString(settingsPath, StandardCharsets.UTF_8);
            final var content = substituteEnvVars(raw);
            final var yamlMapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
            yamlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return yamlMapper.readValue(content, SettingsConfig.class);
        }
        catch (Exception e) {
            log.warn("Failed to load settings from {}: {}. Using empty config.", settingsPath, e.getMessage());
            return SettingsConfig.builder().build();
        }
    }

    /**
     * Loads the settings configuration from {@code {configDir}/settings.yaml}.
     *
     * @param configDir the configuration directory (e.g. {@code ~/.config/sai})
     * @return the loaded {@link SettingsConfig}, or an empty config if the file is absent
     */
    public static SettingsConfig load(String configDir) {
        return load(Paths.get(configDir, SETTINGS_FILE_NAME));
    }

    /**
     * Substitutes environment variables in the content, supporting both {@code ${VAR}} and
     * {@code ${VAR:-default}} syntax (matching shell/bash convention).
     *
     * @param content the raw content with potential {@code ${...}} placeholders
     * @return the content with all placeholders resolved
     */
    public static String substituteEnvVars(String content) {
        final var substitutor = new StringSubstitutor(key -> {
            final var colonDash = key.indexOf(":-");
            if (colonDash >= 0) {
                final var varName = key.substring(0, colonDash);
                final var defaultVal = key.substring(colonDash + 2);
                final var val = System.getenv(varName);
                return val != null ? val : defaultVal;
            }
            final var val = System.getenv(key);
            return val != null ? val : null;
        });
        substitutor.setEnableSubstitutionInVariables(false);
        substitutor.setPreserveEscapes(true);
        return substitutor.replace(content);
    }
}
