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
import net.oneandone.stool.setup.JavaSetup;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.users.User;
import net.oneandone.stool.users.UserNotFound;
import net.oneandone.stool.users.Users;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.ModeException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.ReadLinkException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Strings;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.MessagingException;
import javax.naming.NamingException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class Session {
    public static Session load(Logging logging, String user, String command, Environment environment, Console console,
                               FileNode invocationFile, String svnuser, String svnpassword) throws IOException {
        Session session;

        session = loadWithoutBackstageWipe(logging, user, command, environment, console, invocationFile, svnuser, svnpassword);

        // Stale backstage wiping: how to detect backstages who's stage directory was removed.
        //
        // My first thought was to watch for filesystem events to trigger backstage wiping.
        // But there's quite a big delay and rmdif+mkdir is reported as modification. Plus the code is quite complex and
        // I don't know how to handle overflow events.
        // So I simple wipe thmm whenever I load stool home. That's a well-defined timing and that's before stool might
        // use a stale stage.
        session.wipeStaleBackstages();
        return session;
    }

    public void wipeStaleBackstages() throws IOException {
        long s;
        String pid;

        s = System.currentTimeMillis();
        for (FileNode backstage : backstages.list()) {
            if (backstage.isDirectory()) {
                FileNode anchor = backstage.join("anchor");
                if (!anchor.isDirectory() && anchor.isLink()) {
                    for (Node pidfile : backstage.find("shared/run/*.pid")) {
                        pid = pidfile.readString().trim();
                        console.verbose.println("killing " + pid);
                        // TODO: sudo ...
                        new Launcher(backstage, "kill", "-9", pid).execNoOutput();
                    }
                    console.verbose.println("stale backstage detected: " + backstage);
                    try {
                        backstage.deleteTree();
                    } catch (IOException e) {
                        console.error.println(backstage + ": cannot delete stale backstage: " + e.getMessage());
                        e.printStackTrace(console.verbose);
                    }
                }
            }
        }
        console.verbose.println("wipeStaleBackstages done, ms=" + ((System.currentTimeMillis() - s)));
    }

    public static FileNode locateHome(FileNode bin) throws ReadLinkException {
        return (FileNode) bin.join("home").resolveLink();
    }

    private static Session loadWithoutBackstageWipe(Logging logging, String user, String command, Environment environment, Console console,
                                                  FileNode invocationFile, String svnuser, String svnpassword) throws IOException {
        ExtensionsFactory factory;
        Gson gson;
        FileNode home;
        FileNode bin;
        Session result;

        factory = ExtensionsFactory.create(console.world);
        gson = gson(console.world, factory);
        bin = environment.stoolBin(console.world);
        bin.checkDirectory();
        home = locateHome(bin);
        result = new Session(factory, gson, logging, user, command, home, bin, console, environment, StoolConfiguration.load(gson, home),
                Bedroom.loadOrCreate(gson, home), invocationFile, svnuser, svnpassword);
        result.selectedStageName = environment.getOpt(Environment.STOOL_SELECTED);
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
    public final FileNode bin;
    private String lazyGroup;

    public final Console console;
    public final Environment environment;
    public final StoolConfiguration configuration;
    public final Bedroom bedroom;

    public final FileNode backstages;


    /** may be null */
    private final FileNode invocationFile;
    private final Subversion subversion;

    private String selectedStageName;
    private final String stageIdPrefix;
    private int nextStageId;
    public final Users users;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyMMdd");

    public Session(ExtensionsFactory extensionsFactory, Gson gson, Logging logging, String user, String command, FileNode home, FileNode bin,
                   Console console, Environment environment, StoolConfiguration configuration,
                   Bedroom bedroom, FileNode invocationFile, String svnuser, String svnpassword) {
        this.extensionsFactory = extensionsFactory;
        this.gson = gson;
        this.logging = logging;
        this.user = user;
        this.command = command;
        this.home = home;
        this.bin = bin;
        this.lazyGroup = null;
        this.console = console;
        this.environment = environment;
        this.configuration = configuration;
        this.bedroom = bedroom;
        this.backstages = home.join("backstages");
        this.selectedStageName = null;
        this.invocationFile = invocationFile;
        this.subversion = new Subversion(svnuser, svnpassword);
        this.stageIdPrefix = FMT.format(LocalDate.now()) + "." + logging.id + ".";
        this.nextStageId = 0;
        if (configuration.ldapUrl.isEmpty()) {
            this.users = Users.fromLogin();
        } else {
            this.users = Users.fromLdap(configuration.ldapUrl, configuration.ldapPrincipal, configuration.ldapCredentials);
        }
    }

    //--

    private static final Logger LOG = LoggerFactory.getLogger(Session.class);

    /** logs an error for administrators, i.e. the user is not expected to understand/fix this problem. */
    public void reportException(String context, Throwable e) {
        String subject;
        StringWriter body;
        PrintWriter writer;

        LOG.error("[" + command + "] " + context + ": " + e.getMessage(), e);
        if (!configuration.contactAdmin.isEmpty()) {
            subject = "[stool exception] " + e.getMessage();
            body = new StringWriter();
            body.write("stool: " + JavaSetup.versionObject().toString() + "\n");
            body.write("command: " + command + "\n");
            body.write("context: " + context + "\n");
            body.write("user: " + user + "\n");
            body.write("hostname: " + configuration.hostname + "\n");
            writer = new PrintWriter(body);
            while (true) {
                e.printStackTrace(writer);
                e = e.getCause();
                if (e == null) {
                    break;
                }
                body.append("Caused by:\n");
            }
            try {
                configuration.mailer().send(configuration.contactAdmin,
                        new String[]{configuration.contactAdmin}, subject, body.toString());
            } catch (MessagingException suppressed) {
                LOG.error("cannot send exception email: " + suppressed.getMessage(), suppressed);
            }
        }
    }

    //--

    public String group() throws ModeException {
        if (lazyGroup == null) {
            lazyGroup = home.getGroup().toString();
        }
        return lazyGroup;
    }

    public FileNode bin(String name) {
        return bin.join(name);
    }

    //-- environment handling

    public static int memTotal() {
        long result;

        result = ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalPhysicalMemorySize();
        return (int) (result / 1024 / 1024);
    }

    //--

    public List<Stage> list(EnumerationFailed problems, Predicate predicate) throws IOException {
        List<Stage> result;
        Stage stage;

        result = new ArrayList<>();
        for (FileNode backstage : backstages.list()) {
            if (StageConfiguration.file(backstage).exists()) {
                try {
                    stage = Stage.load(this, backstage);
                } catch (IOException e) {
                    problems.add(backstage, e);
                    continue;
                }
                if (predicate.matches(stage)) {
                    result.add(stage);
                }
            } else {
                // stage is being created, we're usually waiting the the checkout to complete
            }
        }
        return result;
    }

    public List<Stage> listWithoutSystem() throws IOException {
        List<Stage> result;
        EnumerationFailed problems;

        problems = new EnumerationFailed();
        result = list(problems, new Predicate() {
            @Override
            public boolean matches(Stage stage) {
                return !stage.isSystem();
            }
        });
        for (Map.Entry<FileNode, Exception> entry : problems.problems.entrySet()) {
            reportException(entry.getKey() + ": Session.listWithoutDashboard", entry.getValue());
        }
        return result;
    }


    public void select(Stage selected) {
        if (selected == null) {
            throw new IllegalArgumentException();
        }
        selectedStageName = selected.getName();
        environment.setAll(environment(selected));
    }

    public void backupEnvironment() {
        String backupKey;
        String backupValue;

        for (String key : environment(null).keys()) {
            backupKey = Environment.backupKey(key);
            backupValue = environment.getOpt(backupKey);
            if (backupValue != null) {
                throw new ArgumentException("session already opened (environment variable already defined: " + backupKey + ")");
            }
            environment.set(backupKey, environment.getOpt(key));
        }
    }

    public void resetEnvironment() {
        Environment reset;
        String backupKey;
        String backupValue;

        reset = environment(null);
        for (String key : reset.keys()) {
            backupKey = Environment.backupKey(key);
            backupValue = environment.getOpt(backupKey);
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
        FileNode backstage;

        backstage = backstages.join(stageName);
        return Stage.load(this, backstage);
    }
    public List<String> stageNames() throws IOException {
        List<FileNode> files;
        List<String> result;

        files = backstages.list();
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

    public Environment environment(Stage stage) {
        Environment env;
        String stoolIndicator;
        String mavenOpts;
        String prompt;

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
        env.set(Environment.MAVEN_HOME, (stage != null && stage.config().mavenHome() != null) ? stage.config().mavenHome() : null);
        env.set(Environment.MAVEN_OPTS, mavenOpts);
        // to avoid ulimit permission denied warnings on Ubuntu machines:
        if (stage == null) {
            stoolIndicator = "";
        } else {
            stoolIndicator = "\\[$(stoolIndicatorColor)\\]" + stage.getName() + "\\[\\e[m\\]";
        }
        prompt = configuration.prompt;
        prompt = Strings.replace(prompt, "\\+", stoolIndicator);
        prompt = Strings.replace(prompt, "\\=", this.environment.getOpt(Environment.backupKey(Environment.PS1)));
        env.set(Environment.PS1, prompt);
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
        for (FileNode backstage : getBackstages()) {
            if (Stage.shared(backstage).join("run/tomcat.pid").exists()) {
                stage = loadStageConfiguration(backstage);
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

    public List<FileNode> getBackstages() throws IOException {
        List<FileNode> lst;

        lst = backstages.list();
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
        chown(newOwner, stage.backstage, stage.getDirectory());
    }

    public void chown(String newOwner, FileNode ... dirs) throws Failure {
        Launcher launcher;

        launcher = new Launcher(home, "sudo", bin("chowntree.sh").getAbsolute(), newOwner);
        for (FileNode dir : dirs) {
            launcher.arg(dir.getAbsolute());
        }
        launcher.exec(console.info);
    }

    /** session lock */
    public Lock lock() {
        return new Lock(user, home.join("run/stool.lock"));
    }

    public boolean isSelected(Stage stage) {
        return stage.getName().equals(selectedStageName);
    }

    //-- stage properties


    public void saveStageProperties(StageConfiguration stageConfiguration, Node backstage) throws IOException {
        stageConfiguration.save(gson, StageConfiguration.file(backstage));
    }

    public StageConfiguration loadStageConfiguration(Node backstage) throws IOException {
        return StageConfiguration.load(gson, StageConfiguration.file(backstage));
    }

    //-- stool properties

    public List<FileNode> stageDirectories() throws IOException {
        List<FileNode> result;

        result = new ArrayList<>();
        for (FileNode backstage : getBackstages()) {
            result.add((FileNode) Stage.anchor(backstage).resolveLink());
        }
        return result;
    }

    public Pool createPool() {
        return new Pool(configuration.portFirst, configuration.portLast, backstages, configuration.reservedPorts.values());
    }

    public StageConfiguration createStageConfiguration(String url) throws IOException {
        String mavenHome;
        StageConfiguration stage;

        try {
            mavenHome = Maven.locateMaven(console.world).getAbsolute();
        } catch (IOException e) {
            mavenHome = "";
        }
        stage = new StageConfiguration(nextStageId(), javaHome(), mavenHome, extensionsFactory.newInstance());
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

    public static String jdkHome() {
        return Strings.removeRightOpt(javaHome(), "/jre");
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

    public FileNode downloadCache() {
        return configuration.downloadCache;
    }
}