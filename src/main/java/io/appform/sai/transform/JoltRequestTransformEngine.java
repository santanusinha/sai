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

import com.bazaarvoice.jolt.Chainr;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * Applies a list of {@link RequestTransform} operations to a JSON payload
 * using the Jolt {@link Chainr} engine.
 *
 * <p>Jolt is already on the classpath via sentinel-ai-toolbox-remote-http.
 */
@Slf4j
public class JoltRequestTransformEngine {

    private final ObjectMapper mapper;

    public JoltRequestTransformEngine(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Applies the given transforms to the payload in order.
     *
     * @param payload    the original request payload
     * @param transforms the list of transforms to apply
     * @return the modified payload
     */
    public ObjectNode apply(ObjectNode payload, List<RequestTransform> transforms) {
        if (transforms == null || transforms.isEmpty()) {
            return payload;
        }

        Object input = mapper.convertValue(payload, Object.class);

        for (RequestTransform transform : transforms) {
            List<Map<String, Object>> chainrSpec = List.of(
                                                           Map.of("operation",
                                                                  transform.getOperation(),
                                                                  "spec",
                                                                  transform.getSpec()));
            Chainr chainr = Chainr.fromSpec(chainrSpec);
            input = chainr.transform(input);
            log.debug("Applied transform operation '{}' with spec: {}",
                      transform.getOperation(),
                      transform.getSpec());
        }

        return mapper.convertValue(input, ObjectNode.class);
    }
}
