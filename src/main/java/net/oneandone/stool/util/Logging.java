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
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.encoder.EncoderBase;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.MultiOutputStream;
import net.oneandone.sushi.io.PrefixWriter;
import net.oneandone.sushi.util.Strings;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Logging {
    private static final String EXTENSION = ".log";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd-");

    public static Logging forStool(FileNode home, String user) throws IOException {
        return create(home.join("logs"), "stool", user);
    }

    public static Logging create(FileNode dir, String name, String user) throws IOException {
        String today;
        String id;
        Logging result;

        today = DATE_FORMAT.format(LocalDate.now());
        id = today + Integer.toString(id(dir, today));
        result = new Logging(id, dir.join(name + EXTENSION), user);
        result.configureRootLogger();
        return result;
    }

    public final String id;
    private final LoggerContext context;
    private final FileNode file;
    private final String user;

    private String stageId;
    private String stageName;

    public Logging(String id, FileNode file, String user) throws IOException {
        this.id = id;
        this.context = (LoggerContext) LoggerFactory.getILoggerFactory();
        this.file = file;
        this.user = user;
        setStage("", "");
        if (!file.exists()) {
            file.writeBytes();
            Files.stoolFile(file);
        }
    }

    public void setStage(String id, String name) {
        stageId = id;
        stageName = name;
    }

    public void configureRootLogger() throws IOException {
        Logger root;

        // adjust the default configuration
        root = context.getLogger("ROOT");
        root.detachAndStopAllAppenders();
        root.addAppender(appender("OTHER"));
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
        result.addAppender(appender(name));
        return result;
    }

    private RollingFileAppender appender(String logger) {
        RollingFileAppender result;
        TimeBasedRollingPolicy policy;

        result = new RollingFileAppender() {
            @Deprecated
            public void rollover() {
                super.rollover();
                try {
                    if (!file.exists()) {
                        file.mkfile();
                    }
                    // Make sure the file is always group-writable, because all users share the same log file
                    // (The archived log is not a problems, because it's written exactly one, all later access is reading)
                    Files.stoolFile(file);
                } catch (IOException e) {
                    throw new RuntimeException("TODO", e);
                }
            }
        };
        result.setContext(context);
        result.setName(logger + "-appender");
        result.setEncoder(encoder(logger));
        result.setAppend(true);
        result.setFile(file.getAbsolute());

        policy = new TimeBasedRollingPolicy();
        policy.setContext(context);
        policy.setParent(result);
        policy.setFileNamePattern(file.getParent().getAbsolute() + "/" + Strings.removeRightOpt(file.getName(), EXTENSION)
                + "-%d{yyyy-MM-dd}.log.gz");
        policy.setMaxHistory(180);
        policy.start();

        result.setRollingPolicy(policy);
        result.start();

        return result;
    }

    private Encoder<ILoggingEvent> encoder(final String logger) {
        return new EncoderBase<ILoggingEvent>() {
            private PrintWriter writer;

            @Override
            public void init(OutputStream out) {
                writer = new PrintWriter(out);
            }

            @Override
            public void doEncode(ILoggingEvent event) throws IOException {
                String message;
                char c;

                writer.append(LogEntry.FMT.format(LocalDateTime.now())).append('|');
                writer.append(id).append('|');
                writer.append(logger).append('|');
                writer.append(user).append('|');
                writer.append(stageId).append('|');
                writer.append(stageName).append('|');
                message = event.getFormattedMessage();
                for (int i = 0, max = message.length(); i < max; i++) {
                    c = message.charAt(i);
                    switch (c) {
                        case '\r':
                            writer.append("\\r");
                            break;
                        case '\n':
                            writer.append("\\n");
                            break;
                        case '\\':
                            writer.append("\\\\");
                            break;
                        default:
                            writer.append(c);
                            break;
                    }
                }
                writer.append('\n');
                writer.flush();
            }

            @Override
            public void close() throws IOException {
                if (writer != null) {
                    writer.close();
                }
            }
        };
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

    public FileNode directory() {
        return file.getParent();
    }

    /** @return alle COMMAND Log entries operating on the specified stage */
    public List<LogEntry> stageCommands(String stageId) throws Exception {
        LogEntry entry;
        Map<String, LogEntry> commands;
        LogEntry command;
        List<LogEntry> result;

        result = new ArrayList<>();
        commands = new HashMap<>();
        try (LogReader reader = LogReader.create(directory())) {
            while (true) {
                entry = reader.next();
                if (entry == null) {
                    break;
                }
                if (entry.logger.equals("COMMAND")) {
                    if (commands.put(entry.id, entry) != null) {
                        throw new IllegalStateException("duplicate id: " + entry.id);
                    }
                }
                if (entry.stageId.equals(stageId)) {
                    command = commands.remove(entry.id);
                    if (command != null) {
                        result.add(command);
                    }
                }
            }
        }
        return result;
    }
    public List<LogEntry> info(String stageId, String id) throws Exception {
        LogEntry entry;
        List<LogEntry> result;

        result = new ArrayList<>();
        try (LogReader reader = LogReader.create(directory())) {
            while (true) {
                entry = reader.next();
                if (entry == null) {
                    break;
                }
                if (entry.id.equals(id) && entry.stageId.equals(stageId)) {
                    result.add(entry);
                }
            }
        }
        return result;
    }
}
