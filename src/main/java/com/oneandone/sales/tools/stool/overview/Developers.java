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
package com.oneandone.sales.tools.stool.overview;

import com.oneandone.sales.tools.devreg.model.Developer;
import com.oneandone.sales.tools.devreg.model.DeveloperNotFound;
import com.oneandone.sales.tools.devreg.model.Ldap;

import javax.naming.NamingException;
import java.util.HashMap;
import java.util.Map;

public class Developers {
    private final Ldap ldap;
    private final Map<String, Developer> developers;

    public Developers(Ldap ldap) {
        this.ldap = ldap;
        this.developers = new HashMap<>();
    }

    public Developer byLogin(String login) throws DeveloperNotFound, NamingException {
        Developer developer;

        developer = developers.get(login);
        if (developer == null) {
            developer = ldap.developerByLogin(login);
            developers.put(login, developer);
        }
        return developer;
    }
}
