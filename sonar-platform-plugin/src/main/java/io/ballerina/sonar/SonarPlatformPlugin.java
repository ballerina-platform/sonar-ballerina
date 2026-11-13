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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.ballerina.scan.Issue;
import io.ballerina.scan.PlatformPluginContext;
import io.ballerina.scan.StaticCodeAnalysisPlatformPlugin;
import io.ballerina.scan.internal.IssueImpl;
import org.apache.commons.lang3.SystemUtils;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static io.ballerina.sonar.Constants.END_LINE;
import static io.ballerina.sonar.Constants.END_LINE_OFFSET;
import static io.ballerina.sonar.Constants.FILE_NAME;
import static io.ballerina.sonar.Constants.FILE_PATH;
import static io.ballerina.sonar.Constants.ISSUES_FILE_PATH;
import static io.ballerina.sonar.Constants.MESSAGE;
import static io.ballerina.sonar.Constants.RULE_ID;
import static io.ballerina.sonar.Constants.RULE_KIND;
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
    private static final String SCANNER = "sonar-scanner";
    private static final String EXCLUSIONS = String.join(",", "**/*.java", "**/*.xml", "**/*.yaml", "**/*.go",
            "**/*.kt", "**/*.js", "**/*.html", "**/*.YAML", "**/*.rb", "**/*.scala", "**/*.py");

    private final String scanner;
    private final boolean windows;
    private final Path projectDir;
    private PlatformPluginContext platformPluginContext;

    public SonarPlatformPlugin() {
        this(SCANNER, SystemUtils.IS_OS_WINDOWS, Path.of(System.getProperty("user.dir")));
    }

    SonarPlatformPlugin(String scanner, boolean windows, Path projectDir) {
        this.scanner = scanner;
        this.windows = windows;
        this.projectDir = projectDir;
    }

    @Override
    public String platform() {
        return PLATFORM_NAME;
    }

    @Override
    public void init(PlatformPluginContext platformPluginContext) {
        this.platformPluginContext = platformPluginContext;
    }

    @Override
    public void onScan(List<Issue> issues) {
        Path issuesFile = saveIssues(issues);
        if (platformPluginContext.initiatedByPlatform()) {
            return;
        }

        List<String> scannerArguments = new ArrayList<>();
        scannerArguments.add("-Dsonar.exclusions=" + EXCLUSIONS);
        scannerArguments.add("-DanalyzedResultsPath=" + issuesFile);
        String sonarProjectPropertiesPath = platformPluginContext.platformArgs().get("sonarProjectPropertiesPath");
        if (sonarProjectPropertiesPath != null) {
            scannerArguments.add("-Dproject.settings=" + sonarProjectPropertiesPath);
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command(windows, scanner, scannerArguments))
                .directory(projectDir.toFile())
                .redirectErrorStream(true)
                .redirectInput(ProcessBuilder.Redirect.INHERIT);
        runScanner(processBuilder, System.err);
    }

    /**
     * Runs the scanner and copies its output to the given stream. Standard output of {@code bal scan} holds only
     * the scan results, so the scanner must not write to it.
     */
    private void runScanner(ProcessBuilder processBuilder, PrintStream log) {
        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException ex) {
            throw new RuntimeException("Unable to run " + scanner + ", which must be on the PATH: " + ex.getMessage(),
                    ex);
        }
        try (InputStream scannerOutput = process.getInputStream()) {
            scannerOutput.transferTo(log);
            log.flush();
            int exitCode = process.waitFor();
            log.println(exitCode == 0 ? "Reporting successful!" : "Reporting failed!");
        } catch (IOException ex) {
            process.destroy();
            throw new RuntimeException(ex);
        } catch (InterruptedException ex) {
            process.destroy();
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        }
    }

    /**
     * Builds the command that runs the scanner with the given arguments, without a shell on Unix, so that each
     * argument reaches the scanner as is.
     *
     * <p>On Windows, the scanner is a batch file, which only {@code cmd} can run, and {@code cmd} finds it on the
     * {@code PATH} through {@code PATHEXT}. {@code cmd /s /c} runs the text inside the outer quotes as one command
     * line, so each argument is quoted to keep spaces and the characters {@code cmd} treats specially.
     *
     * @param windows   whether the command runs on Windows
     * @param scanner   the scanner executable
     * @param arguments the arguments of the scanner
     * @return the command
     */
    static List<String> command(boolean windows, String scanner, List<String> arguments) {
        List<String> command = new ArrayList<>();
        if (!windows) {
            command.add(scanner);
            command.addAll(arguments);
            return command;
        }
        StringBuilder commandLine = new StringBuilder("\"").append(quoteForCmd(scanner));
        arguments.forEach(argument -> commandLine.append(' ').append(quoteForCmd(argument)));
        commandLine.append('"');
        command.add("cmd.exe");
        command.add("/d");
        command.add("/s");
        command.add("/c");
        command.add(commandLine.toString());
        return command;
    }

    private static String quoteForCmd(String argument) {
        if (argument.indexOf('"') >= 0) {
            throw new IllegalArgumentException("Unable to pass an argument with a double quote to the scanner: "
                    + argument);
        }
        // A backslash before the closing quote would escape it, so trailing backslashes are doubled
        int end = argument.length();
        while (end > 0 && argument.charAt(end - 1) == '\\') {
            end--;
        }
        return "\"" + argument + argument.substring(end) + "\"";
    }

    private Path saveIssues(List<Issue> issues) {
        JsonArray issuesAsJson = new JsonArray();
        issues.forEach(issue -> {
            IssueImpl reportedIssue = (IssueImpl) issue;
            JsonObject issueObject = new JsonObject();
            issueObject.addProperty(START_LINE, reportedIssue.location().lineRange().startLine().line());
            issueObject.addProperty(START_LINE_OFFSET, reportedIssue.location().lineRange().startLine().offset());
            issueObject.addProperty(END_LINE, reportedIssue.location().lineRange().endLine().line());
            issueObject.addProperty(END_LINE_OFFSET, reportedIssue.location().lineRange().endLine().offset());
            issueObject.addProperty(RULE_ID, reportedIssue.rule().id());
            issueObject.addProperty(MESSAGE, reportedIssue.rule().description());
            issueObject.addProperty(RULE_KIND, reportedIssue.rule().kind().toString());
            issueObject.addProperty(SOURCE, reportedIssue.source().toString());
            issueObject.addProperty(FILE_NAME, reportedIssue.fileName());
            issueObject.addProperty(FILE_PATH, reportedIssue.filePath());
            issuesAsJson.add(issueObject);
        });

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String jsonOutput = gson.toJson(issuesAsJson);
        Path destination = projectDir.resolve(ISSUES_FILE_PATH).toAbsolutePath();
        try (FileWriter writer = new FileWriter(destination.toFile(), StandardCharsets.UTF_8)) {
            writer.write(jsonOutput);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return destination;
    }
}
