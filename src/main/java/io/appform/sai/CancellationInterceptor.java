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

import java.io.IOException;
import java.util.function.BooleanSupplier;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * Fails every HTTP request while the current run is cancelled.
 *
 * <p>Ctrl-C aborts calls that are already running. The agent loop can still start the next request a
 * moment later. This interceptor closes that window.
 */
@RequiredArgsConstructor
public class CancellationInterceptor implements Interceptor {

    private final BooleanSupplier cancelled;

    @Override
    public Response intercept(@NonNull final Chain chain) throws IOException {
        if (cancelled.getAsBoolean()) {
            throw new IOException("Request cancelled by user");
        }
        return chain.proceed(chain.request());
    }
}
