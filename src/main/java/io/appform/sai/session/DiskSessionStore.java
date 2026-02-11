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
package io.appform.sai.session;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.session.BiScrollable;
import com.phonepe.sentinelai.session.QueryDirection;
import com.phonepe.sentinelai.session.SessionStore;
import com.phonepe.sentinelai.session.SessionSummary;

import io.appform.sai.session.internal.FileOps;
import io.appform.sai.session.internal.MessageMeta;
import io.appform.sai.session.internal.PointerCodec;
import io.appform.sai.session.internal.SessionCache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Disk-backed implementation of SessionStore based on DISK_SESSION.md design.
 */
public class DiskSessionStore implements SessionStore {
    private static final Logger log = LoggerFactory.getLogger(DiskSessionStore.class);

    private final Path dataDir;
    private final int maxCacheMessagesPerSession;
    private final boolean fsyncOnAppend;
    private final boolean indexOnStartup;
    private final int pageScanBatchSize;

    private final Map<String, SessionCache> sessionCaches = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = JsonUtils.createMapper();

    public DiskSessionStore(Path dataDir) {
        this(dataDir, 10_000, false, false, 128);
    }

    public DiskSessionStore(
            Path dataDir,
            int maxCacheMessagesPerSession,
            boolean fsyncOnAppend,
            boolean indexOnStartup,
            int pageScanBatchSize
    ) {
        this.dataDir = dataDir;
        this.maxCacheMessagesPerSession = maxCacheMessagesPerSession;
        this.fsyncOnAppend = fsyncOnAppend;
        this.indexOnStartup = indexOnStartup;
        this.pageScanBatchSize = pageScanBatchSize;
        validateDataDir();
        if (indexOnStartup) {
            // Preload indices for existing sessions
            try {
                if (Files.exists(dataDir) && Files.isDirectory(dataDir)) {
                    try (DirectoryStream<Path> ds = Files.newDirectoryStream(dataDir)) {
                        for (Path p : ds) {
                            if (Files.isDirectory(p)) {
                                String sessionId = p.getFileName().toString();
                                loadCache(sessionId);
                            }
                        }
                    }
                }
            }
            catch (IOException e) {
                log.warn("Failed to preload session indices: {}", e.getMessage());
            }
        }
    }

    private static long optLong(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && f.isNumber()) ? f.asLong() : 0L;
    }

    private static String optText(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && !f.isNull()) ? f.asText() : null;
    }

    @Override
    public boolean deleteSession(String sessionId) {
        SessionCache cache = sessionCaches.get(sessionId);
        if (cache != null) {
            cache.lock.writeLock().lock();
        }
        try {
            Path dir = sessionDir(sessionId);
            if (!Files.exists(dir)) return true;
            // Delete directory recursively
            try {
                Files.walk(dir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            }
                            catch (IOException e) {
                                log.warn("Failed to delete {}: {}", p, e.getMessage());
                            }
                        });
            }
            catch (IOException e) {
                log.error("Error deleting session {}: {}", sessionId, e.getMessage());
                return false;
            }
            sessionCaches.remove(sessionId);
            return true;
        }
        finally {
            if (cache != null) cache.lock.writeLock().unlock();
        }
    }

    @Override
    public BiScrollable<AgentMessage> readMessages(
            String sessionId,
            int count,
            boolean skipSystemPrompt,
            BiScrollable.DataPointer pointer,
            QueryDirection queryDirection
    ) {
        SessionCache cache = loadCache(sessionId);
        ReentrantReadWriteLock.ReadLock rl = cache.lock.readLock();
        rl.lock();
        try {
            List<MessageMeta> metas = cache.metaIndex;
            if (metas.isEmpty()) {
                return new BiScrollable<>(Collections.emptyList(), new BiScrollable.DataPointer(null, null));
            }
            // Determine anchor index
            int anchorIndex;
            if (pointer == null || ((queryDirection == QueryDirection.NEWER) && pointer.getNewer() == null)
                    || ((queryDirection == QueryDirection.OLDER) && pointer.getOlder() == null)) {
                anchorIndex = (queryDirection == QueryDirection.NEWER) ? -1 : metas.size();
            }
            else {
                String token = (queryDirection == QueryDirection.NEWER) ? pointer.getNewer() : pointer.getOlder();
                PointerCodec.Anchor anchor = PointerCodec.decodeMessagePointer(token);
                anchorIndex = findAnchorIndex(metas, anchor);
            }

            List<MessageMeta> pageMetas = new ArrayList<>();
            if (queryDirection == QueryDirection.NEWER) {
                // strictly newer than anchor
                int i = anchorIndex + 1;
                while (i < metas.size() && pageMetas.size() < count) {
                    MessageMeta m = metas.get(i);
                    if (!(skipSystemPrompt && m.isSystemPrompt)) {
                        pageMetas.add(m);
                    }
                    i++;
                }
            }
            else { // OLDER
                int i = anchorIndex - 1;
                List<MessageMeta> temp = new ArrayList<>();
                while (i >= 0 && temp.size() < count) {
                    MessageMeta m = metas.get(i);
                    if (!(skipSystemPrompt && m.isSystemPrompt)) {
                        temp.add(m);
                    }
                    i--;
                }
                // Return oldest to newest
                Collections.reverse(temp);
                pageMetas.addAll(temp);
            }

            // Hydrate messages from cache or disk
            List<AgentMessage> items = pageMetas.stream()
                    .map(m -> hydrateMessage(sessionId, cache, m))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // Construct pointers
            String older = null;
            String newer = null;
            if (!items.isEmpty()) {
                MessageMeta first = pageMetas.get(0);
                MessageMeta last = pageMetas.get(pageMetas.size() - 1);
                // If there are older beyond first
                int firstIndex = metas.indexOf(first);
                if (firstIndex > 0) {
                    older = PointerCodec.encodeMessagePointer(first.ts, first.id);
                }
                // If there are newer beyond last
                int lastIndex = metas.indexOf(last);
                if (lastIndex < metas.size() - 1) {
                    newer = PointerCodec.encodeMessagePointer(last.ts, last.id);
                }
            }

            return new BiScrollable<>(items, new BiScrollable.DataPointer(older, newer));
        }
        finally {
            rl.unlock();
        }
    }

    @Override
    public void saveMessages(String sessionId, String runId, List<AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) return;
        SessionCache cache = loadCache(sessionId);
        ReentrantReadWriteLock.WriteLock wl = cache.lock.writeLock();
        wl.lock();
        try {
            Path dir = sessionDir(sessionId);
            Files.createDirectories(dir);
            Path mf = messagesFile(sessionId);
            try (FileChannel channel = FileChannel.open(mf,
                                                        StandardOpenOption.CREATE,
                                                        StandardOpenOption.WRITE,
                                                        StandardOpenOption.APPEND)) {
                for (AgentMessage msg : messages) {
                    long offset = channel.position();
                    Map<String, Object> envelope = new LinkedHashMap<>();
                    envelope.put("id", msg.getMessageId());
                    envelope.put("ts", msg.getTimestamp());
                    envelope.put("runId", runId);
                    envelope.put("type", msg.getMessageType().name());
                    envelope.put("message", msg);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (OutputStreamWriter osw = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
                         JsonGenerator jg = mapper.getFactory().createGenerator(osw)) {
                        mapper.writeValue(jg, envelope);
                    }
                    byte[] bytes = baos.toByteArray();
                    // Append newline
                    byte[] newline = "\n".getBytes(StandardCharsets.UTF_8);
                    channel.write(java.nio.ByteBuffer.wrap(bytes));
                    channel.write(java.nio.ByteBuffer.wrap(newline));
                    if (fsyncOnAppend) channel.force(true);
                    boolean isSystem = msg.getMessageType() == AgentMessageType.SYSTEM_PROMPT_REQUEST_MESSAGE;
                    cache.metaIndex.add(new MessageMeta(msg.getMessageId(), msg.getTimestamp(), offset, isSystem));
                    cache.sortMeta();
                    cache.messageCachePut(msg.getMessageId(), msg);
                }
            }
        }
        catch (IOException e) {
            log.error("Failed to save messages for session {}: {}", sessionId, e.getMessage());
        }
        finally {
            wl.unlock();
        }
    }

    @Override
    public Optional<SessionSummary> saveSession(SessionSummary sessionSummary) {
        if (sessionSummary == null || sessionSummary.getSessionId() == null) {
            return Optional.empty();
        }
        String sessionId = sessionSummary.getSessionId();
        Path dir = sessionDir(sessionId);
        try {
            Files.createDirectories(dir);
            Path sf = summaryFile(sessionId);
            boolean ok = FileOps.atomicWriteJson(sf, sessionSummary, mapper);
            if (!ok) return Optional.empty();
            return Optional.of(sessionSummary);
        }
        catch (IOException e) {
            log.error("Failed to save session summary for {}: {}", sessionId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<SessionSummary> session(String sessionId) {
        Path sf = summaryFile(sessionId);
        if (!Files.exists(sf)) return Optional.empty();
        try {
            try (InputStream is = Files.newInputStream(sf)) {
                SessionSummary s = mapper.readValue(is, SessionSummary.class);
                return Optional.ofNullable(s);
            }
        }
        catch (IOException e) {
            log.error("Failed to read session summary for {}: {}", sessionId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public BiScrollable<SessionSummary> sessions(int count, String pointer, QueryDirection queryDirection) {
        List<SessionSummary> all = new ArrayList<>();
        try {
            if (Files.exists(dataDir)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(dataDir)) {
                    for (Path p : ds) {
                        if (!Files.isDirectory(p)) continue;
                        String sessionId = p.getFileName().toString();
                        Optional<SessionSummary> s = session(sessionId);
                        s.ifPresent(all::add);
                    }
                }
            }
        }
        catch (IOException e) {
            log.error("Error listing sessions: {}", e.getMessage());
        }
        // sort by updatedAt asc, then sessionId asc
        all.sort(Comparator.comparing(SessionSummary::getUpdatedAt)
                .thenComparing(SessionSummary::getSessionId));

        if (all.isEmpty()) {
            return new BiScrollable<>(Collections.emptyList(), new BiScrollable.DataPointer(null, null));
        }

        PointerCodec.SessionAnchor anchor = PointerCodec.decodeSessionPointer(pointer);
        int anchorIndex;
        if (pointer == null || pointer.isEmpty() || anchor == null) {
            anchorIndex = (queryDirection == QueryDirection.NEWER) ? -1 : all.size();
        }
        else {
            anchorIndex = findSessionAnchorIndex(all, anchor);
        }

        List<SessionSummary> page = new ArrayList<>();
        if (queryDirection == QueryDirection.NEWER) {
            int i = anchorIndex + 1;
            while (i < all.size() && page.size() < count) {
                page.add(all.get(i));
                i++;
            }
        }
        else {
            int i = anchorIndex - 1;
            List<SessionSummary> tmp = new ArrayList<>();
            while (i >= 0 && tmp.size() < count) {
                tmp.add(all.get(i));
                i--;
            }
            Collections.reverse(tmp);
            page.addAll(tmp);
        }

        String older = null, newer = null;
        if (!page.isEmpty()) {
            int firstIndex = all.indexOf(page.get(0));
            int lastIndex = all.indexOf(page.get(page.size() - 1));
            if (firstIndex > 0) {
                SessionSummary s = all.get(firstIndex);
                older = PointerCodec.encodeSessionPointer(s.getUpdatedAt(), s.getSessionId());
            }
            if (lastIndex < all.size() - 1) {
                SessionSummary s = all.get(lastIndex);
                newer = PointerCodec.encodeSessionPointer(s.getUpdatedAt(), s.getSessionId());
            }
        }

        return new BiScrollable<>(page, new BiScrollable.DataPointer(older, newer));
    }

    private void buildMetaIndex(SessionCache cache, Path mf) {
        ReentrantReadWriteLock.WriteLock wl = cache.lock.writeLock();
        wl.lock();
        try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(mf));
             InputStreamReader isr = new InputStreamReader(bis, StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(isr)) {
            long offset = 0L;
            String line;
            while ((line = br.readLine()) != null) {
                int bytes = line.getBytes(StandardCharsets.UTF_8).length + 1; // +1 for newline
                try {
                    JsonNode node = mapper.readTree(line);
                    String id = optText(node, "id");
                    long ts = optLong(node, "ts");
                    String typeStr = optText(node, "type");
                    boolean isSystem = false;
                    if (typeStr != null) {
                        try {
                            AgentMessageType t = AgentMessageType.valueOf(typeStr);
                            isSystem = (t == AgentMessageType.SYSTEM_PROMPT_REQUEST_MESSAGE);
                        }
                        catch (IllegalArgumentException ignore) {
                        }
                    }
                    cache.metaIndex.add(new MessageMeta(id, ts, offset, isSystem));
                }
                catch (Exception e) {
                    log.warn("Failed to parse envelope during index build at offset {}: {}", offset, e.getMessage());
                }
                offset += bytes;
            }
            cache.sortMeta();
        }
        catch (IOException e) {
            log.error("Error building meta index for {}: {}", mf, e.getMessage());
        }
        finally {
            wl.unlock();
        }
    }

    private int findAnchorIndex(List<MessageMeta> metas, PointerCodec.Anchor anchor) {
        if (anchor == null) return -1;
        // Find the first meta that matches ts and id; if not found, binary search by ts then scan by id
        int lo = 0, hi = metas.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            MessageMeta m = metas.get(mid);
            if (m.ts < anchor.ts) lo = mid + 1;
            else if (m.ts > anchor.ts) hi = mid - 1;
            else {
                // timestamps equal; locate by id by scanning nearby
                int idx = mid;
                // scan backward
                while (idx >= 0 && metas.get(idx).ts == anchor.ts) {
                    if (Objects.equals(metas.get(idx).id, anchor.id)) return idx;
                    idx--;
                }
                // scan forward
                idx = mid + 1;
                while (idx < metas.size() && metas.get(idx).ts == anchor.ts) {
                    if (Objects.equals(metas.get(idx).id, anchor.id)) return idx;
                    idx++;
                }
                // not found; anchor precedes the first item with ts > anchor.ts
                return lo - 1;
            }
        }
        return lo - 1; // position of item strictly older than anchor
    }

    private int findSessionAnchorIndex(List<SessionSummary> list, PointerCodec.SessionAnchor anchor) {
        int lo = 0, hi = list.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            SessionSummary s = list.get(mid);
            long sts = s.getUpdatedAt();
            long ats = anchor.updatedAt;
            if (sts < ats) lo = mid + 1;
            else if (sts > ats) hi = mid - 1;
            else {
                // equal timestamp; scan for sessionId
                int idx = mid;
                while (idx >= 0 && list.get(idx).getUpdatedAt() == sts) {
                    if (Objects.equals(list.get(idx).getSessionId(), anchor.sessionId)) return idx;
                    idx--;
                }
                idx = mid + 1;
                while (idx < list.size() && list.get(idx).getUpdatedAt() == sts) {
                    if (Objects.equals(list.get(idx).getSessionId(), anchor.sessionId)) return idx;
                    idx++;
                }
                return lo - 1;
            }
        }
        return lo - 1;
    }

    private AgentMessage hydrateMessage(String sessionId, SessionCache cache, MessageMeta meta) {
        AgentMessage cached = cache.messageCacheGet(meta.id);
        if (cached != null) return cached;
        Path mf = messagesFile(sessionId);
        try {
            String line = FileOps.readLineAtOffset(mf, meta.offset);
            if (line == null) return null;
            JsonNode node = mapper.readTree(line);
            JsonNode messageNode = node.get("message");
            if (messageNode == null || messageNode.isNull()) return null;
            AgentMessage msg = mapper.convertValue(messageNode, new TypeReference<AgentMessage>() {
            });
            cache.messageCachePut(meta.id, msg);
            return msg;
        }
        catch (IOException e) {
            log.error("Failed to hydrate message at {}: {}", meta.offset, e.getMessage());
            return null;
        }
    }

    private SessionCache loadCache(String sessionId) {
        return sessionCaches.computeIfAbsent(sessionId, sid -> {
            SessionCache cache = new SessionCache(maxCacheMessagesPerSession);
            Path dir = sessionDir(sid);
            try {
                Files.createDirectories(dir);
            }
            catch (IOException e) {
                log.error("Failed to create session directory {}: {}", dir, e.getMessage());
            }
            Path mf = messagesFile(sid);
            if (Files.exists(mf)) {
                buildMetaIndex(cache, mf);
            }
            return cache;
        });
    }

    private Path messagesFile(String sessionId) {
        return sessionDir(sessionId).resolve("messages.jsonl");
    }

    private Path sessionDir(String sessionId) {
        return dataDir.resolve(sessionId);
    }

    private Path summaryFile(String sessionId) {
        return sessionDir(sessionId).resolve("summary.json");
    }

    private void validateDataDir() {
        try {
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
            }
            if (!Files.isDirectory(dataDir)) {
                throw new IllegalArgumentException("dataDir is not a directory: " + dataDir);
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to prepare dataDir: " + dataDir, e);
        }
    }
}
