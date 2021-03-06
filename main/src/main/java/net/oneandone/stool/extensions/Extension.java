/*
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
package net.oneandone.stool.extensions;

import net.oneandone.stool.stage.Stage;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.Map;

public interface Extension {
    /**
     * @return vhost names mapped to a docroot. Docroot may be null which indicates that this vhost must not be added
     * to the tomcat configration (this is useful to only allocate ports)
     */
    Map<String, FileNode> vhosts(Stage stage) throws IOException;

    void beforeStart(Stage stage) throws IOException;

    void beforeStop(Stage stage) throws IOException;
    void contextParameter(Stage stage, String host, int httpPort, FileNode webinf, Map<String, String> result);
    void tomcatOpts(Stage stage, Map<String, String> result);
}
