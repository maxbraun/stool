/**
 * Copyright 1&1 Internet AG, https://github.com/1and1/
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
package net.oneandone.stool;

import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Command;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.cli.Remaining;

public class Help implements Command {
    private String command = null;

    public Help() {
    }

    @Remaining
    public void remaining(String arg) {
        if (command != null) {
            throw new ArgumentException("too many arguments");
        }
        command = arg;
    }

    @Override
    public void invoke() throws Exception {
        ProcessBuilder builder;
        Process process;

        builder = new ProcessBuilder();
        builder.directory(null /* use current directory */);
        builder.command("man", command == null ? "stool" : "stool-" + command);
        builder.inheritIO();
        process = builder.start();
        process.waitFor();
    }
}
