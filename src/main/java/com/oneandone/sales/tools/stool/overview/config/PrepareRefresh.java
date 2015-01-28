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
package com.oneandone.sales.tools.stool.overview.config;

import com.oneandone.sales.tools.stool.Chown;
import com.oneandone.sales.tools.stool.Refresh;
import com.oneandone.sales.tools.stool.overview.StageGatherer;
import com.oneandone.sales.tools.stool.overview.StoolCallable;
import com.oneandone.sales.tools.stool.stage.Stage;
import com.oneandone.sales.tools.stool.util.Session;
import com.oneandone.sales.tools.stool.util.Slf4jOutputStream;

import java.io.PrintWriter;
import java.util.List;
import java.util.TimerTask;
import java.util.UUID;

public class PrepareRefresh extends TimerTask {
    private final Session session;
    private final PrintWriter printWriter;
    public PrepareRefresh(Session session) {
        this.session = session;
        this.printWriter = new PrintWriter(new Slf4jOutputStream(org.slf4j.LoggerFactory.getLogger(PrepareRefresh.class), false));
    }

    @Override
    public void run() {
        Refresh refresh;
        Chown chown;
        try {
            List<Stage> allStages = StageGatherer.getAllStages(session);
            refresh = new Refresh(session, true, true, false);
            chown = new Chown(session, true, true);
            for (Stage stage : allStages) {
                if (stage.config().autoRefresh) {
                    if (!stage.technicalOwner().equals(session.whoAmI())) {
                        chown.doInvoke(stage);
                    }
                    refresh.prepare(stage);
                    if (stage.updateAvailable()) {
                        executeUpdate(stage);
                    }
                }
            }
        } catch (Exception e) {
            printWriter.println("Error while preparing refresh " + e.getCause());
            e.printStackTrace();
        }
    }

    public void executeUpdate(Stage stage) throws Exception {
        boolean own;
        own = !stage.technicalOwner().equals(session.whoAmI());
        new StoolCallable("refresh", "-usePrepared", stage.getName(), "daemon", UUID.randomUUID().toString(),
          session.home.join("logs", "overview"), own).call();
    }

}
