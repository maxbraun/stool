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
import net.oneandone.sushi.util.Separator;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class DebianMainSetup extends Debian {
    public static void main(String[] args) throws IOException {
        int result;

        try (PrintWriter out = new PrintWriter(new FileOutputStream("/tmp/dpkg-stool.log", true))) {
            result = new DebianMainSetup(out).run(args);
        }
        System.exit(result);
    }

    //--

    private final FileNode bin;
    private final FileNode lib;
    private final String config;
    private final String group;

    public DebianMainSetup(PrintWriter log) throws IOException {
        super(log);
        // this is not configurable, because the content comes from the package:
        bin = world.file("/usr/share/stool");

        lib = world.file(db_get("stool/lib"));
        config = db_get("stool/config");
        group = db_get("stool/group");
    }

    //--

    @Override
    public void preinstUpgrade(String version) throws IOException {
        log("preinst upgrade " + version);
        if (version != null && (version.startsWith("3.1.") || version.startsWith("3.2.")
                || version.equals("3.3.0~20151124103733") /* a rebuilt version of stool 3.2 with a 3.3 version to make it visible in our legacy debian repository */ )) {
            if (world.file("/opt/ui/opt/tools").isDirectory()) {
                log("upgrade stool config settings from version " + version);
                db_set("stool/lib", "/opt/ui/opt/tools/stool");
                db_set("stool/group", "users");
            } else {
                log("no upgrade required for config settings");
            }
        }
    }

    @Override
    public void postinstConfigure(String previous) throws IOException {
        FileNode binLib;

        setupGroup();
        setupLib();
        binLib = bin.join("lib");
        if (previous != null) {
            if (Files.deleteIfExists(binLib.toPath())) {
                log("cleaned previous lib: " + lib);
            }
        }
        lib.link(binLib);
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
        bin.join("lib").deleteDirectory();
        // bin itself is not deleted, it's removed by the package manager
        lib.deleteTree();
    }

    //--

    private void setupGroup() throws IOException {
        List<String> result;

        if (test("getent", "group", group)) {
            log("group: " + group + " (existing)");
        } else {
            result = new ArrayList<>();
            exec("groupadd", group);
            for (FileNode dir : world.file("/home/").list()) {
                if (dir.isDirectory()) {
                    String name = dir.getName();
                    if (test("id", "-u", name)) {
                        result.add(name);
                        exec("usermod", "-a", "-G", group, name);
                    }
                }
            }
            log("group: " + group + " (created with " + Separator.SPACE.join(result) + ")");
        }
    }

    //--

    public void setupLib() throws IOException {
        Lib h;

        h = new Lib(console, lib, group, config.trim().isEmpty() ? null : config);
        if (lib.exists()) {
            h.upgrade(bin);
            log("lib: " + lib.getAbsolute() + " (upgraded)");
        } else {
            console.info.println("creating lib: " + lib);
            try {
                h.create();
            } catch (IOException e) {
                // make sure we don't leave any undefined lib directory;
                try {
                    lib.deleteTreeOpt();
                } catch (IOException suppressed) {
                    e.addSuppressed(suppressed);
                }
                throw e;
            }
        }
    }
}
