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

import net.oneandone.sushi.fs.Copy;
import net.oneandone.sushi.fs.ModeException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.filter.Filter;
import net.oneandone.sushi.io.OS;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Substitution;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * The method below adjust file permissions.
 *
 * To think about permissions, it's useful to distinguishes three types for files/directories
 * * Source files:
 *    * group is stool (or users)
 *    * setgid bit is set for directories
 *   Almost everything in the stage directory - the only exception is if an application write files into the stage directory.
 *   Source files include all files generated by the build process (usually in the target directory)
 *   If the running application can change files with an built-in editor, these files are considered source files,
 *   even though they were changed by the application
 * * Stool files
 *   * group: stool or users
 *   * directories have the setgid bit set
 *   * permissions are rw-rw-r--
 *   everything written by stool - in particular file to configure the running application. All stool files reside in
 *   the backstage directory, stool does not modify the stage directory.
 * * Application files
 *   * nothing we can guaranty about application files.
 *   everything written by the running application, e.g. log files; usually, application files are written under
 *   the backstage directory, but some are accidentally written into the target directory.
 *
 * Technically important:
 * * the setgid bit is used to add the proper group to backstage files
 * * when overwriting files in Java, Java changes the content, but not the ower, group or permissions
 */
public final class Files {
    /** Creates a directory that's readable for all stool group users. */
    public static void createSourceDirectory(PrintWriter log, FileNode dir, String group) throws IOException {
        dir.mkdir();
        sourceTree(log, dir, group);
    }

    /** Fixes permissions and group so that all files are stage files. */
    public static void sourceTree(PrintWriter log, FileNode dir, String group) throws IOException {
        // CAUTION: setPermission doesn't work, see the comment in createBackstageDirectory
        exec(log, dir, "chgrp", "-R", group, ".");
        exec(log, dir, "sh", "-c", "find . -type d | xargs chmod g+s");
    }


    public static Node createStoolDirectoryOpt(PrintWriter log, FileNode directory) throws IOException {
        if (!directory.isDirectory()) {
            createStoolDirectory(log, directory);
        }
        return directory;
    }

    /** Creates a directory with mode 2775  */
    public static Node createStoolDirectory(PrintWriter log, FileNode directory) throws IOException {
        // The code
        //    directory.mkdir();
        // would inherit the setgid flag from the home directory, fine. But
        //     permissions(directory, "rwxrwxr-x");
        // would reset setgid. Thus, I have to do it expensively;
        exec(log, directory.getParent(), "mkdir", "-m", "2775", directory.getName());
        return directory;
    }

    /**
     * Set permissions of a backstage file.
     * Assumes the files is owned by the current user (usually because it was just created by us)
     * or otherwise already has the proper permissions.
     */
    public static Node stoolFile(Node file) throws IOException {
        permissions(file, "rw-rw-r--");
        return file;
    }

    /**
     * Set permissions of a backstage directory.
     * Assumes the directory is owned by the current user (usually because it was just created by us)
     * or otherwise already has the proper permissions.
     */
    public static FileNode stoolDirectory(PrintWriter log, FileNode dir) throws IOException {
        String old;

        // TODO: this is expensive, but otherwise, the setgid bit inherited from the home directory is lost by the previous permissions call.
        // see comments in createBackstageDirectory ...
        if (OS.CURRENT == OS.MAC) {
            old = dir.exec("stat", "-f", "%Op", ".");
            // MAC OS returns an addition mode octet, mask it out:
            old = Integer.toString(Integer.parseInt(old.trim(), 8) & 07777, 8);
        } else {
            old = dir.exec("stat", "--format", "%a", ".").trim();
        }
        if (!old.equals("2775")) {
            exec(log, dir, "chmod", "2775", ".");
        }
        return dir;
    }

    /**
     * CAUTION: assumes that the files is owned by the current user (usually because it was just created by us) or otherwise
     * already has the proper permissions.
     */
    private static Node stoolExecutable(Node file) throws IOException {
        permissions(file, "rwxrwxr-x");
        return file;
    }

    private static void permissions(Node file, String permissions) throws ModeException {
        // TODO: if Java overwrites an existing file, ownership and permissions are not changed!
        // As a consequence. setPermissions would fail.
        // To work-around this, I assume that the original creator of the file has already adjusted permissions;
        // or to put it vice versa: if the permissions don't match, the current user is the owner, we can adjust them.
        String old;

        old = file.getPermissions();
        if (!old.equals(permissions)) {
            file.setPermissions(permissions);
        }
    }

    /**
     * Adjusts permissions for a tree of backstage files. The group is not touched.
     *
     * CAUTION: assumes that the files is owned by the current user (usually because it was just created by us) or otherwise
     * already has the proper permissions.
     */
    public static void stoolTree(PrintWriter log, FileNode dir) throws IOException {
        stoolDirectory(log, dir);
        for (FileNode child : dir.list()) {
            if (child.isDirectory()) {
                stoolTree(log, child);
            } else {
                stoolFile(child);
            }
        }
    }

    //--

    public static void exec(PrintWriter log, FileNode home, String ... cmd) throws IOException {
        log.println("[" + home + "] " + Separator.SPACE.join(cmd));
        home.execNoOutput(cmd);
    }

    //-- templates


    /** files without keyword substitution */
    private static final String[] BINARY_EXTENSIONS = {".keystore", ".war", ".jar", ".gif", ".ico", ".class "};

    public static boolean isBinary(String name) {
        for (String ext : BINARY_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private static Filter withoutBinary(Filter orig) {
        Filter result;

        result = new Filter(orig);
        for (String ext : BINARY_EXTENSIONS) {
            result.exclude("**/*" + ext);
        }
        return result;
    }


    //--

    public static final Substitution S = new Substitution("${{", "}}", '\\');

    public static void template(PrintWriter log, Node src, FileNode dest, Map<String, String> variables) throws IOException {
        Filter selection;
        List<Node> nodes;

        dest.checkDirectory();
        // Permissions:
        //
        // template files are stool files, but some of the directories contain application files
        // (e.g. tomcat/conf/Catalina). So we cannot remove the whole directory to create a fresh copy
        // (also, we would loose log files by wiping the template) and we cannot simply update permissions
        // on all files (via backstageTree), we have to iterate the file actually part of the template.
        selection = src.getWorld().filter().includeAll();
        nodes = new Copy(src, withoutBinary(selection), false, variables, S).directory(dest);
        for (Node node : nodes) {
            if (node.isDirectory()) {
                stoolDirectory(log, (FileNode) node);
            } else {
                if (node.getName().endsWith(".sh")) {
                    stoolExecutable(node);
                } else {
                    stoolFile(node);
                }
            }
        }

        for (Node binary : src.find(selection)) {
            if (isBinary(binary.getName())) {
                Files.stoolFile(binary.copyFile(dest.join(binary.getRelative(src))));
            }
        }
    }

    //--

    private Files() {
    }

}