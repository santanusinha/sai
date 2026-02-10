package io.appform.sai.session.internal;

public class MessageMeta {
    public final String id;
    public final long ts;
    public final long offset;
    public final boolean isSystemPrompt;

    public MessageMeta(String id, long ts, long offset, boolean isSystemPrompt) {
        this.id = id;
        this.ts = ts;
        this.offset = offset;
        this.isSystemPrompt = isSystemPrompt;
    }
}
