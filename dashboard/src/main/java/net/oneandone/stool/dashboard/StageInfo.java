/*
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
package net.oneandone.stool.dashboard;


import net.oneandone.stool.configuration.Expire;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.stage.artifact.Changes;
import net.oneandone.stool.users.UserNotFound;
import net.oneandone.stool.users.Users;
import net.oneandone.sushi.fs.file.FileNode;

import javax.naming.NamingException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

public class StageInfo {
    private String name;
    private StageConfiguration configuration;
    private String extractionUrl;
    /** Maps name to actual url */
    private Map<String, String> urls;
    private Stage.State running;
    private String lastModifiedBy;
    private boolean updateAvailable;
    private Expire expire;
    private Changes changes;
    private BuildStats stats;
    private String category;
    private String state;

    public static StageInfo fromStage(FileNode logDir, Stage stage, Users users) throws IOException, NamingException {
        StageInfo stageInfo;
        Changes changes;

        stageInfo = new StageInfo();
        stageInfo.name = stage.getName();
        stageInfo.configuration = stage.config();
        stageInfo.extractionUrl = stage.getUrl();
        stageInfo.running = stage.isWorking() ? Stage.State.WORKING : stage.state();
        stageInfo.urls = stage.urlMap();
        try {
            stageInfo.lastModifiedBy = users.byLogin(stage.lastModifiedBy()).name;
        } catch (UserNotFound userNotFound) {
            stageInfo.lastModifiedBy = "unknown: " + stage.lastModifiedBy();
        }
        stageInfo.updateAvailable = stage.updateAvailable();
        stageInfo.expire = stage.config().expire;

        changes = stage.changes();
        if (changes.size() > 0) {
            stageInfo.changes = changes;
        }
        stageInfo.stats = BuildStats.load(logDir, stage);
        if (stageInfo.extractionUrl.contains("/trunk")) {
            stageInfo.category = "trunk";
        } else if (stageInfo.extractionUrl.contains("/branches")) {
            stageInfo.category = "branches";
        } else if (stageInfo.extractionUrl.contains("/workspaces")) { // TODO
            stageInfo.category = "workspaces";
        }

        switch (stageInfo.running) {
            case UP:
                stageInfo.state = "success";
                break;
            case WORKING:
                stageInfo.state = "primary";
                break;
            default:
                stageInfo.state = "danger";
        }

        return stageInfo;
    }


    public Expire getExpire() {
        return expire;
    }
    public Changes getChanges() {
        return changes;
    }
    public BuildStats getStats() {
        return stats;
    }
    public String getCategory() {
        return category;
    }
    public String getState() {
        return state;
    }
    public String getName() {
        return name;
    }

    public StageConfiguration getConfiguration() {
        return configuration;
    }

    public String getExtractionUrl() {
        return extractionUrl;
    }

    public Map<String, String> getUrls() {
        return urls;
    }

    public String getRunning() {
        return running.name().toLowerCase();
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public String getShareText() throws UnsupportedEncodingException {
        if (urls == null) {
            return "";
        }
        String content;
        StringBuilder stringBuilder;
        stringBuilder = new StringBuilder("Hi, \n");
        for (String url : urls.values()) {
            stringBuilder.append(url).append("\n");
        }

        content = URLEncoder.encode(stringBuilder.toString(), "UTF-8");
        content = content.replace("+", "%20").replaceAll("\\+", "%20")
          .replaceAll("\\%21", "!")
          .replaceAll("\\%27", "'")
          .replaceAll("\\%28", "(")
          .replaceAll("\\%29", ")")
          .replaceAll("\\%7E", "~");

        return content;
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getHash() {
        return "" + this.toString().hashCode();
    }

    @Override
    public String toString() {
        return "StageInfo{"
          + "name='" + name + '\''
          + ", extractionUrl='" + extractionUrl + '\''
          + ", urls=" + urls
          + ", changes=" + changes
          + ", running=" + running
          + ", last-modified-by='" + lastModifiedBy + '\''
          + ", updateAvailable=" + updateAvailable
          + ", category='" + category + '\''
          + ", state='" + state + '\''
          + '}';
    }
}
