/*
 * Copyright (c) 2024, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.ballerina.sonar;

/**
 * {@code Constants} contains the constant variables used within the SonarQube Platform plugin.
 *
 * @since 0.1.0
 */
public final class Constants {
    private Constants() {
    }

    static final String PLATFORM_NAME = "sonarqube";
    static final String ISSUES_FILE_PATH = "ballerina-static-code-analysis-results.json";
    static final String START_LINE = "startLine";
    static final String START_LINE_OFFSET = "startLineOffset";
    static final String END_LINE = "endLine";
    static final String END_LINE_OFFSET = "endLineOffset";
    static final String RULE_ID = "ruleID";
    static final String MESSAGE = "message";
    static final String RULE_KIND = "ruleKind";
    static final String SOURCE = "source";
    static final String FILE_NAME = "fileName";
    static final String FILE_PATH = "filePath";
}
