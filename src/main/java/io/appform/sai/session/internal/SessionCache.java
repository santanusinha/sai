package io.appform.sai.session.internal;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;

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

    public void sortMeta() {
        metaIndex.sort(Comparator.comparingLong((MessageMeta m) -> m.ts).thenComparing(m -> m.id));
    }

    public AgentMessage messageCacheGet(String id) {
        return messageCache.get(id);
    }

    public void messageCachePut(String id, AgentMessage msg) {
        messageCache.put(id, msg);
    }
}
