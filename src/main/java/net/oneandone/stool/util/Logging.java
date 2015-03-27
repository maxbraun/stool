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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.MultiOutputStream;
import net.oneandone.sushi.io.PrefixWriter;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logging {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyMMdd-");

    public static Logging forHome(FileNode home, String user) throws IOException {
        String today;
        String id;
        Logging result;

        today = DATE_FORMAT.format(new Date());
        id = Integer.toString(id(home.join("logs"), today));
        result = new Logging(id, home.join("logs/stool.log"), user);
        result.configureRootLogger();
        return result;
    }

    public final String id;
    private final LoggerContext context;
    private final FileNode stool;
    private final String user;

    public Logging(String id, FileNode stool, String user) {
        this.id = id;
        this.context = (LoggerContext) LoggerFactory.getILoggerFactory();
        this.stool = stool;
        this.user = user;
        setStage("", "");
    }

    public void setStage(String id, String name) {
        context.putProperty("stageId", id);
        context.putProperty("stageName", name);
    }

    public void configureRootLogger() throws IOException {
        Logger root;

        // adjust the default configuration
        root = context.getLogger("ROOT");
        root.detachAndStopAllAppenders();
        root.addAppender(stoolAppender("OTHER"));
        root.setLevel(Level.INFO);
    }

    public PrintWriter writer(OutputStream stream, String logger) throws IOException {
        PrintWriter result;

        result = new PrintWriter(MultiOutputStream.createTeeStream(stream, new Slf4jOutputStream(logger(logger), false)), true);
        // empty prefix is replaced by stage commands when iterating multiple stages:
        result = new PrefixWriter(result);
        return result;
    }

    public Logger logger(String name) throws IOException {
        Logger result;

        result = context.getLogger(name);
        // important for test cases, where you might instantiate multiple Logging objects
        result.detachAndStopAllAppenders();
        result.setAdditive(false);
        result.setLevel(Level.INFO);
        result.addAppender(stoolAppender(name));
        return result;
    }

    private RollingFileAppender stoolAppender(String logger) throws IOException {
        RollingFileAppender result;
        TimeBasedRollingPolicy policy;

        result = new RollingFileAppender();
        result.setContext(context);
        result.setName("stoolAppender");
        result.setEncoder(encoder(logger));
        result.setAppend(true);
        result.setFile(stool.getAbsolute());

        policy = new TimeBasedRollingPolicy();
        policy.setContext(context);
        policy.setParent(result);
        policy.setFileNamePattern(stool.getParent().getAbsolute() + "/stool-%d{yyyy-MM-dd}.log.gz");
        policy.setMaxHistory(7);
        policy.start();

        result.setRollingPolicy(policy);
        result.start();

        if (!stool.exists()) {
            stool.writeBytes();
            Files.stoolFile(stool);
        }
        return result;
    }

    private PatternLayoutEncoder encoder(String logger) {
        PatternLayoutEncoder encoder;

        encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        // note that msg is not excaped, it may contain | characters
        encoder.setPattern("%date|" + id + "|" + logger + "|" + user + "|%property{stageId}|%property{stageName}|%msg%n");
        encoder.start();
        return encoder;
    }


    //--

    /**
     * Unique id starting with 1 every day, bumped for every invocation.
     */
    private static int id(FileNode varRun, String prefix) throws IOException {
        int retries;
        FileNode lock;
        FileNode file;
        int id;
        String str;

        retries = 0;
        while (true) {
            lock = varRun.join("id.lock");
            try {
                lock.mkfile();
                break;
            } catch (IOException e) {
                retries++;
                if (retries > 10) {
                    throw new IOException("cannot create " + lock);
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e1) {
                    break;
                }
            }
        }
        try {
            file = varRun.join("id");
            if (!file.exists()) {
                id = 1;
                touch(file);
            } else {
                str = file.readString();
                if (str.startsWith(prefix)) {
                    id = Integer.parseInt(str.substring(prefix.length())) + 1;
                } else {
                    id = 1;
                }
            }
            file.writeString(prefix + id);
            return id;
        } finally {
            lock.deleteFile();
        }
    }

    private static FileNode touch(FileNode file) throws IOException {
        if (!file.exists()) {
            file.mkfile();
            file.setPermissions("rw-rw----");
        }
        return file;
    }
}
