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
package net.oneandone.stool.util;

import net.oneandone.sushi.fs.World;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class ProcessesTest {
    private static final World WORLD = World.createMinimal();

    @Test
    public void tomcat() throws IOException {
        Processes p;

        p = Processes.create(WORLD.guessProjectHome(getClass()).join("src/test/psoutput").readString());
        assertEquals(false, p.hasPid(4));
        assertEquals(true, p.hasPid(5));
        assertEquals(21785, p.servicePid(WORLD.file("/opt/ui/opt/tools/stool/backstages/maria-snapshots-new")));
        assertEquals(21787, p.oneChildOpt(21785));
    }
}
