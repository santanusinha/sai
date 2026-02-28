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

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class App {
    @SneakyThrows
    public static void main(String[] args) {
        setupLogging();
        final var commandLine = new picocli.CommandLine(new SaiCommand());
        // Disable picocli "@file" expansion so we can implement our own @file semantics for --input
        commandLine.setExpandAtFiles(false);
        final var exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }

    private static void setupLogging() {
        try {
            final var context = (LoggerContext) LoggerFactory
                    .getILoggerFactory();
            final var configurator = new JoranConfigurator();
            configurator.setContext(context);
            context.reset();
            configurator.doConfigure(App.class.getResourceAsStream("/logback.xml"));
        }
        catch (JoranException je) {
            je.printStackTrace();
        }
    }
}
