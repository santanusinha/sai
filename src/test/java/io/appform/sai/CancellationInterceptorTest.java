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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

class CancellationInterceptorTest {

    private static Interceptor.Chain chain(final Request request, final AtomicBoolean proceeded) {
        return (Interceptor.Chain) java.lang.reflect.Proxy.newProxyInstance(
                                                                            CancellationInterceptorTest.class
                                                                                    .getClassLoader(),
                                                                            new Class<?>[]{
                                                                                    Interceptor.Chain.class
                                                                            },
                                                                            (proxy, method, args) -> {
                                                                                if ("request".equals(method
                                                                                        .getName())) {
                                                                                    return request;
                                                                                }
                                                                                if ("proceed".equals(method
                                                                                        .getName())) {
                                                                                    proceeded.set(true);
                                                                                    return new Response.Builder()
                                                                                            .request(request)
                                                                                            .protocol(Protocol.HTTP_1_1)
                                                                                            .code(200)
                                                                                            .message("OK")
                                                                                            .build();
                                                                                }
                                                                                throw new UnsupportedOperationException(
                                                                                                                        method.getName());
                                                                            });
    }

    @Test
    void requestFailsWhenCancelled() {
        final var proceeded = new AtomicBoolean();
        final var request = new Request.Builder().url("http://localhost/").build();
        assertThrows(IOException.class,
                     () -> new CancellationInterceptor(() -> true).intercept(chain(request, proceeded)));
        assertEquals(false, proceeded.get());
    }

    @Test
    void requestPassesThroughWhenNotCancelled() throws IOException {
        final var proceeded = new AtomicBoolean();
        final var request = new Request.Builder().url("http://localhost/").build();
        final var response = new CancellationInterceptor(() -> false).intercept(chain(request, proceeded));
        assertEquals(200, response.code());
        assertEquals(true, proceeded.get());
    }
}
