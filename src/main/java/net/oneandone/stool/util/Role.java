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

import net.oneandone.stool.configuration.StoolConfiguration;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;

public enum Role {
    ADMIN(),
    USER();

    public static final String ERROR = "You do not have the permissions to do that.";


    public static boolean isAdmin(StoolConfiguration stoolConfiguration) {
        return detect(stoolConfiguration) == ADMIN;

    }

    public static Role detect(StoolConfiguration stoolConfiguration) {
        if (!stoolConfiguration.security.isLocal() && !isInGroup(stoolConfiguration.adminGroup)) {
            return USER;
        }
        return ADMIN;
    }

    private static boolean isInGroup(String expectedGroup) {
        try {

            String groups;
            groups = new Launcher("groups").dir(new World().file("/")).exec();
            for (String group : groups.trim().split(" ")) {
                if (group.equals(expectedGroup)) {
                    return true;
                }
            }
            return false;

        } catch (Failure failure) {
            throw new RuntimeException(failure);
        }
    }

}
