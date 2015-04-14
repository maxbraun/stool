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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.oneandone.maven.embedded.Maven;
import net.oneandone.stool.EnumerationFailed;
import net.oneandone.stool.configuration.Bedroom;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.configuration.StoolConfiguration;
import net.oneandone.stool.configuration.Until;
import net.oneandone.stool.configuration.adapter.ExtensionsAdapter;
import net.oneandone.stool.configuration.adapter.FileNodeTypeAdapter;
import net.oneandone.stool.configuration.adapter.UntilTypeAdapter;
import net.oneandone.stool.extensions.ExtensionsFactory;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.users.User;
import net.oneandone.stool.users.UserNotFound;
import net.oneandone.stool.users.Users;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.ModeException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Strings;
import org.codehaus.plexus.DefaultPlexusContainer;

import javax.naming.NamingException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/** Mostly a representation of $STOOL_HOME */
public class Session {
    public static Session load(Logging logging, String user, String command, Environment environment, Console console, FileNode invocationFile) throws IOException {
        Session session;

        session = loadWithoutWipe(logging, user, command, environment, console, invocationFile);

        // my first thought was to watch for filesystem events to trigger wrapper wiping.
        // But there's quite a big delay and rmdif+mkdir is reported as modification. Plus the code is quite complex and
        // I don't know how to handle overflow events.
        // So I simple wipe thmm whenever I load stool home. That's a well-defined timing and that's before stool might
        // use a stale stage.
        session.wipeStaleWrappers();
        return session;
    }

    public void wipeStaleWrappers() throws IOException {
        long s;
        String pid;

        s = System.currentTimeMillis();
        for (FileNode wrapper : wrappers.list()) {
            if (wrapper.isDirectory()) {
                FileNode anchor = wrapper.join("anchor");
                if (!anchor.isDirectory() && anchor.isLink()) {
                    for (Node pidfile : wrapper.find("shared/run/*.pid")) {
                        pid = pidfile.readString().trim();
                        console.verbose.println("killing " + pid);
                        // TODO: sudo ...
                        new Launcher(wrapper, "kill", "-9", pid).execNoOutput();
                    }
                    console.verbose.println("stale wrapper detected: " + wrapper);
                    try {
                        wrapper.deleteTree();
                    } catch (IOException e) {
                        console.error.println(wrapper + ": cannot delete stale wrapper: " + e.getMessage());
                        e.printStackTrace(console.verbose);
                    }
                }
            }
        }
        console.verbose.println("wipeStaleWrappers done, ms=" + ((System.currentTimeMillis() - s)));
    }

    private static Session loadWithoutWipe(Logging logging, String user, String command, Environment environment, Console console, FileNode invocationFile) throws IOException {
        ExtensionsFactory factory;
        Gson gson;
        FileNode home;
        Session result;

        factory = ExtensionsFactory.create(console.world);
        gson = gson(console.world, factory);
        home = environment.stoolHome(console.world);
        home.checkDirectory();
        result = new Session(factory, gson, logging, user, command, home, console, environment, StoolConfiguration.load(gson, home), Bedroom.loadOrCreate(gson, home), invocationFile);
        result.selectedStageName = environment.get(Environment.STOOL_SELECTED);
        return result;
    }

    private static final int MEM_RESERVED_OS = 500;

    //--

    public final ExtensionsFactory extensionsFactory;
    public final Gson gson;
    public final Logging logging;
    public final String user;
    public final String command;

    // TODO: redundant!
    public final FileNode home;
    public final Console console;
    public final Environment environment;
    public final StoolConfiguration configuration;
    public final Bedroom bedroom;

    public final FileNode wrappers;


    /** may be null */
    private final FileNode invocationFile;
    private final Subversion subversion;

    private String selectedStageName;
    private String stageIdPrefix;
    private int nextStageId;
    public final Users users;

    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyMMdd");

    public Session(ExtensionsFactory extensionsFactory, Gson gson, Logging logging, String user, String command, FileNode home, Console console, Environment environment, StoolConfiguration configuration,
                   Bedroom bedroom, FileNode invocationFile) {
        this.extensionsFactory = extensionsFactory;
        this.gson = gson;
        this.logging = logging;
        this.user = user;
        this.command = command;
        this.home = home;
        this.console = console;
        this.environment = environment;
        this.configuration = configuration;
        this.bedroom = bedroom;
        this.wrappers = home.join("wrappers");
        this.selectedStageName = null;
        this.invocationFile = invocationFile;
        this.subversion = new Subversion(null, null);
        this.stageIdPrefix = FMT.format(new Date()) + "." + logging.id + ".";
        this.nextStageId = 0;
        if (configuration.ldapUrl.isEmpty()) {
            this.users = Users.fromLogin();
        } else {
            this.users = Users.fromLdap(configuration.ldapUrl, configuration.ldapPrincipal, configuration.ldapCredentials);
        }
    }

    //--

    public void saveConfiguration() throws IOException {
        configuration.save(gson, home);
    }


    public static String jdkHome() {
        String result;

        result = System.getProperty("java.home");
        result = Strings.removeRightOpt(result, "/");
        return Strings.removeRightOpt(result, "/jre");
    }

    public FileNode bin(String name) {
        return home.join("bin", name);
    }

    //-- environment handling

    public static int memTotal() throws IOException {
        long result;

        result = ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalPhysicalMemorySize();
        return (int) (result / 1024 / 1024);
    }

    //--

    public List<Stage> list(EnumerationFailed problems, Predicate predicate) throws IOException {
        List<Stage> result;
        Stage stage;

        result = new ArrayList<>();
        for (FileNode wrapper : wrappers.list()) {
            try {
                stage = Stage.load(this, wrapper);

            } catch (IOException e) {
                problems.add(wrapper, e);
                continue;
            }
            if (predicate.matches(stage)) {
                result.add(stage);
            }
        }
        return result;
    }

    public List<Stage> listWithoutOverview() throws IOException, EnumerationFailed {
        List<Stage> result;
        EnumerationFailed problems;

        problems = new EnumerationFailed();
        result = list(problems, new Predicate() {
            @Override
            public boolean matches(Stage stage) {
                return !stage.isOverview();
            }
        });
        if (!problems.empty()) {
            throw problems;
        }
        return result;
    }


    public void select(Stage selected) throws ModeException {
        if (selected == null) {
            throw new IllegalArgumentException();
        }
        selectedStageName = selected.getName();
        environment.setAll(environment(selected));
    }

    public void backupEnvironment() throws ModeException {
        String backupKey;
        String backupValue;

        for (String key : environment(null).keys()) {
            backupKey = Environment.backupKey(key);
            backupValue = environment.get(backupKey);
            if (backupValue != null) {
                throw new ArgumentException("session already opened (environment variable already defined: " + backupKey + ")");
            }
            environment.set(backupKey, environment.get(key));
        }
    }

    public void resetEnvironment() throws IOException {
        Environment reset;
        String backupKey;
        String backupValue;

        reset = environment(null);
        for (String key : reset.keys()) {
            backupKey = Environment.backupKey(key);
            backupValue = environment.get(backupKey);
            environment.set(key, backupValue);
            environment.set(backupKey, null);
        }
    }

    public void invocationFileUpdate() throws IOException {
        List<String> lines;

        lines = new ArrayList<>();
        for (String key : environment(null).keys()) {
            lines.add(environment.code(key));
            lines.add(environment.code(Environment.backupKey(key)));
        }
        if (invocationFile != null) {
            if (console.getVerbose()) {
                for (String line : lines) {
                    console.verbose.println("[env] " + line);
                }
            }
            invocationFile.writeLines(lines);
        }
    }

    public Subversion subversion() {
        return subversion;
    }
    public Stage load(String stageName) throws IOException {
        FileNode wrapper;

        wrapper = wrappers.join(stageName);
        return Stage.load(this, wrapper);
    }
    public List<String> stageNames() throws IOException {
        List<FileNode> files;
        List<String> result;

        files = wrappers.list();
        result = new ArrayList<>(files.size());
        for (FileNode file : files) {
            result.add(file.getName());
        }
        return result;
    }

    //-- Memory checks - all value in MB
    public String getSelectedStageName() {
        return selectedStageName;
    }

    public Environment environment(Stage stage) throws ModeException {
        Environment env;
        String stoolIndicator;
        String mavenOpts;

        if (stage == null) {
            mavenOpts = "";
        } else {
            mavenOpts = stage.macros().replace(stage.config().mavenOpts);
        }
        env = new Environment();
        env.set(Environment.STOOL_SELECTED, selectedStageName);
        // for pws and repositories:
        if (stage != null) {
            env.set(Environment.MACHINE, stage.getMachine());
        }
        // for pws:
        env.set(Environment.STAGE_HOST, stage != null ? stage.getName() + "." + configuration.hostname : null);
        // not that both MAVEN and ANT use JAVA_HOME to locate their JVM - it's not necessary to add java to the PATH variable
        env.set(Environment.JAVA_HOME, stage != null ? stage.config().javaHome : null);
        env.set(Environment.MAVEN_HOME, stage != null ? stage.config().mavenHome : null);
        env.set(Environment.MAVEN_OPTS, mavenOpts);
        // to avoid ulimit permission denied warnings on Ubuntu machines:
        if (stage == null) {
            stoolIndicator = "";
        } else {
            stoolIndicator = "\\[$(stoolIndicatorColor)\\]" + stage.getName() + "\\[\\e[m\\]";
        }
        env.set(Environment.PS1, Strings.replace(configuration.prompt, "\\+", stoolIndicator));
        env.set(Environment.PWD, (stage == null ? ((FileNode) console.world.getWorking()) : stage.getDirectory()).getAbsolute());
        return env;
    }

    //--

    /** @return memory not yet reserved */
    public int memUnreserved() throws IOException {
        return memTotal() - MEM_RESERVED_OS - memReservedTomcats();
    }

    /** used for running tomcat */
    private int memReservedTomcats() throws IOException {
        int reserved;
        StageConfiguration stage;

        reserved = 0;
        for (FileNode wrapper : getWrappers()) {
            if (Stage.shared(wrapper).join("run/tomcat.pid").exists()) {
                stage = loadStageConfiguration(wrapper);
                reserved += stage.tomcatHeap;
                reserved += stage.tomcatPerm;
            }
        }
        return reserved;
    }

    public void checkDiskFree() {
        int free;
        int min;

        free = diskFree();
        min = configuration.diskMin;
        if (free < min) {
            throw new ArgumentException("Disk almost full. Currently available " + free + " mb, required " + min + " mb.");
        }
    }

    /** @return Free disk space in partition used for stool home. CAUTION: not necessarily the partition used for stages. */
    public int diskFree() {
        return (int) (home.toPath().toFile().getUsableSpace() / 1024 / 1024);
    }

    public List<FileNode> getWrappers() throws IOException {
        List<FileNode> lst;

        lst = wrappers.list();
        Collections.sort(lst, new Comparator<Node>() {
            @Override
            public int compare(Node left, Node right) {
                return left.getName().compareTo(right.getName());
            }
        });
        return lst;
    }

    public User lookupUser(String login) throws NamingException, UserNotFound {
        if (configuration.shared) {
            return users.byLogin(login);
        } else {
            return null;
        }
    }


    public void chown(Stage stage, String newOwner) throws Failure {
        new Launcher(home, "sudo", bin("chowntree.sh").getAbsolute(), newOwner, stage.wrapper.getAbsolute(), stage.getDirectory().getAbsolute()).exec(console.info);
    }

    /** session lock */
    public Lock lock() {
        return new Lock(user, home.join("sessions/stool.lock"));
    }

    public boolean isSelected(Stage stage) {
        return stage.getName().equals(selectedStageName);
    }

    //-- stage properties


    public void saveStageProperties(StageConfiguration stageConfiguration, Node wrapper) throws IOException {
        stageConfiguration.save(gson, wrapper);
    }

    public StageConfiguration loadStageConfiguration(Node wrapper) throws IOException {
        return StageConfiguration.load(gson, wrapper);
    }

    //-- stool properties

    public List<FileNode> stageDirectories() throws IOException {
        List<FileNode> result;

        result = new ArrayList<>();
        for (FileNode wrapper : getWrappers()) {
            result.add((FileNode) Stage.anchor(wrapper).resolveLink());
        }
        return result;
    }

    public Pool createPool() {
        return new Pool(configuration.portFirst, configuration.portLast, configuration.portOverview, wrappers);
    }

    public StageConfiguration createStageConfiguration(String url) throws IOException {
        StageConfiguration stage;

        stage = new StageConfiguration(nextStageId(), javaHome(), Maven.locateMaven(console.world).getAbsolute(), extensionsFactory.newInstance());
        configuration.setDefaults(StageConfiguration.properties(extensionsFactory), stage, url);
        return stage;
    }

    private String nextStageId() {
        nextStageId++;
        return stageIdPrefix + nextStageId;
    }

    public static String javaHome() {
        String result;

        result = System.getProperty("java.home");
        if (result == null) {
            throw new IllegalStateException();
        }
        result = Strings.removeRightOpt(result, "/");
        return result;
    }

    private DefaultPlexusContainer lazyPlexus;

    public DefaultPlexusContainer plexus() {
        if (lazyPlexus == null) {
            lazyPlexus = Maven.container();
        }
        return lazyPlexus;
    }

    public static Gson gson(World world, ExtensionsFactory factory) {
        return new GsonBuilder()
                .registerTypeAdapter(FileNode.class, new FileNodeTypeAdapter(world))
                .registerTypeAdapter(Until.class, new UntilTypeAdapter())
                .registerTypeAdapterFactory(ExtensionsAdapter.factory(factory))
                .disableHtmlEscaping()
                .serializeNulls()
                .excludeFieldsWithModifiers(Modifier.STATIC, Modifier.TRANSIENT)
                .setPrettyPrinting()
                .create();
    }

    public FileNode downloads() {
        return configuration.downloads;
    }
}
