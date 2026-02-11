/*
 * Copyright 2026 authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.appform.sai;

import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Utils {

    public static float elapsedTimeInSeconds(final Stopwatch stopwatch) {
        return toMillis(stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    public static float toMillis(long seconds) {
        return seconds / 1000.0f;
    }
    
}

