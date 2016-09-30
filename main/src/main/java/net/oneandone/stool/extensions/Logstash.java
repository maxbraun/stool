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
package net.oneandone.stool.extensions;

import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Files;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.ExitCode;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Logstash implements Extension {
    private final String output;

    // TODO: rename to bin
    private final String link;

    public Logstash() {
        this("output { stdout {} }", "");
    }

    public Logstash(String output, String link) {
        this.output = output;
        this.link = link;
    }

    @Override
    public Map<String, FileNode> vhosts(Stage stage) {
        return new HashMap<>();
    }

    private FileNode conf(Stage stage) {
        return stage.getBackstage().join("logstash.conf");
    }
    private FileNode pid(Stage stage) {
        return stage.getBackstage().join("run/logstash.pid");
    }
    private FileNode log(Stage stage) {
        return stage.getBackstage().join("tomcat/logs/logstash.log");
    }

    @Override
    public void beforeStart(Stage stage) throws IOException {
        String bin;
        FileNode file;
        StringBuilder config;

        bin = link; // TODO: rename
        file = conf(stage);
        config = new StringBuilder("input {\n" +
                "  file {\n" +
                "    type => 'generic'\n" +
                "    tags => ['" + stage.getName() + "']\n" +
                "    start_position => beginning\n" +
                "    ignore_older => 0\n" +
                "    path => ['" + stage.getBackstage().join(Pustefix.APPLOGS).getAbsolute() + "/*/*.log']\n" +
                "  }\n" +
                "}\n\n");
        for (String name : Separator.COMMA.split(output)) {
            config.append(file.getWorld().file(name).readString());
            config.append('\n');
        }
        file.writeString(config.toString());
        Files.stoolFile(file);
        file.getParent().exec(bin, file.getAbsolute(), log(stage).getAbsolute(), pid(stage).getAbsolute());
    }

    @Override
    public void beforeStop(Stage stage) throws IOException {
        FileNode pidfile;
        String pid;

        conf(stage).deleteFile();
        pidfile = pid(stage);
        if (!pidfile.isFile()) {
            stage.session.console.info.println("WARNING: " + pidfile.getAbsolute() + " not found");
        } else {
            pid = pidfile.readString().trim();
            try {
                pidfile.getParent().execNoOutput("kill", pid);
            } catch (ExitCode e) {
                throw new IOException("ERROR: cannot stop logstash, pid=" + pid);
            }
            pidfile.deleteFile();
        }
    }

    @Override
    public void contextParameter(Stage stage, String host, int httpPort, FileNode webinf, Map<String, String> result) {
    }
}
