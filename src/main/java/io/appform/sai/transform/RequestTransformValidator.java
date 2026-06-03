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
package io.appform.sai.transform;

import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Validates {@link RequestTransform} configurations at load time.
 */
@Slf4j
@UtilityClass
public class RequestTransformValidator {

    private static final Set<String> KNOWN_JOLT_OPERATIONS = Set.of(
                                                                    "default",
                                                                    "add",
                                                                    "remove",
                                                                    "modify",
                                                                    "shift",
                                                                    "cardinality",
                                                                    "sort");

    private static final int MAX_TRANSFORMS = 100;

    /**
     * Validates a list of request transforms.
     *
     * @param transforms the transforms to validate
     * @throws IllegalArgumentException if validation fails
     */
    public static void validate(List<RequestTransform> transforms) {
        if (transforms == null) {
            return;
        }

        if (transforms.size() > MAX_TRANSFORMS) {
            throw new IllegalArgumentException(
                                               "Too many request transforms: " + transforms.size()
                                                       + ". Maximum allowed: " + MAX_TRANSFORMS);
        }

        for (int i = 0; i < transforms.size(); i++) {
            var transform = transforms.get(i);
            validateTransform(transform, i);
        }

        log.debug("Validated {} request transforms", transforms.size());
    }

    private static void validateTransform(RequestTransform transform, int index) {
        if (transform == null) {
            throw new IllegalArgumentException("Request transform at index " + index + " is null");
        }

        var operation = transform.getOperation();
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException(
                                               "Request transform at index " + index
                                                       + " has missing or blank operation");
        }

        if (!KNOWN_JOLT_OPERATIONS.contains(operation)) {
            throw new IllegalArgumentException(
                                               "Request transform at index " + index + " has unknown operation: '"
                                                       + operation
                                                       + "'. Known operations: " + KNOWN_JOLT_OPERATIONS);
        }

        var spec = transform.getSpec();
        if (spec == null) {
            throw new IllegalArgumentException(
                                               "Request transform at index " + index + " has missing spec");
        }

        if (spec.isEmpty()) {
            throw new IllegalArgumentException(
                                               "Request transform at index " + index + " has empty spec");
        }

        if ("remove".equals(operation)) {
            for (Map.Entry<String, Object> entry : spec.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    throw new IllegalArgumentException(
                                                       "Request transform at index " + index
                                                               + " (operation 'remove') has empty key in spec");
                }
            }
        }
    }
}
