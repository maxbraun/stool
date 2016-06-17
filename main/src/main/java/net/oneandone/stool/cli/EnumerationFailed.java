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
package net.oneandone.stool.cli;

import net.oneandone.sushi.fs.file.FileNode;

import java.util.HashMap;
import java.util.Map;

public class EnumerationFailed extends Exception {
    public final Map<FileNode, Exception> problems;

    public EnumerationFailed() {
        problems = new HashMap<>();
    }

    public boolean empty() {
        return problems.isEmpty();
    }

    public void add(FileNode backstage, Exception cause) {
        problems.put(backstage, cause);
        addSuppressed(cause);
    }

    /** @return null when there's no message */
    @Override
    public String getMessage() {
        StringBuilder result;

        if (problems.isEmpty()) {
            return null;
        }

        result = new StringBuilder("stage command failed for the following stage(s):\n");
        for (Map.Entry<FileNode, Exception> entry : problems.entrySet()) {
            result.append("  ").append(entry.getKey().getName()).append(": ").append(entry.getValue().getMessage());
        }
        return result.toString();
    }
}