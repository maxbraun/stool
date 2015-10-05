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
package net.oneandone.stool.extensions;

import net.oneandone.stool.configuration.Property;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.World;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ExtensionsFactory {
    public static ExtensionsFactory create(World world) {
        ExtensionsFactory factory;
        Properties properties;

        try {
            factory = new ExtensionsFactory();
            for (Node node : world.resources("META-INF/stool-extensions.properties")) {
                properties = node.readProperties();
                for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                    try {
                        factory.put((String) entry.getKey(), (Class) Class.forName((String) entry.getValue()));
                    } catch (ClassNotFoundException e) {
                        throw new IllegalArgumentException("extension not found: " + entry.getValue());
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("unexpected io exception", e);
        }
        return factory;
    }

    //--

    private final Map<String, Class<? extends Extension>> types;

    public ExtensionsFactory() {
        types = new HashMap<>();
    }

    public void put(String name, Class<? extends Extension> extension) {
        if (types.put(name, extension) != null) {
            throw new IllegalArgumentException("duplicate extension: " + name);
        }
    }

    public Class<? extends Extension> type(String name) {
        return types.get(name);
    }

    public Extensions newInstance() {
        Extensions extensions;

        extensions = new Extensions();
        for (Map.Entry<String, Class<? extends Extension>> entry : types.entrySet()) {
            try {
                extensions.add(entry.getKey(), false, entry.getValue().newInstance());
            } catch (InstantiationException | IllegalAccessException e) {
                throw new IllegalArgumentException("cannot instantiate extension " + entry.getValue().getName()
                        + ": " + e.getMessage(), e);
            }
        }
        return extensions;
    }

    public void fields(Map<String, Property> result) {
        String name;
        String fullName;
        Class<? extends Extension> extension;
        int modifiers;

        for (Map.Entry<String, Class<? extends Extension>> entry : types.entrySet()) {
            name = entry.getKey();
            extension = entry.getValue();
            result.put(name, new Property(name, "enable extension", Switch.FIELD, name));
            for (Field field : extension.getDeclaredFields()) {
                modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)) {
                    fullName = name + "." + field.getName();
                    result.put(fullName, new Property(fullName, "extension field", field, name));
                }
            }
        }
    }
}