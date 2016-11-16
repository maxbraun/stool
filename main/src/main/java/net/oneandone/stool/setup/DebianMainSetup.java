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
package net.oneandone.stool.setup;

import net.oneandone.sushi.fs.file.FileNode;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

public class DebianMainSetup extends Debian {
    public static void main(String[] args) throws IOException {
        int result;

        try (PrintWriter out = new PrintWriter(new FileOutputStream("/tmp/dpkg-stool.log", true))) {
            result = new DebianMainSetup(out).run(args);
        }
        System.exit(result);
    }

    //--

    private final String group;

    public DebianMainSetup(PrintWriter log) throws IOException {
        super(log);
        group = db_get("stool/group");
    }

    //--

    @Override
    public void preinstUpgrade(String version) throws IOException {
    }

    @Override
    public void postinstConfigure(String previous) throws IOException {
        for (String dir : new String[] {"logs", "run", "downloads", "backstages", "service-wrapper", "tomcat", "system" }) {
            setGroup(world.file("/usr/share/stool-3.4/" + dir));
        }
        log("setup service:\n" + slurp("update-rc.d", "stool", "defaults"));
        log("start service:\n" + slurp("service", "stool", "start"));
    }

    @Override
    public void postrmRemove() throws IOException {
        log(slurp("service", "stool", "stop"));
    }

    @Override
    public void postrmUpgrade() throws IOException {
        // TODO: prevent new stool invocations
    }

    @Override
    public void postrmPurge() throws IOException {
        exec("update-rc.d", "stool", "remove"); // Debian considers this a configuration file!?
    }

    // TODO
    private void setGroup(FileNode dir) throws IOException {
        exec("chgrp", "-R", group, dir.getAbsolute());
        // chgrp overwrites the permission - thus, i have to re-set permissions
        exec("chmod", "2775", dir.getAbsolute());
    }
}
