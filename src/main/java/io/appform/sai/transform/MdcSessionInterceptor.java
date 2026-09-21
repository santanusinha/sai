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

import org.slf4j.MDC;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * An OkHttp interceptor that stamps the current session id onto the thread
 * MDC so that log lines written by downstream interceptors (and by OkHttp
 * network threads) route to the per-session log file instead of
 * {@code session-default.log}.
 *
 * <p>OkHttp runs interceptors on its own dispatcher threads, which never
 * inherit the session id set on the main thread via
 * {@code MDC.put("sessionId", ...)} — MDC is thread-local. This interceptor
 * closes that gap by setting and clearing the MDC value around every call.
 */
@Slf4j
public class MdcSessionInterceptor implements Interceptor {

    /**
     * The MDC key used by {@code logback.xml} to route log lines to
     * per-session files. Keep in sync with the SiftingAppender
     * discriminator key.
     */
    public static final String MDC_SESSION_ID_KEY = "sessionId";

    private final String sessionId;

    public MdcSessionInterceptor(String sessionId) {
        this.sessionId = sessionId;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        final var previous = MDC.get(MDC_SESSION_ID_KEY);
        MDC.put(MDC_SESSION_ID_KEY, sessionId);
        try {
            return chain.proceed(chain.request());
        }
        finally {
            if (previous != null) {
                MDC.put(MDC_SESSION_ID_KEY, previous);
            }
            else {
                MDC.remove(MDC_SESSION_ID_KEY);
            }
        }
    }
}
