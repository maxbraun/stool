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
package net.oneandone.stool.stage.artifact;

public class Change {
    private final long revision;
    private final String user;
    private final String message;
    private final long timestamp;

    public Change(long revision, String user, String message, long timestamp) {
        this.revision = revision;
        this.user = user;
        this.message = message;
        this.timestamp = timestamp;
    }

    public long getRevision() {
        return revision;
    }

    public String getUser() {
        return user;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        Change change;

        if (o instanceof Change) {
            change = (Change) o;
            return (revision == change.revision && timestamp == change.timestamp) && eq(message, change.message) && eq(user, change.user);
        }
        return true;
    }

    private static boolean eq(String left, String right) {
        if (left == null) {
            return right == null;
        } else {
            return left.equals(right);
        }
    }

    @Override
    public int hashCode() {
        return (int) revision;
    }
}
