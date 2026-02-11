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
package io.appform.sai.session.internal;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SessionCache {
    public final List<MessageMeta> metaIndex = new ArrayList<>();
    public final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final int maxCacheMessages;

    private final LinkedHashMap<String, AgentMessage> messageCache;

    public SessionCache(int maxCacheMessages) {
        this.maxCacheMessages = maxCacheMessages;
        this.messageCache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, AgentMessage> eldest) {
                return size() > SessionCache.this.maxCacheMessages;
            }
        };
    }

    public AgentMessage messageCacheGet(String id) {
        return messageCache.get(id);
    }

    public void messageCachePut(String id, AgentMessage msg) {
        messageCache.put(id, msg);
    }

    public void sortMeta() {
        metaIndex.sort(Comparator.comparingLong((MessageMeta m) -> m.ts).thenComparing(m -> m.id));
    }
}
