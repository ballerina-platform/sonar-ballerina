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

import org.assertj.core.api.Assertions;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.batch.sensor.issue.ExternalIssue;
import org.sonar.api.batch.sensor.issue.Issue;
import org.sonar.api.batch.sensor.issue.IssueLocation;
import org.sonar.api.batch.sensor.rule.AdHocRule;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.config.internal.Settings;
import org.sonar.api.rules.RuleType;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;

import static io.ballerina.sonar.Constants.ISSUES_FILE_PATH;

/**
 * {@code BallerinaSensorTest} is the test class for the {@link BallerinaSensor}.
 *
 * @since 0.1.0
 */
public class BallerinaSensorTest extends AbstractSensorTest {
    @Test(description = "Test the BallerinaSensor")
    void testBallerinaSensor() throws IOException {
        Path projectDir = testResources.resolve("ballerina-project-with-report");
        SensorContextTester context = SensorContextTester.create(projectDir);
        cleanUp(projectDir);

        // Create mock issues file
        String resourceFileContent = Files.readString(
                projectDir.resolve("ballerina-static-code-analysis-results.json.template"));
        String projectAbsolutePath = projectDir.resolve("main.bal").toAbsolutePath().toString().replace("\\", "\\\\");
        resourceFileContent = resourceFileContent.replaceAll("__file_path__", Matcher
                .quoteReplacement(projectAbsolutePath));
        Path filePath = Files.writeString(Paths.get(projectDir.toString(), ISSUES_FILE_PATH), resourceFileContent,
                StandardCharsets.UTF_8);

        // Append mock settings file
        Settings settings = context.settings().appendProperty("analyzedResultsPath", filePath.toString());
        context.setSettings((MapSettings) settings);

        // Add mock input files
        InputFile ballerinaFile = createInputFileFromPath(projectDir, "main.bal");
        context.fileSystem().add(ballerinaFile);

        // Trigger analysis
        BallerinaSensor sensor = sensor();
        sensor.execute(context);
        if (Files.exists(filePath)) {
            Files.delete(filePath);
        }
        List<Issue> issues = context.allIssues().stream().toList();
        Assert.assertEquals(issues.size(), 3);
        assertIssue(issues.get(0), "ballerina:Ballerina1", "Avoid checkpanic",
                21, 17, 21, 39);
        assertIssue(issues.get(1), "ballerina:ballerina/example_module_static_code_analyzer:1", "rule 1",
                17, 0, 22, 1);
        assertIssue(issues.get(2), "ballerina:ballerinax/example_module_static_code_analyzer:1", "rule 1",
                17, 0, 22, 1);
        List<AdHocRule> adHocRules = context.allAdHocRules().stream().toList();
        Assert.assertEquals(adHocRules.size(), 1);
        AdHocRule adHocRule = adHocRules.get(0);
        Assert.assertEquals(adHocRule.severity(), Severity.MAJOR);
        Assert.assertEquals(adHocRule.type(), RuleType.CODE_SMELL);
        Assert.assertEquals(adHocRule.name(), "exampleOrg/example_module_static_code_analyzer:1");
        Assert.assertEquals(adHocRule.description(), "rule 1");
        Assert.assertEquals(adHocRule.engineId(), "ballerina_external_analyzer");
        Assert.assertEquals(adHocRule.ruleId(), "exampleOrg/example_module_static_code_analyzer:1");
        List<ExternalIssue> externalIssues = context.allExternalIssues().stream().toList();
        Assert.assertEquals(externalIssues.size(), 1);
        ExternalIssue externalIssue = externalIssues.get(0);
        Assert.assertEquals(externalIssue.severity(), Severity.MAJOR);
        Assert.assertEquals(externalIssue.type(), RuleType.CODE_SMELL);
        Assert.assertEquals(externalIssue.engineId(), "ballerina_external_analyzer");
        Assert.assertEquals(externalIssue.ruleId(), "exampleOrg/example_module_static_code_analyzer:1");
        IssueLocation issueLocation = externalIssue.primaryLocation();
        Assert.assertEquals(issueLocation.message(), "rule 1");
        TextRange textRange = issueLocation.textRange();
        Assert.assertNotNull(textRange);
        Assert.assertEquals(textRange.start().line(), 17);
        Assert.assertEquals(textRange.start().lineOffset(), 0);
        Assert.assertEquals(textRange.end().line(), 22);
        Assert.assertEquals(textRange.end().lineOffset(), 1);
    }

    @Test(description = "Test the BallerinaSensor with the scan command")
    void testBallerinaSensorWithScanCommand() throws IOException {
        Path projectDir = testResources.resolve("ballerina-project-wo-report");
        SensorContextTester context = SensorContextTester.create(projectDir);
        cleanUp(projectDir);

        // Add mock input files
        InputFile ballerinaFile = createInputFileFromPath(projectDir, "main.bal");
        context.fileSystem().add(ballerinaFile);

        // Trigger analysis
        BallerinaSensor sensor = sensor();
        String userDir = System.getProperty("user.dir");
        System.setProperty("user.dir", projectDir.toString());
        sensor.execute(context);
        System.setProperty("user.dir", userDir);
        List<Issue> issues = context.allIssues().stream().toList();
        assertIssue(issues.get(0), "ballerina:Ballerina1", "Avoid checkpanic", 21, 17, 21, 39);
    }

    private void assertIssue(Issue issue, String ruleKey, String message, int startLine, int startLineOffset,
                             int endLine, int endLineOffset) {
        Assertions.assertThat(issue.ruleKey().toString()).isEqualTo(ruleKey);
        IssueLocation issueLocation = issue.primaryLocation();
        Assertions.assertThat(issueLocation.message()).isEqualTo(message);
        TextRange textRange = issueLocation.textRange();
        Assert.assertNotNull(textRange);
        Assertions.assertThat(textRange.start().line()).isEqualTo(startLine);
        Assertions.assertThat(textRange.start().lineOffset()).isEqualTo(startLineOffset);
        Assertions.assertThat(textRange.end().line()).isEqualTo(endLine);
        Assertions.assertThat(textRange.end().lineOffset()).isEqualTo(endLineOffset);
    }

    private BallerinaSensor sensor() {
        return new BallerinaSensor(language());
    }
}
