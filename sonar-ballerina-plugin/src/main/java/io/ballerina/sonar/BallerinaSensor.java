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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.FilePredicate;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rules.RuleType;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.ballerina.sonar.Constants.ANALYSIS_RESULTS_FILE_PATH;
import static io.ballerina.sonar.Constants.BUG;
import static io.ballerina.sonar.Constants.BUILT_IN;
import static io.ballerina.sonar.Constants.EXTERNAL;
import static io.ballerina.sonar.Constants.ISSUES_FILE_PATH;
import static io.ballerina.sonar.Constants.ISSUE_END_LINE;
import static io.ballerina.sonar.Constants.ISSUE_END_LINE_OFFSET;
import static io.ballerina.sonar.Constants.ISSUE_FILE_PATH;
import static io.ballerina.sonar.Constants.ISSUE_MESSAGE;
import static io.ballerina.sonar.Constants.ISSUE_RULE_ID;
import static io.ballerina.sonar.Constants.ISSUE_RULE_KIND;
import static io.ballerina.sonar.Constants.ISSUE_SOURCE;
import static io.ballerina.sonar.Constants.ISSUE_START_LINE;
import static io.ballerina.sonar.Constants.ISSUE_START_LINE_OFFSET;
import static io.ballerina.sonar.Constants.RULE_REPOSITORY_KEY;
import static io.ballerina.sonar.Constants.SONAR_SCANNER_OFFSET;
import static io.ballerina.sonar.Constants.VULNERABILITY;

/**
 * Represents the implementation of the {@link Sensor} class for Ballerina.
 *
 * @since 0.1.0
 */
class BallerinaSensor implements Sensor {
    private final Logger logger = LoggerFactory.getLogger(BallerinaSensor.class);
    private final BallerinaLanguage language;
    private final Set<String> externalRules = new HashSet<>();

    public BallerinaSensor(BallerinaLanguage language) {
        this.language = language;
    }

    @Override
    public void describe(SensorDescriptor descriptor) {
        descriptor.onlyOnLanguage(language.getKey()).name(language.getName() + " Sensor");
    }

    @Override
    public void execute(SensorContext sensorContext) {
        FileSystem fileSystem = sensorContext.fileSystem();
        FilePredicate mainFilePredicate = sensorContext.fileSystem().predicates()
                .and(fileSystem.predicates().hasLanguage(language.getKey()),
                        fileSystem.predicates().hasType(InputFile.Type.MAIN));
        Map<String, InputFile> pathAndInputFiles = new HashMap<>();
        fileSystem.inputFiles(mainFilePredicate).forEach(inputFile -> {
            pathAndInputFiles.put(Path.of(inputFile.uri()).toString(), inputFile);
        });
        sensorContext.config().get(ANALYSIS_RESULTS_FILE_PATH).ifPresentOrElse((analyzedResultsPath) ->
                        processAnalyzedResultsReport(sensorContext, pathAndInputFiles, analyzedResultsPath),
                () -> performLibraryCall(sensorContext, pathAndInputFiles)
        );
    }

    public void processAnalyzedResultsReport(SensorContext context,
                                             Map<String, InputFile> pathAndInputFiles,
                                             String analyzedResultsFilePath) {
        logger.info("Analyzing batch report: ", analyzedResultsFilePath);
        String fileContent = getFileContent(analyzedResultsFilePath);
        reportFileContent(context, pathAndInputFiles, fileContent);
    }

    public void performLibraryCall(SensorContext context, Map<String, InputFile> pathAndInputFiles) {
        logger.info("Analyzing Ballerina project");
        ProcessBuilder fileScan = new ProcessBuilder();
        fileScan.directory(context.fileSystem().baseDir());
        List<String> arguments = new ArrayList<>();
        if (SystemUtils.IS_OS_WINDOWS) {
            arguments.add("cmd");
            arguments.add("/c");
        } else {
            arguments.add("sh");
            arguments.add("-c");
        }

        arguments.add("bal scan --platform-triggered --platforms=sonarqube");
        fileScan.command(arguments);
        try {
            fileScan.inheritIO();
            Process process = fileScan.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                String analyzedResultsFilePath = Paths.get(context.fileSystem().baseDir().getPath())
                        .resolve(ISSUES_FILE_PATH)
                        .toString();
                String fileContent = getFileContent(analyzedResultsFilePath);
                reportFileContent(context, pathAndInputFiles, fileContent);
            } else {
                logger.info("Unable to analyze ballerina file batch!");
            }
        } catch (IOException | InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    private String getFileContent(String analyzedResultsFilePath) {
        String fileContent = "";
        try (
                FileReader fileReader = new FileReader(analyzedResultsFilePath, StandardCharsets.UTF_8);
                BufferedReader bufferedReader = new BufferedReader(fileReader)
        ) {
            fileContent = bufferedReader.lines().collect(Collectors.joining());
        } catch (IOException ex) {
            logger.info("Unable to retrieve analysis results!");
        }
        return fileContent;
    }

    private void reportFileContent(SensorContext context,
                                   Map<String, InputFile> pathAndInputFiles,
                                   String fileContent) {
        JsonArray balScanOutput = null;
        try {
            balScanOutput = JsonParser.parseString(fileContent).getAsJsonArray();
        } catch (Exception ignored) {
            logger.info("Unable to report analysis results!");
        }

        boolean reportingSuccessful = reportAnalysisIssues(context, balScanOutput, pathAndInputFiles);
        if (reportingSuccessful) {
            logger.info("Ballerina analysis successful!");
        } else {
            logger.info("Unable to analyze ballerina file batch!");
        }
    }

    public boolean reportAnalysisIssues(SensorContext context, JsonArray analysisIssues,
                                        Map<String, InputFile> pathAndInputFiles) {
        if (analysisIssues == null) {
            return false;
        }
        for (JsonElement issueElement : analysisIssues) {
            JsonObject issue = issueElement.getAsJsonObject();
            String absoluteFilePath = issue.get(ISSUE_FILE_PATH).getAsString();
            InputFile inputFile = pathAndInputFiles.get(absoluteFilePath);
            String issueType = issue.get(ISSUE_SOURCE).getAsString();
            switch (issueType) {
                case BUILT_IN -> reportIssue(inputFile, context, issue, true);
                case EXTERNAL -> reportIssue(inputFile, context, issue, false);
                default -> logger.info("Invalid Issue Format!");
            }
        }
        return true;
    }

    public void reportIssue(InputFile inputFile, SensorContext context, JsonObject balScanOutput,
                             boolean builtInIssue) {
        String ruleID = balScanOutput.get(ISSUE_RULE_ID).getAsString();
        String message = balScanOutput.get(ISSUE_MESSAGE).getAsString();
        int startLine = balScanOutput.get(ISSUE_START_LINE).getAsInt();
        int startLineOffset = balScanOutput.get(ISSUE_START_LINE_OFFSET).getAsInt();
        int endLine = balScanOutput.get(ISSUE_END_LINE).getAsInt();
        int endLineOffset = balScanOutput.get(ISSUE_END_LINE_OFFSET).getAsInt();
        RuleKey ruleKey = RuleKey.of(RULE_REPOSITORY_KEY, ruleID);
        if (builtInIssue) {
            context.newIssue()
                    .forRule(ruleKey)
                    .at(context.newIssue()
                            .newLocation()
                            .on(inputFile)
                            .at(inputFile.newRange(startLine + SONAR_SCANNER_OFFSET,
                                    startLineOffset,
                                    endLine + SONAR_SCANNER_OFFSET,
                                    endLineOffset))
                            .message(message)
                    )
                    .save();
            return;
        }

        String ruleKind = balScanOutput.get(ISSUE_RULE_KIND).getAsString();
        RuleType ruleType = switch (ruleKind) {
            case BUG -> RuleType.BUG;
            case VULNERABILITY -> RuleType.VULNERABILITY;
            default -> RuleType.CODE_SMELL;
        };
        if (!externalRules.contains(ruleID)) {
            context.newAdHocRule()
                    .engineId("ballerina_external_analyzer")
                    .ruleId(ruleID)
                    .name(ruleID)
                    .type(ruleType)
                    .severity(Severity.MAJOR)
                    .description(message)
                    .save();
            externalRules.add(ruleID);
        }

        context.newExternalIssue()
                .engineId("ballerina_external_analyzer")
                .ruleId(ruleID)
                .type(ruleType)
                .severity(Severity.MAJOR)
                .remediationEffortMinutes(10L)
                .at(context.newIssue()
                        .newLocation()
                        .on(inputFile)
                        .at(inputFile.newRange(startLine + SONAR_SCANNER_OFFSET,
                                startLineOffset,
                                endLine + SONAR_SCANNER_OFFSET,
                                endLineOffset))
                        .message(message))
                .save();
    }
}
