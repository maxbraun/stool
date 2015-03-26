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

import com.github.zafarkhaja.semver.Version;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.oneandone.stool.SystemImport;
import net.oneandone.stool.configuration.StoolConfiguration;
import net.oneandone.stool.util.Environment;
import net.oneandone.stool.util.RmRfThread;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Cli;
import net.oneandone.sushi.cli.Command;
import net.oneandone.sushi.cli.Option;
import net.oneandone.sushi.cli.Remaining;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.Reader;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class Main extends Cli implements Command {
    public static void main(String[] args) throws Exception {
        Main main;
        String file;

        main = new Main();
        file = System.getenv("SETUP_STOOL_DEFAULTS");
        if (file != null) {
            main.defaultsFile(file);
        }
        System.exit(main.run(args));
    }

    private FileNode home;
    private FileNode oldHome;

    @Option("batch")
    private boolean batch;

    private final Environment environment;

    /** maps to String or Map<String, String> */
    private final Map<String, Object> config;

    private Main() {
        environment = Environment.loadSystem();
        config = new LinkedHashMap<>();
    }

    @Remaining
    public void remaining(String str) throws IOException {
        int idx;

        idx = str.indexOf('=');
        if (idx != -1) {
            config.put(str.substring(0, idx).trim(), str.substring(idx + 1).trim());
        } else if (str.startsWith("@")) {
            defaultsFile(str.substring(1));
        } else if (home == null) {
            home = console.world.file(str);
        } else if (oldHome == null) {
            oldHome = home;
            home = console.world.file(str);
        } else {
            throw new ArgumentException("too many directories");
        }
    }

    private void defaultsFile(String file) throws IOException {
        Object value;

        for (Map.Entry<String, JsonElement> entry : loadJson(console.world.file(file)).entrySet()) {
            if (entry.getValue() instanceof JsonObject) {
                value = toMap((JsonObject) entry.getValue());
            } else {
                value = environment.substitute(entry.getValue().getAsString());
            }
            config.put(entry.getKey(), value);
        }
    }

    @Override
    public void printHelp() {
        console.info.println("Setup stool " + versionObject());
        console.info.println("usage: setup-stool [<old>] <home>");
        console.info.println("  Create a new <home> directory, upgrades an existing <home> (incremental upgrade), ");
        console.info.println("  or upgrade and existing <old> home directory into a new <home> (full upgrade).");
        console.info.println("  Does not modify anything outside the home directory.");
        console.info.println("documentation:");
        console.info.println("  https://github.com/mlhartme/stool");
    }

    @Override
    public void invoke() throws Exception {
        String user;
        Version version;
        Version old;

        user = System.getProperty("user.name");
        version = versionObject();
        if (home == null) {
            printHelp();
            return;
        }
        if (oldHome == null) {
            oldHome = home;
        } else {
            oldHome.checkDirectory();
        }
        environment.setStoolHome(home);
        if (oldHome.exists()) {
            try {
                old = oldVersion();
            } catch (IOException e) {
                throw new ArgumentException("Cannot detect Stool version from old Stool home directory " + oldHome + ": "
                    + e.getMessage(), e);
            }
            if (oldHome.equals(home)) {
                if (old.getMajorVersion() != version.getMajorVersion() || old.getMinorVersion() != version.getMinorVersion()) {
                    throw new ArgumentException("incremental upgrade " + old + " -> " + version
                            + " not possible, specify a new home to perform a full upgrade.");
                }
                incrementalUpgrade(old, version);
            } else {
                fullUpgrade(user, old, version, environment);
            }
        } else {
            console.info.println("Ready to install Stool " + version + " to " + home.getAbsolute());
            if (!batch) {
                console.pressReturn();
            }
            new Install(true, console, environment, config).invoke(user);
            console.info.println("Done. To complete the installation:");
            console.info.println("1. add");
            console.info.println("       source " + home.join("stool-function").getAbsolute());
            console.info.println("   to your ~/.bashrc");
            console.info.println("2. restart your shell");
        }
    }

    private void fullUpgrade(String user, Version old, Version version, Environment environment) throws Exception {
        RmRfThread cleanup;
        Session session;

        console.info.println("Preparing full upgrade of " + home.getAbsolute() + ": " + old + " -> " + version);
        cleanup = new RmRfThread(console);
        cleanup.add(home);
        Runtime.getRuntime().addShutdownHook(cleanup);

        session = new Install(true, console, environment, config).invoke(user);
        new SystemImport(session, oldHome).invoke();

        Runtime.getRuntime().removeShutdownHook(cleanup);
        console.info.println("Done. To complete the installation:");
        console.info.println("1. change your ~/.bashrc to");
        console.info.println("       source " + home.join("stool-function").getAbsolute());
        console.info.println("2. restart your shell");
    }

    private void incrementalUpgrade(Version old, Version version) throws IOException {
        FileNode timestamp;
        FileNode link;

        timestamp = home.join("bin/stool-" + Install.FMT.format(new Date()) + ".jar");
        link = home.join("bin/stool.jar");
        console.info.println("Ready for incremental upgrade of " + home.getAbsolute() + " from version " + old + " to " + version);
        console.info.println("A " + timestamp);
        console.info.println("M " + link);
        if (!batch) {
            console.pressReturn();
        }
        link.deleteFile();
        link.mklink(timestamp.getName());
        console.world.locateClasspathItem(getClass()).copyFile(timestamp);
        console.info.println("Done. Consider 'stool -stage overview refresh' now.");
    }

    private Version oldVersion() throws IOException {
        Node file;
        JsonObject obj;

        file = oldHome.join("bin/stool.jar");
        if (file.exists()) {
            file = ((FileNode) file).openZip().join("META-INF/maven/net.oneandone/stool/pom.properties");
            return Version.valueOf((String) file.readProperties().get("version"));
        } else {
            obj = (JsonObject) new JsonParser().parse(oldHome.join("config.json").readString());
            return Version.valueOf(obj.get("version").getAsString());
        }
    }

    //--

    private Map<String, Object> toMap(JsonObject object) {
        Map<String, Object> result;
        JsonElement value;

        result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            value = entry.getValue();
            if (value instanceof JsonObject) {
                result.put(entry.getKey(), toMap((JsonObject) value));
            } else {
                result.put(entry.getKey(), environment.substitute(value.getAsString()));
            }
        }
        return result;
    }

    private static JsonObject loadJson(FileNode file) throws IOException {
        JsonParser parser;

        parser = new JsonParser();
        try (Reader src = file.createReader()) {
            return (JsonObject) parser.parse(src);
        }
    }

    //--

    // TODO: doesn't work in integration tests
    public static Version versionObject() {
        String str;

        str = StoolConfiguration.class.getPackage().getSpecificationVersion();
        return Version.valueOf(String.valueOf(str));
    }

}
