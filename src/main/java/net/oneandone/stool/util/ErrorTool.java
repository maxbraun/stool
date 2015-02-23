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
import net.oneandone.stool.setup.Main;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class ErrorTool {
    public static void send(URL url, Level level, String hostname, String subject, Exception e) throws IOException {
        StringWriter dest;

        dest = new StringWriter();
        e.printStackTrace(new PrintWriter(dest));
        send(url, level, hostname, subject, dest.getBuffer().toString());
    }

    public static void send(URL url, Level level, String hostname, String subject, String body) throws IOException {
        StringBuilder dest;
        HttpURLConnection con;

        dest = new StringBuilder();

        add("mt", "httpParams", dest);

        add("product", "stool", dest);
        add("category", "default", dest);
        add("container", hostname, dest);
        add("sessionId", Main.versionObject().toString(), dest);

        add("subject", subject, dest);
        add("body", body, dest);
        add("occurTime", Long.toString(System.currentTimeMillis()), dest);
        add("level", level.toString(), dest);

        byte[] formData = dest.toString().getBytes("ascii");

        con = (HttpURLConnection) url.openConnection();
        con.setReadTimeout(250);
        con.setConnectTimeout(250);
        con.setDoOutput(true);
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        con.setFixedLengthStreamingMode(formData.length);
        con.setRequestProperty("User-Agent", "com.oneandone.sales.applog.errortoollog4j.JavaNetErrortoolAppender-0.0.1");
        con.getOutputStream().write(formData);
        con.getResponseCode();
        con.disconnect();
    }

    private static void add(String parameter, String value, StringBuilder dest) {
        if (value == null) {
            return;
        }
        if (dest.length() > 0) {
            dest.append('&');
        }
        try {
            dest.append(URLEncoder.encode(parameter, "utf8"));
            dest.append('=');
            dest.append(URLEncoder.encode(value, "utf8"));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}