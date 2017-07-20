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
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

public class Fault implements Extension {
    private String project;

    public Fault() {
        this.project = "";
    }

    @Override
    public Map<String, FileNode> vhosts(Stage stage) {
        return new HashMap<>();
    }

    @Override
    public void beforeStart(Stage stage) throws IOException {
        FileNode notify;
        String fault;
        Launcher l;
        Launcher.Handle handle;
        FileNode log;
        long waiting;
        int exitCode;

        notify = stage.session.world.getTemp().createTempFile();
        fault = "fault -auth=false while -workspace " + wspath(stage) + " -notify " + notify.getAbsolute() + " "
                + project + " " + pidfile(stage);
        log = stage.backstage.join("fault.log");
        l = stage.launcher("bash", "-c", fault + ">" + log.getAbsolute() + " 2>&1");
        handle = l.launch();
        waiting = 0;
        do {
            try {
                Thread.sleep(50);
                waiting += 50;
            } catch (InterruptedException e) {
                // fall through
            }
            if (!handle.process.isAlive()) {
                try {
                    exitCode = handle.process.waitFor();
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                throw new IOException("fault terminated with exit code " + exitCode + ":\n" + log.readString());
            }
            if (waiting > 5000) {
                handle.process.destroy();
                throw new IOException("fault timed out, log output:\n" + log.readString());
            }
        } while (notify.isFile());
        stage.session.console.verbose.println("started " + l);
    }

    @Override
    public void beforeStop(Stage stage) throws IOException {
    }

    @Override
    public void contextParameter(Stage stage, String host, int httpPort, FileNode webinf, Map<String, String> result) {
    }

    @Override
    public void tomcatOpts(Stage stage, Map<String, String> result) {
        result.put("fault.workspace", wspath(stage));
    }

    private static String wspath(Stage stage) {
        return stage.backstage.join("fault").getAbsolute();
    }
    private static String pidfile(Stage stage) {
        return stage.servicePidFile().getAbsolute();
    }
}
