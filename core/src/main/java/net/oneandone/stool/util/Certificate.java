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
package net.oneandone.stool.util;

import net.oneandone.sushi.fs.file.FileNode;

public class Certificate {
    private final FileNode privateKey;
    private final FileNode certificate;

    public Certificate(FileNode privateKey, FileNode certificate) {

        this.privateKey = privateKey;
        this.certificate = certificate;
    }

    public FileNode privateKey() {
        return privateKey;
    }

    public FileNode certificate() {
        return certificate;
    }
}