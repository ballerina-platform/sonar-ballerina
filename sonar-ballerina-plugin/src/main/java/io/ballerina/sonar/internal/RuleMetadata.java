/*
 * Copyright (c) 2025, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.sonar.internal;

/**
 * Represents all metadata required for a rule to be registered in SonarQube.
 *
 * @since 0.2.0
 */
public class RuleMetadata {
    private final String id;
    private final String name;
    private final String description;
    private final String type;
    private final String severity;
    private final String[] tags;

    private RuleMetadata(String id, String name, String description, String type,
                         String severity, String[] tags) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.severity = severity;
        this.tags = tags;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String type() {
        return type;
    }

    public String severity() {
        return severity;
    }

    public String[] tags() {
        return tags.clone();
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {
        private String id;
        private String name;
        private String description;
        private String type;
        private String severity;
        private String[] tags;

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder setType(String type) {
            this.type = type;
            return this;
        }

        public Builder setSeverity(String severity) {
            this.severity = severity;
            return this;
        }

        public Builder setTags(String[] tags) {
            this.tags = tags;
            return this;
        }

        public RuleMetadata build() {
            return new RuleMetadata(id, name, description, type, severity, tags);
        }
    }
}
