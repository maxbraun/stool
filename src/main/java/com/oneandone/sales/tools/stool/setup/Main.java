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
package com.oneandone.sales.tools.stool.setup;

import com.github.zafarkhaja.semver.Version;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.oneandone.sales.tools.stool.SystemImport;
import com.oneandone.sales.tools.stool.configuration.Configuration;
import com.oneandone.sales.tools.stool.util.Environment;
import com.oneandone.sales.tools.stool.util.RmRfThread;
import com.oneandone.sales.tools.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Cli;
import net.oneandone.sushi.cli.Command;
import net.oneandone.sushi.cli.Remaining;
import net.oneandone.sushi.fs.CopyException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Main extends Cli implements Command {
    public static void main(String[] args) throws Exception {
        System.exit(new Main().run(args));
    }

    private FileNode home;
    private FileNode oldHome;
    private final Map<String, String> config = new HashMap<>();

    private Main() {
    }

    @Remaining
    public void remaining(String str) {
        int idx;

        if (home == null) {
            home = console.world.file(str);
        } else if (oldHome == null) {
            oldHome = home;
            home = console.world.file(str);
        } else {
            idx = str.indexOf('=');
            if (idx == -1) {
                throw new ArgumentException("key=value expected, got " + str);
            }
            config.put(str.substring(0, idx).trim(), str.substring(idx + 1).trim());
        }
    }

    @Override
    public void printHelp() {
        console.info.println("Setup stool.");
        console.info.println("  Create a new Stool home directory or upgrade an existing.");
        console.info.println("usage: setup-stool [oldHome] home");
        console.info.println("documentation:");
        console.info.println("  http://wiki.intranet.1and1.com/bin/view/UE/StoolHome");
    }

    @Override
    public void invoke() throws Exception {
        Version version;
        Version old;
        Environment environment;

        version = Configuration.version();
        if (home == null) {
            throw new ArgumentException("missing home argument");
        }
        if (oldHome == null) {
            oldHome = home;
        } else {
            oldHome.checkDirectory();
        }
        environment = Environment.loadSystem();
        environment.setStoolHome(home);
        if (oldHome.exists()) {
            old = oldVersion();
            if (oldHome.equals(home)) {
                if (old.getMajorVersion() != version.getMajorVersion() || old.getMinorVersion() != version.getMinorVersion()) {
                    throw new ArgumentException("incremental upgrade " + old + " -> " + version
                            + " not possible, specify a new home to perform a full upgrade.");
                }
                incrementalUpgrade(old, version);
            } else {
                fullUpgrade(old, version, environment);
            }
        } else {
            console.info.println("Ready to install Stool " + version + " to " + home.getAbsolute());
            console.pressReturn();
            new Install(true, console, environment, config).invoke();
            console.info.println("Done. To complete the installation:");
            console.info.println("1. add");
            console.info.println("       source " + home.join("stool-function").getAbsolute());
            console.info.println("   to your ~/.bashrc");
            console.info.println("2. restart your shell");
        }
    }

    private void fullUpgrade(Version old, Version version, Environment environment) throws Exception {
        RmRfThread cleanup;
        Session session;

        console.info.println("Preparing full upgrade of " + home.getAbsolute() + ": " + old + " -> " + version);
        cleanup = new RmRfThread(console);
        cleanup.add(home);
        Runtime.getRuntime().addShutdownHook(cleanup);

        new Install(true, console, environment, config).invoke();
        session = Session.load(environment, console, null);
        new SystemImport(session, oldHome).invoke();

        Runtime.getRuntime().removeShutdownHook(cleanup);
        console.info.println("Done. To complete the installation:");
        console.info.println("1. change your ~/.bashrc to");
        console.info.println("       source " + home.join("stool-function").getAbsolute());
        console.info.println("2. restart your shell");
    }

    private void incrementalUpgrade(Version old, Version version) throws CopyException {
        FileNode jar;

        jar = home.join("bin/stool.jar");
        console.info.println("Ready for incremental upgrade of " + home.getAbsolute() + " from version " + old + " to " + version);
        console.info.println("M " + jar);
        console.pressReturn();
        console.world.locateClasspathItem(getClass()).copyFile(jar);
        console.info.println("done");
    }

    private Version oldVersion() throws IOException {
        Node file;
        JsonObject obj;

        file = oldHome.join("bin/stool.jar");
        if (file.exists()) {
            file = ((FileNode) file).openZip().join("META-INF/pominfo.properties");
            return Version.valueOf((String) file.readProperties().get("version"));
        } else {
            obj = (JsonObject) new JsonParser().parse(oldHome.join("config.json").readString());
            return Version.valueOf(obj.get("version").getAsString());
        }
    }
}
