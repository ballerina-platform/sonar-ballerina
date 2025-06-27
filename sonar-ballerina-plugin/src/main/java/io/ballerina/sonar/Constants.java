/*
 *  Copyright (c) 2024, WSO2 LLC. (https://www.wso2.com).
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package io.ballerina.sonar;

/**
 * {@code Constants} contains the constant variables used within the SonarQube Ballerina plugin.
 *
 * @since 0.1.0
 */
public final class Constants {
    private Constants() {
    }

    // Language definition constants
    static final String LANGUAGE_KEY = "ballerina";
    static final String LANGUAGE_NAME = "Ballerina";

    // Profile and rule definition constants
    static final String PROFILE_NAME = "Ballerina way";
    static final String RULE_REPOSITORY_KEY = "ballerina";
    static final String RULE_REPOSITORY_NAME = "BallerinaAnalyzer";

    // Property definition constants
    static final String FILE_SUFFIXES_KEY = "sonar.ballerina.file.suffixes";
    static final String FILE_SUFFIXES_DEFAULT_VALUE = "bal";

    // Sensor
    static final String ANALYSIS_RESULTS_FILE_PATH = "analyzedResultsPath";
    static final String ISSUES_FILE_PATH = "ballerina-static-code-analysis-results.json";
    static final String ISSUE_FILE_PATH = "filePath";
    static final String ISSUE_SOURCE = "source";
    static final String ISSUE_RULE_ID = "ruleID";
    static final String ISSUE_RULE_KIND = "ruleKind";
    static final String ISSUE_MESSAGE = "message";
    static final String ISSUE_START_LINE = "startLine";
    static final String ISSUE_START_LINE_OFFSET = "startLineOffset";
    static final String ISSUE_END_LINE = "endLine";
    static final String ISSUE_END_LINE_OFFSET = "endLineOffset";
    static final String BUILT_IN = "BUILT_IN";
    static final String EXTERNAL = "EXTERNAL";
    static final String BUG = "BUG";
    static final String VULNERABILITY = "VULNERABILITY";
    static final int SONAR_SCANNER_OFFSET = 1;
}
