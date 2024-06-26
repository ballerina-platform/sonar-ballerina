/*
 *
 *  * Copyright (c) 2024, WSO2 LLC. (https://www.wso2.com).
 *  *
 *  * WSO2 LLC. licenses this file to you under the Apache License,
 *  * Version 2.0 (the "License"); you may not use this file except
 *  * in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing,
 *  * software distributed under the License is distributed on an
 *  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  * KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations
 *  * under the License.
 *
 */

package io.ballerina.sonar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.ballerina.scan.Issue;
import io.ballerina.scan.PlatformPluginContext;
import io.ballerina.scan.StaticCodeAnalysisPlatformPlugin;
import io.ballerina.scan.internal.IssueImpl;
import org.apache.commons.lang3.SystemUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static io.ballerina.sonar.Constants.END_LINE;
import static io.ballerina.sonar.Constants.END_LINE_OFFSET;
import static io.ballerina.sonar.Constants.FILE_NAME;
import static io.ballerina.sonar.Constants.FILE_PATH;
import static io.ballerina.sonar.Constants.FORWARD_SLASH;
import static io.ballerina.sonar.Constants.ISSUE_FILE_PATH;
import static io.ballerina.sonar.Constants.MESSAGE;
import static io.ballerina.sonar.Constants.RULE_ID;
import static io.ballerina.sonar.Constants.RULE_KIND;
import static io.ballerina.sonar.Constants.RULE_PREFIX;
import static io.ballerina.sonar.Constants.SOURCE;
import static io.ballerina.sonar.Constants.START_LINE;
import static io.ballerina.sonar.Constants.PLATFORM_NAME;
import static io.ballerina.sonar.Constants.START_LINE_OFFSET;

/**
 * Represents the implementation of the {@link StaticCodeAnalysisPlatformPlugin} for reporting issues to SonarQube.
 *
 * @since 0.1.0
 */
public class SonarPlatformPlugin implements StaticCodeAnalysisPlatformPlugin {
    private PlatformPluginContext platformPluginContext;
    private final List<String> processBuilderArguments = new ArrayList<>();
    private final ProcessBuilder processBuilder = new ProcessBuilder();
    private final PrintStream outputStream = System.out;

    @Override
    public String platform() {
        return PLATFORM_NAME;
    }

    @Override
    public void init(PlatformPluginContext platformPluginContext) {
        this.platformPluginContext = platformPluginContext;
        if (SystemUtils.IS_OS_WINDOWS) {
            processBuilderArguments.add("cmd");
            processBuilderArguments.add("/c");
        } else {
            processBuilderArguments.add("sh");
            processBuilderArguments.add("-c");
        }
        processBuilderArguments.add("sonar-scanner");
        processBuilderArguments.add("-Dsonar.exclusions=" +
                "'" +
                "**/*.java," +
                "**/*.xml," +
                "**/*.yaml," +
                "**/*.go," +
                "**/*.kt," +
                "**/*.js," +
                "**/*.html," +
                "**/*.YAML" +
                ",**/*.rb," +
                "**/*.scala," +
                "**/*.py" +
                "'");
    }

    @Override
    public void onScan(List<Issue> issues) {
        saveIssues(ISSUE_FILE_PATH, issues);
        if (platformPluginContext.initiatedByPlatform()) {
            return;
        }

        processBuilderArguments.add("-DanalyzedResultsPath=" + Path.of(ISSUE_FILE_PATH).toAbsolutePath());
        String sonarProjectPropertiesPath = platformPluginContext.platformArgs().get("sonarProjectPropertiesPath");
        if (sonarProjectPropertiesPath != null) {
            processBuilderArguments.add("-Dproject.settings=" + sonarProjectPropertiesPath);
        }

        processBuilder.command(processBuilderArguments);
        processBuilder.inheritIO();
        try {
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                outputStream.println("Reporting successful!");
            } else {
                outputStream.println("Reporting failed!");
            }
        } catch (IOException | InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void saveIssues(String fileName, List<Issue> issues) {
        JsonArray issuesAsJson = new JsonArray();
        issues.forEach(issue -> {
            IssueImpl reportedIssue = (IssueImpl) issue;
            JsonObject issueObject = new JsonObject();
            issueObject.addProperty(START_LINE, reportedIssue.location().lineRange().startLine().line());
            issueObject.addProperty(START_LINE_OFFSET, reportedIssue.location().lineRange().startLine()
                    .offset());
            issueObject.addProperty(END_LINE, reportedIssue.location().lineRange().endLine().line());
            issueObject.addProperty(END_LINE_OFFSET, reportedIssue.location().lineRange().endLine().offset());
            String fullyQualifiedRuleId = reportedIssue.rule().id();
            String[] parts = fullyQualifiedRuleId.split(":");
            String ruleWithPrefix;
            if (parts[0].split(FORWARD_SLASH).length == 1) {
                ruleWithPrefix = RULE_PREFIX + parts[1];
            } else {
                ruleWithPrefix = fullyQualifiedRuleId;
            }
            issueObject.addProperty(RULE_ID, ruleWithPrefix);
            issueObject.addProperty(MESSAGE, reportedIssue.rule().description());
            issueObject.addProperty(RULE_KIND, reportedIssue.rule().kind().toString());
            issueObject.addProperty(SOURCE, reportedIssue.source().toString());
            issueObject.addProperty(FILE_NAME, reportedIssue.fileName());
            issueObject.addProperty(FILE_PATH, reportedIssue.filePath());
            issuesAsJson.add(issueObject);
        });

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String jsonOutput = gson.toJson(issuesAsJson);
        File destination = new File(fileName);
        try (FileWriter writer = new FileWriter(destination, StandardCharsets.UTF_8)) {
            writer.write(jsonOutput);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
