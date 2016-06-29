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
package net.oneandone.stool.stage;

import net.oneandone.inline.ArgumentException;
import net.oneandone.inline.Console;
import net.oneandone.maven.embedded.Maven;
import net.oneandone.stool.cli.Start;
import net.oneandone.stool.configuration.Expire;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.extensions.Extensions;
import net.oneandone.stool.scm.Scm;
import net.oneandone.stool.stage.artifact.Changes;
import net.oneandone.stool.util.Files;
import net.oneandone.stool.util.KeyStore;
import net.oneandone.stool.util.Macros;
import net.oneandone.stool.util.OwnershipException;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.ServerXml;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.GetLastModifiedException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.ReadLinkException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.OS;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.repository.RepositoryPolicy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Concrete implementations are SourceStage or ArtifactStage.
 */
public abstract class Stage {
    public static FileNode backstageDirectory(FileNode dir) {
        return dir.join(".stool");
    }

    //--

    public static Stage load(Session session, FileNode backstageLink) throws IOException {
        FileNode backstageResolved;

        backstageResolved = backstageLink.resolveLink();
        return load(session, session.loadStageConfiguration(backstageResolved), backstageLink.getName(), backstageResolved.getParent());
    }

    private static Stage load(Session session, StageConfiguration configuration, String name, FileNode directory) throws IOException {
        Stage result;
        String url;

        url = probe(directory);
        if (url == null) {
            throw new IOException("cannot determine stage url: " + directory);
        }
        result = createOpt(session, url, configuration, name, directory);
        if (result == null) {
            throw new IOException("unknown stage type: " + directory);
        }
        return result;
    }

    /** @return stage url or null if not a stage */
    public static String probe(FileNode directory) throws IOException {
        Node artifactGav;

        directory.checkDirectory();
        artifactGav = ArtifactStage.urlFile(directory);
        if (artifactGav.exists()) {
            return artifactGav.readString().trim();
        }
        return Scm.checkoutUrlOpt(directory);
    }

    public static Stage createOpt(Session session, String url, StageConfiguration configuration, String name, FileNode directory) throws IOException {
        if (configuration == null) {
            throw new IllegalArgumentException();
        }
        directory.checkDirectory();
        if (url.startsWith("gav:") || url.startsWith("file:")) {
            return new ArtifactStage(session, url, name, directory, configuration);
        }
        if (directory.join(configuration.pom).exists()) {
            return SourceStage.forLocal(session, name, directory, configuration);
        }
        return null;
    }

    public static FileNode shared(FileNode backstage) {
        return backstage.join("shared");
    }

    //--

    public final Session session;

    //-- main methods
    protected final String url;

    private final String name;

    /**
     * Contains two types of files:
     * * stool files:
     *   generated by stool, owned by the current user. Theses nodes get shared permissions
     * * runtime files:
     *   generated by tomcat, owned by the stage owner. These nodes get normal permissions of the owner
     */
    public FileNode backstage;

    /** user visible directory */
    protected FileNode directory;
    private final StageConfiguration configuration;
    private Maven lazyMaven;

    //--

    public Stage(Session session, String url, String name, FileNode directory, StageConfiguration configuration) throws ReadLinkException {
        this.session = session;
        this.url = url;
        this.name = name;
        this.backstage = backstageDirectory(directory);
        this.directory = directory;
        this.configuration = configuration;
    }

    public FileNode shared() {
        return shared(backstage);
    }

    /** User visible name of the stage */
    public String getName() {
        return name;
    }

    public FileNode getBackstage() {
        return backstage;
    }
    public FileNode getDirectory() {
        return directory;
    }
    public String getUrl() {
        return url;
    }
    public StageConfiguration config() {
        return configuration;
    }
    public String getType() {
        return getClass().getSimpleName().toLowerCase();
    }

    public String backstageLock() {
        return "backstage-" + config().id;
    }

    public String directoryLock() {
        return "directory-" + config().id;
    }

    private KeyStore keystore() throws IOException {
        KeyStore keyStore;
        FileNode sslDir;
        String hostname;

        if (session.configuration.certificates.isEmpty()) {
            return null;
        }
        sslDir = shared().join("ssl");
        keyStore = new KeyStore(sslDir);
        if (!keyStore.exists()) {
            if (session.configuration.vhosts) {
                hostname = "*." + getName() + "." + session.configuration.hostname;
                keyStore.download(session.configuration.certificates, hostname);
            } else {
                keyStore.download(session.configuration.certificates, session.configuration.hostname);
            }
        }
        return keyStore;
    }

    public abstract boolean updateAvailable();

    /**
     * @return name "." first part of the domain
     */
    public String getMachine() {
        String machine;
        int idx;

        machine = session.configuration.hostname;
        idx = machine.indexOf('.');
        if (idx != -1) {
            machine = machine.substring(0, idx);
        }
        return getName() + "." + machine;
    }

    public String owner() throws IOException {
        return directory.getOwner().getName();
    }

    //-- pid file handling

    public boolean isWorking() throws IOException {
        return session.lockManager.hasExclusiveLocks(directoryLock(), backstageLock());
    }

    public State state() throws IOException {
        if (session.bedroom.stages().contains(getName())) {
            return State.SLEEPING;
        } else if (runningService() != 0) {
            return State.UP;
        } else {
            return State.DOWN;
        }

    }

    public int runningService() throws IOException {
        return readPidOpt(servicePidFile());
    }

    /** @return pid or null */
    public FileNode servicePidFile() {
        // Yes, that's the service pid (tomcat is a child process of the service)
        return shared().join("run/tomcat.pid");
    }

    //--

    /** @return vhost to docroot mapping, where vhost does *not* include the stage name */
    public abstract Map<String, FileNode> vhosts() throws IOException;

    public Map<String, FileNode> selectedVhosts() throws IOException {
        Map<String, FileNode> vhosts;
        Iterator<Map.Entry<String, FileNode>> iter;
        List<String> selected;
        String vhostname;

        vhosts = vhosts();
        selected = configuration.tomcatSelect;
        if (!selected.isEmpty()) {
            iter = vhosts.entrySet().iterator();
            while (iter.hasNext()) {
                vhostname = iter.next().getKey();
                if (!selected.contains(vhostname)) {
                    iter.remove();
                }
            }
        }
        return vhosts;
    }

    public Ports loadPortsOpt() throws IOException {
        return session.pool().stageOpt(getName());
    }

    /** @return empty list of no ports are allocated */
    public List<String> namedUrls() throws IOException {
        List<String> result;

        result = new ArrayList<>();
        for (Map.Entry<String, String> entry : urlMap().entrySet()) {
            result.add(entry.getKey() + " " + entry.getValue());
        }
        return result;
    }

    /** @return empty map of no ports are allocated */
    public Map<String, String> urlMap() throws IOException {
        Ports ports;

        ports = loadPortsOpt();
        return ports == null ? new HashMap<>()
                : ports.urlMap(!session.configuration.certificates.isEmpty(), session.configuration.vhosts, session.configuration.hostname,
                config().urls);
    }

    /** @return nummer of applications */
    public abstract int size() throws IOException;

    public abstract String getDefaultBuildCommand();

    protected FileNode catalinaHome() {
        return session.lib.join("tomcat", Start.tomcatName(configuration.tomcatVersion));
    }

    //-- tomcat helper

    public void start(Console console, Ports ports) throws Exception {
        ServerXml serverXml;
        int pid;
        KeyStore keystore;
        Extensions extensions;
        Launcher launcher;

        checkMemory();
        console.info.println("starting tomcat ...");

        // TODO workspace stages
        // FileNode editorLocations = directory.join("tomcat/editor/WEB-INF/editor-locations.xml");
        // if (editorLocations.exists()) {
        //    editorLocations.writeString(editorLocations.readString().replace("8080", Integer.toString(configuration.ports.tomcatHttp())));
        //    Files.stoolFile(editorLocations);
        // }

        serverXml = ServerXml.load(serverXmlTemplate(), session.configuration.hostname);
        keystore = keystore();
        extensions = extensions();
        serverXml.configure(ports, keystore, config().cookies, this);
        serverXml.save(serverXml());
        extensions.beforeStart(this);
        launcher = serviceWrapper("start");
        console.verbose.println("executing: " + launcher);
        launcher.exec(console.verbose);
        pid = runningService();
        if (pid == 0) {
            throw new IOException("tomcat startup failed - no pid file found");
        }
        console.info.println("Applications available:");
        for (String app : namedUrls()) {
            console.info.println("  " + app);
        }
    }

    /** Fails if Tomcat is not running */
    public void stop(Console console) throws IOException {
        console.info.println("stopping tomcat ...");
        if (runningService() == 0) {
            throw new IOException("tomcat is not running.");
        }
        extensions().beforeStop(this);
        serviceWrapper("stop").exec(console.verbose);
    }

    private Launcher serviceWrapper(String ... action) throws IOException {
        String owner;
        Launcher launcher;

        owner = owner();
        launcher = new Launcher(getDirectory());
        launcher.getBuilder().environment().clear();
        launcher.getBuilder().environment().putAll(configuration.tomcatEnv);
        launcher.getBuilder().environment().put("HOME", homeOf(owner).getAbsolute());
        launcher.getBuilder().environment().put("USER", owner);
        if (session.configuration.shared) {
            launcher.arg("sudo", "-u", owner, "-E");
        }
        launcher.arg(session.bin("service-wrapper.sh").getAbsolute());
        launcher.arg(catalinaHome().getAbsolute());
        launcher.arg(serviceWrapperBase().getAbsolute());
        launcher.arg(backstage.getAbsolute());
        launcher.arg(action);
        return launcher;
    }

    public FileNode serviceWrapperBase() {
        String platform;
        String name;

        platform = (OS.CURRENT == OS.LINUX) ? "linux-x86-64" : "macosx-universal-64";
        name = "wrapper-" + platform + "-" + config().tomcatService;
        return session.lib.join("service-wrapper", name);
    }


    // TODO: only works for most basic setup ...
    private FileNode homeOf(String user) throws IOException {
        FileNode result;

        if (OS.CURRENT == OS.MAC) {
            result = directory.getWorld().file("/Users");
        } else {
            result = directory.getWorld().file("/home");
        }
        result = result.join(user);
        result.checkDirectory();
        return result;
    }

    private void checkMemory() throws IOException {
        int requested;

        requested = configuration.tomcatHeap;
        int unreserved = session.memUnreserved();
        if (requested > unreserved) {
            throw new ArgumentException("Cannot reserve memory:\n"
              + "  unreserved: " + unreserved + "\n"
              + "  requested: " + requested + "\n"
              + "Consider stopping stages.");
        }
    }

    public FileNode catalinaBase() {
        return shared().join("tomcat");
    }

    public FileNode serverXml() {
        return catalinaBase().join("conf", "server.xml");
    }

    public FileNode serverXmlTemplate() {
        return catalinaBase().join("conf", "server.xml.template");
    }

    //--

    public void move(FileNode newDirectory) throws IOException {
        FileNode link;

        link = session.backstageLink(getName());
        link.deleteTree();
        directory.move(newDirectory);
        directory = newDirectory;
        backstageDirectory(directory).link(link);
    }

    //--

    @Override
    public String toString() {
        return getType() + " " + url;
    }

    //-- util

    public void checkNotUp() throws IOException {
        if (state() == State.UP) {
            throw new IOException("stage is not stopped.");
        }
    }

    public void checkOwnership() throws IOException, OwnershipException {
        if (!owner().equals(session.user)) {
            throw new OwnershipException("Only the owner of the stage is allowed to to do this.\n"
              + "Just own the stage via 'stool chown' and try again.");
        }
    }


    public Launcher launcher(String... command) {
        return launcher(directory, command);
    }

    public Launcher launcher(FileNode working, String... command) {
        Launcher launcher;

        launcher = new Launcher(working, command);
        session.environment(this).save(launcher);
        return launcher;
    }

    public abstract boolean refreshPending(Console console) throws IOException;

    public void restoreFromBackup(Console console) throws IOException {
        console.info.println("Nothing to restore.");
    }

    public void executeRefresh(Console console) throws IOException {
        launcher(Strings.toArray(Separator.SPACE.split(macros().replace(config().refresh)))).exec(console.info);
    }

    //--

    public void tuneConfiguration() throws IOException {
        int size;

        size = size();
        if (configuration.tomcatHeap == 0 || configuration.tomcatHeap == 200) {
            configuration.tomcatHeap = Math.min(4096, 150 + size * session.configuration.baseHeap);
        }
        if (configuration.build.isEmpty() || configuration.build.equals("false")) {
            configuration.build = getDefaultBuildCommand();
        }

        if (session.configuration.shared) {
            configuration.expire = Expire.withOffset(8);
        } else {
            configuration.expire = Expire.never();
        }

    }

    public void initialize() throws IOException {
        Files.createStoolDirectory(session.console.verbose, shared());
        // important: this is the last step in stage creation; creating this file indicates that the stage is ready
        session.saveStageProperties(configuration, backstage);
    }

    //--

    public void setMaven(Maven maven) {
        this.lazyMaven = maven;
    }

    /** CAUTION: this is not a session method, because it respected the stage repository */
    public Maven maven() throws IOException {
        World world;
        String mavenHome;
        FileNode settings;

        if (lazyMaven == null) {
            world = session.world;
            mavenHome = config().mavenHome();
            if (mavenHome == null) {
                settings = session.lib.join("maven-settings.xml");
            } else {
                settings = world.file(mavenHome).join("conf/settings.xml");
            }
            // CAUTION: shared plexus - otherwise, Maven components are created over and over again
            lazyMaven = Maven.withSettings(world, localRepository(), settings, null, session.plexus(), null, null);
            // always get the latest snapshots
            lazyMaven.getRepositorySession().setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        }
        return lazyMaven;
    }

    protected List<MavenProject> loadWars(FileNode rootPom) throws IOException {
        List<MavenProject> wars;
        List<String> profiles;
        Properties userProperties;

        wars = new ArrayList<>();
        profiles = new ArrayList<>();
        userProperties = new Properties();
        addProfilesAndProperties(userProperties, profiles, configuration.mavenOpts);
        addProfilesAndProperties(userProperties, profiles, getBuild());
        session.console.verbose.println("profiles: " + profiles);
        session.console.verbose.println("userProperties: " + userProperties);
        warProjects(rootPom, userProperties, profiles, wars);
        if (wars.size() == 0) {
            throw new IOException("no war projects");
        }
        return wars;
    }

    public String getBuild() {
        return macros().replace(configuration.build);
    }

    private Macros lazyMacros = null;

    public Macros macros() {
        if (lazyMacros == null) {
            lazyMacros = new Macros();
            lazyMacros.addAll(session.configuration.macros);
            lazyMacros.add("directory", getDirectory().getAbsolute());
            lazyMacros.add("localRepository", localRepository().getAbsolute());
            lazyMacros.add("svnCredentials", Separator.SPACE.join(session.svnCredentials().svnArguments()));
            lazyMacros.add("stoolSvnCredentials", session.svnCredentials().stoolSvnArguments());
        }
        return lazyMacros;
    }

    public boolean isCommitted() throws IOException {
        return session.scm(getUrl()).isCommitted(this);
    }

    private void addProfilesAndProperties(Properties userProperties, List<String> profiles, String args) {
        int idx;

        for (String part : Separator.SPACE.split(args)) {
            if (part.startsWith("-P")) {
                profiles.add(part.substring(2));
            }
            if (part.startsWith("-D")) {
                part = part.substring(2);
                idx = part.indexOf('=');
                if (idx == -1) {
                    userProperties.put(part, "");
                } else {
                    userProperties.put(part.substring(0, idx), part.substring(idx + 1));
                }
            }
        }
    }
    private void warProjects(FileNode pomXml, Properties userProperties, List<String> profiles,
      List<MavenProject> result) throws IOException {
        MavenProject root;
        FileNode modulePom;

        try {
            root = maven().loadPom(pomXml, false, userProperties, profiles, null);
        } catch (ProjectBuildingException | RepositoryException e) {
            throw new IOException("cannot parse " + pomXml + ": " + e.getMessage(), e);
        }
        session.console.verbose.println("loading " + pomXml);
        if ("war".equals(root.getPackaging())) {
            result.add(root);
        } else {
            for (String module : root.getModules()) {
                modulePom = session.world.file(root.getBasedir()).join(module);
                if (modulePom.isDirectory()) {
                    modulePom = modulePom.join("pom.xml");
                }
                warProjects(modulePom, userProperties, profiles, result);
            }
        }
    }

    public boolean isSystem() {
        return session.lib.equals(directory.getParent());
    }

    public Changes changes() {
        return new Changes();
    }
    public FileNode localRepository() {
        return session.configuration.shared ? backstage.join(".m2") : session.world.getHome().join(".m2/repository");
    }

    public Logs logs() {
        return new Logs(shared().join("tomcat/logs"));
    }

    public String uptime() throws GetLastModifiedException {
        FileNode file;
        long diff;
        StringBuilder result;
        long hours;

        file = servicePidFile();
        if (!file.exists()) {
            return "";
        }
        diff = System.currentTimeMillis() - file.getLastModified();
        diff /= 1000;
        hours = diff / 3600;
        if (hours >= 48) {
            return (hours / 24) + " days";
        } else {
            result = new StringBuilder();
            new Formatter(result).format("%d:%02d:%02d", hours, diff % 3600 / 60, diff % 60);
            return result.toString();
        }
    }

    public int diskUsed() throws IOException {
        return used(directory);
    }

    /** @return megabytes */
    private static int used(FileNode dir) throws IOException {
        String str;

        str = dir.exec("du", "-s", "-k", ".");
        str = str.trim();
        str = Strings.removeRight(str, ".");
        str = str.trim();
        return (Integer.parseInt(str) + 512) / 1024;
    }

    public enum State {
        DOWN, SLEEPING, UP, WORKING;

        public String toString() {
            return name().toLowerCase();
        }
    }

    //--

    /** @return pid or null */
    private static int readPidOpt(FileNode file) throws IOException {
        return file.exists() ? Integer.parseInt(file.readString().trim()) : 0;
    }

    //-- stage name

    /**
     * The stage name has to be a valid domain name because is used as part of the application url.
     * See http://tools.ietf.org/html/rfc1035 section 2.3.1.
     */
    public static void checkName(String name) {
        char c;

        if (name.isEmpty()) {
            throw new ArgumentException("empty stage name is not allowed");
        }
        if (name.length() > 30) {
            //ITCA does not accept too long commonNames
            throw new ArgumentException("Stage Name is too long. Please take a shorter one.");
        }
        if (!isLetter(name.charAt(0))) {
            throw new ArgumentException("stage name does not start with a letter");
        }
        for (int i = 1; i < name.length(); i++) {
            c = name.charAt(i);
            if (!isValidStageNameChar(c)) {
                throw new ArgumentException("stage name contains illegal character: " + c);
            }
        }
    }

    public static boolean isValidStageNameChar(char c) {
        return isLetter(c) || isDigit(c) || c == '-' || c == '.';
    }
    // cannot use Character.is... because we check ascii only
    private static boolean isLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }
    // cannot use Character.is... because we check ascii only
    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    public static String nameForUrl(String url) {
        if (url.startsWith("gav:")) {
            return nameForGavUrl(url);
        } else if (url.startsWith("file:")) {
            return nameForFileUrl(url);
        } else {
            return nameForSvnOrGitUrl(url);
        }
    }

    private static String nameForGavUrl(String url) {
        int end;
        int start;

        url = one(url);
        end = url.lastIndexOf(':');
        if (end == -1) {
            return "stage";
        }
        start = url.lastIndexOf(':', end - 1);
        if (start == -1) {
            return "stage";
        }
        return url.substring(start + 1, end);
    }

    private static String nameForFileUrl(String url) {
        int idx;

        url = one(url);
        idx = url.lastIndexOf('/');
        if (idx == -1) {
            return "idx";
        }
        url = url.substring(idx + 1);
        idx = url.lastIndexOf('.');
        if (idx == -1) {
            return url;
        } else {
            return url.substring(0, idx);
        }
    }

    private static String one(String url) {
        int end;

        end = url.lastIndexOf(',');
        if (end != -1) {
            url = url.substring(0, end);
        }
        end = url.lastIndexOf('=');
        if (end != -1) {
            url = url.substring(0, end);
        }
        return url;
    }

    private static String nameForSvnOrGitUrl(String url) {
        String result;
        int idx;

        result = Strings.removeRightOpt(url, "/");
        idx = result.indexOf(':');
        if (idx != -1) {
            // strip protocol - important vor gav stages
            result = result.substring(idx + 1);
        }
        result = Strings.removeRightOpt(result, "/trunk");
        idx = result.lastIndexOf('/');
        result = result.substring(idx + 1); // ok for -1
        result = Strings.removeRightOpt(result, ".git");
        return result.isEmpty() ? "stage" : result;
    }

    //--

    public Extensions extensions() {
        return configuration.extensions;
    }

    //--

    public void checkConstraints() throws IOException {
        int used;
        int quota;

        if (config().expire.isExpired()) {
            throw new ArgumentException("Stage expired " + config().expire + ". To start it, you have to adjust the 'expire' date.");
        }
        quota = config().quota;
        if (quota != 0) {
            used = diskUsed();
            if (used > quota) {
                throw new ArgumentException("Stage quota exceeded. Used: " + used + " mb  >  quota: " + quota + " mb.\n" +
                        "Consider running 'stool cleanup'.");
            }
        }
    }
}
