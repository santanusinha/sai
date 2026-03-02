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
package io.appform.sai.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import io.appform.sai.AgentConfig;

import java.nio.file.Files;
import java.nio.file.Path;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class AgentConfigLoader {

    public static AgentConfig load(Path path, ObjectMapper jsonMapper) {
        if (path == null) {
            throw new IllegalArgumentException("Persona path is required");
        }
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Persona file does not exist: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Persona path is not a regular file: " + path);
        }
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException("Persona file is not readable: " + path);
        }
        final var fileName = path.getFileName().toString().toLowerCase();
        try {
            if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
                final var yamlMapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
                // Align key deserialization features with the JSON mapper for consistency
                yamlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                                     jsonMapper.getDeserializationConfig().isEnabled(
                                                                                     DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
                return yamlMapper.readValue(Files.readAllBytes(path), AgentConfig.class);
            }
            else if (fileName.endsWith(".json")) {
                return jsonMapper.readValue(Files.readAllBytes(path), AgentConfig.class);
            }
            else {
                throw new IllegalArgumentException("Unsupported persona file type. Use .yaml, .yml, or .json: " + path);
            }
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Failed to load persona from: " + path + "; " + e.getMessage(), e);
        }
    }
}
