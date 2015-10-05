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
package net.oneandone.stool;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SystemImportTest {
    @Test
    public void mergeMissing() {
        String result;

        result = SystemImport.mergeConfig(
                "{\n  \"version\": \"3.0.0-SNAPSHOT\"\n}\n",
                "{\n  \"version\": \"3.0.0-SNAPSHOT\",\n  \"stages\": \"/Users/mhm\"\n}",
                new Object() {});
        assertEquals("{\n  \"version\": \"3.0.0-SNAPSHOT\",\n  \"stages\": \"/Users/mhm\"\n}", result);
    }

    @Test
    public void mergeValue() {
        String result;

        result = SystemImport.mergeConfig(
                "{\n  \"version\": \"3.0.1-SNAPSHOT\",\n  \"stages\": \"/Users/mhm\"\n}\n",
                "{\n  \"version\": \"3.0.0-SNAPSHOT\",\n  \"stages\": \"/Users/mhm\"\n}",
                new Object() {});
        assertEquals("{\n  \"version\": \"3.0.1-SNAPSHOT\",\n  \"stages\": \"/Users/mhm\"\n}", result);
    }

    @Test
    public void mergeRemove() {
        String result;

        result = SystemImport.mergeConfig(
                "{\n  \"version\": \"3.0.1-SNAPSHOT\",\n  \"stages\": \"/Users/mhm\",\n  \"additional\": \"foo\"\n}\n",
                "{\n  \"version\": \"3.0.0-SNAPSHOT\",\n  \"stages\": \"/Users/mhm\"\n}",
                new Object() {
                    void additionalRemove() {}
                });
        assertEquals("{\n  \"version\": \"3.0.1-SNAPSHOT\",\n  \"stages\": \"/Users/mhm\"\n}", result);
    }

    @Test
    public void mergeRename() {
        String result;

        result = SystemImport.mergeConfig(
                "{\n  \"version\": \"3.0.1-SNAPSHOT\",\n  \"before\": \"/Users/mhm\"\n}\n",
                "{\n  \"version\": \"3.0.0-SNAPSHOT\"\n}",
                new Object() {
                    String beforeRename() { return "after"; }
                });
        assertEquals("{\n  \"version\": \"3.0.1-SNAPSHOT\",\n  \"after\": \"/Users/mhm\"\n}", result);
    }

    @Test
    public void mergeTransform() {
        String result;

        result = SystemImport.mergeConfig(
                "{\n  \"version\": \"3.0.1-SNAPSHOT\",\n  \"stages\": 5\n}\n",
                "{\n  \"version\": \"3.0.0-SNAPSHOT\"\n}",
                new Object() {
                    JsonElement stagesTransform(JsonElement orig) { return new JsonPrimitive(orig.getAsInt() * 2); }
                });
        assertEquals("{\n  \"version\": \"3.0.1-SNAPSHOT\",\n  \"stages\": 10\n}", result);
    }
}