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
package net.oneandone.stool.cli;

import net.oneandone.stool.configuration.Property;
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.users.UserNotFound;
import net.oneandone.stool.util.Field;
import net.oneandone.stool.util.Info;
import net.oneandone.stool.util.LogEntry;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.Processes;
import net.oneandone.stool.util.Session;
import net.oneandone.stool.util.Vhost;
import net.oneandone.sushi.util.Separator;

import javax.naming.NamingException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class InfoCommand extends StageCommand {

    protected final List<Info> selected = new ArrayList<>();

    private final String defaults;

    public InfoCommand(Session session, String defaults) {
        super(false, session, Mode.SHARED, Mode.SHARED,
                Mode.NONE
                /* this is not 100% accurate, but it help to avoid annoging lock-waits:
                1) diskused might be work in progress
                2) buildtime might inaccurate */);
        this.defaults = defaults;
    }

    public void field(String str) {
        selected.add(get(str));
    }

    private Info get(String str) {
        return Info.get(session.properties(), str);
    }

    protected List<Info> defaults(Info ... systemDefaults) {
        List<Info> result;

        if (defaults.isEmpty()) {
            return Arrays.asList(systemDefaults);
        }
        result = new ArrayList<>();
        for (String name : Separator.COMMA.split(defaults)) {
            result.add(get(name));
        }
        return result;
    }

    //--

    public static String toString(Object value) {
        boolean first;
        List<Object> lst;
        StringBuilder builder;

        if (value == null) {
            return "";
        } else if (value instanceof Future) {
            try {
                return toString(((Future) value).get());
            } catch (IOException e) {
                return "[error: " + e.getMessage() + "]";
            }
        } else if (value instanceof List) {
            first = true;
            lst = (List) value;
            builder = new StringBuilder();
            for (Object item : lst) {
                if (first) {
                    first = false;
                } else {
                    builder.append('\t');
                }
                builder.append(toString(item));
            }
            return builder.toString();
        } else {
            return value.toString();
        }
    }

    public static Map<Info, Object> status(Session session, Processes processes, Stage stage) throws IOException {
        Map<Info, Object> result;
        Ports ports;
        List<String> jmx;
        String url;
        Stage.State state;

        result = new HashMap<>();
        result.put(Field.ID, stage.getId());
        result.put(Field.SELECTED, session.isSelected(stage));
        result.put(Field.DIRECTORY, stage.getDirectory().getAbsolute());
        result.put(Field.BACKSTAGE, stage.backstage.getAbsolute());
        result.put(Field.DISK, new Future<Integer>() {
            @Override
            protected Integer doGet() throws IOException {
                return stage.diskUsed();
            }
        });
        result.put(Field.URL, stage.getUrl());
        result.put(Field.TYPE, stage.getType());
        result.put(Field.BUILDTIME, new Future<String>() {
            @Override
            protected String doGet() throws IOException {
                return stage.buildtime();
            }
        });
        result.put(Field.CREATOR, userName(session, stage.creator()));
        result.put(Field.CREATED, LogEntry.FULL_FMT.format(stage.created()));
        result.put(Field.MAINTAINER, userName(session, stage.maintainer()));
        result.put(Field.MAINTAINED, Stage.timespan(stage.maintained()));
        result.put(Field.UPTIME, stage.uptime());
        state = stage.state();
        result.put(Field.STATE, state.toString());
        ports = processStatus(state, processes, stage, result);
        result.put(Field.APPS, stage.namedUrls());
        result.put(Field.OTHER, other(stage, ports));
        jmx = new ArrayList<>();
        if (ports != null) {
            url = stage.session.configuration.hostname + ":" + ports.jmx();
            jmx.add("jconsole " + url);
            jmx.add("jvisualvm --openjmx " + url);
        }
        result.put(Field.JMX, jmx);
        for (Property property: session.properties().values()) {
            result.put(property, property.get(stage.config()));
        }
        return result;
    }

    private static String userName(Session session, String login) {
        try {
            return session.users.byLogin(login).toStatus();
        } catch (NamingException | UserNotFound e) {
            return "[error: " + e.getMessage() + "]";
        }
    }

    /** TODO: we need this field to list fitnesse urls ...*/
    private static List<String> other(Stage stage, Ports ports) {
        List<String> result;

        result = new ArrayList<>();
        if (ports != null) {
            for (Vhost vhost : ports.vhosts()) {
                if (vhost.isWebapp()) {
                    continue;
                }
                if (vhost.name.contains("+")) {
                    continue;
                }
                result.add(vhost.httpUrl(stage.session.configuration.vhosts, stage.session.configuration.hostname));
            }
        }
        return result;
    }

    public static Ports processStatus(Stage.State state, Processes processes, Stage stage, Map<Info, Object> result) throws IOException {
        int servicePid;
        int tomcatPid;
        String debug;
        boolean suspend;
        Ports ports;
        String config;
        Double cpu;
        Double mem;

        servicePid = stage.runningService();
        if (servicePid != 0) {
            tomcatPid = processes.oneChildOpt(servicePid);
            cpu = processes.lookup(tomcatPid).cpu;
            mem = processes.lookup(tomcatPid).mem;
            ports = stage.loadPortsOpt();
            if (ports == null) {
                debug = null;
                suspend = false;
            } else {
                config = stage.getBackstage().join("service/service-wrapper.conf").readString();
                if (config.contains("=-Xdebug\n")) {
                    debug = Integer.toString(ports.debug());
                } else {
                    debug = null;
                }
                suspend = debug != null && config.contains(",suspend=y");
            }
        } else {
            tomcatPid = 0;
            cpu = null;
            mem = null;
            ports = null;
            debug = null;
            suspend = false;
        }
        result.put(Field.CPU, cpu);
        result.put(Field.MEM, mem);
        result.put(Field.SERVICE, opt(servicePid));
        result.put(Field.TOMCAT, opt(tomcatPid));
        result.put(Field.DEBUGGER, debug);
        result.put(Field.SUSPEND, suspend);
        result.put(Field.FITNESSE, state == Stage.State.UP && servicePid == 0);
        return ports;
    }

    private static Integer opt(int i) {
        return i == 0 ? null : i;
    }

    public abstract static class Future<T> {
        private T lazy = null;
        public T get() throws IOException {
            if (lazy == null) {
                lazy = doGet();
            }
            return lazy;
        }
        protected abstract T doGet() throws IOException;
    }
}
