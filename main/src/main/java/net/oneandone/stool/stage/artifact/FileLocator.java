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
package net.oneandone.stool.stage.artifact;

import net.oneandone.sushi.fs.file.FileNode;
import org.apache.maven.project.ProjectBuildingException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.VersionRangeResolutionException;

import java.io.FileNotFoundException;
import java.io.IOException;

public class FileLocator extends Locator {
    private final FileNode source;

    public FileLocator(FileNode source) {
        this.source = source;
    }

    public String defaultName() {
        String result;
        int idx;

        result = source.getName();
        idx = result.lastIndexOf('.');
        if (idx != -1) {
            result = result.substring(0, idx);
        }
        return result;
    }

    public WarFile resolve() {
        if (source.exists()) {
            return null;
        } else {
            return new WarFile(source);
        }
    }

    public String svnurl() {
        return null;
    }
}
