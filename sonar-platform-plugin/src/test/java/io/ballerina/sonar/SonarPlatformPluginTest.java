/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
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

import io.ballerina.scan.PlatformPluginContext;
import org.apache.commons.lang3.SystemUtils;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Tests the command that {@link SonarPlatformPlugin} runs to report the issues to SonarQube.
 */
public class SonarPlatformPluginTest {
    private static final String EXCLUSIONS = "-Dsonar.exclusions=**/*.java,**/*.xml,**/*.yaml,**/*.go,**/*.kt,"
            + "**/*.js,**/*.html,**/*.YAML,**/*.rb,**/*.scala,**/*.py";
    private static final String PROPERTIES_PATH = "conf dir/sonar project.properties";

    private final List<Path> tempDirs = new ArrayList<>();

    @AfterMethod
    public void tearDown() throws IOException {
        for (Path tempDir : tempDirs) {
            try (Stream<Path> paths = Files.walk(tempDir)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        tempDirs.clear();
    }

    @Test
    public void testUnixCommandPassesEachArgumentWithoutShell() {
        List<String> command = SonarPlatformPlugin.command(false, "sonar-scanner",
                List.of(EXCLUSIONS, "-DanalyzedResultsPath=/a b/results.json",
                        "-Dproject.settings=" + PROPERTIES_PATH));
        Assert.assertEquals(command, List.of("sonar-scanner", EXCLUSIONS, "-DanalyzedResultsPath=/a b/results.json",
                "-Dproject.settings=" + PROPERTIES_PATH));
    }

    @Test
    public void testWindowsCommandQuotesEachArgumentInOneCommandLine() {
        List<String> command = SonarPlatformPlugin.command(true, "sonar-scanner",
                List.of(EXCLUSIONS, "-DanalyzedResultsPath=C:\\a b\\results.json",
                        "-Dproject.settings=a&b.properties"));
        Assert.assertEquals(command, List.of("cmd.exe", "/d", "/s", "/c", "\"\"sonar-scanner\" \"" + EXCLUSIONS
                + "\" \"-DanalyzedResultsPath=C:\\a b\\results.json\" \"-Dproject.settings=a&b.properties\"\""));
    }

    @Test
    public void testWindowsCommandDoublesTrailingBackslashes() {
        List<String> command = SonarPlatformPlugin.command(true, "sonar-scanner", List.of("-Dx=C:\\a b\\"));
        Assert.assertEquals(command.get(4), "\"\"sonar-scanner\" \"-Dx=C:\\a b\\\\\"\"");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testWindowsCommandRejectsDoubleQuote() {
        SonarPlatformPlugin.command(true, "sonar-scanner", List.of("-Dx=a\"b"));
    }

    @Test
    public void testOnScanPassesEveryArgumentToScanner() throws IOException {
        Workspace workspace = workspace();
        SonarPlatformPlugin plugin = workspace.plugin(0);
        plugin.init(context(false, Map.of("sonarProjectPropertiesPath", PROPERTIES_PATH)));
        plugin.onScan(List.of());

        Path issuesFile = workspace.projectDir().resolve(Constants.ISSUES_FILE_PATH).toAbsolutePath();
        Assert.assertEquals(Files.readAllLines(workspace.argumentsFile(), StandardCharsets.UTF_8), List.of(EXCLUSIONS,
                "-DanalyzedResultsPath=" + issuesFile, "-Dproject.settings=" + PROPERTIES_PATH));
        Assert.assertEquals(Files.readString(issuesFile, StandardCharsets.UTF_8), "[]");
    }

    @Test
    public void testOnScanWithoutPropertiesPath() throws IOException {
        Workspace workspace = workspace();
        SonarPlatformPlugin plugin = workspace.plugin(0);
        plugin.init(context(false, Map.of()));
        plugin.onScan(List.of());

        Path issuesFile = workspace.projectDir().resolve(Constants.ISSUES_FILE_PATH).toAbsolutePath();
        Assert.assertEquals(Files.readAllLines(workspace.argumentsFile(), StandardCharsets.UTF_8),
                List.of(EXCLUSIONS, "-DanalyzedResultsPath=" + issuesFile));
    }

    @Test
    public void testOnScanTriggeredByPlatformOnlySavesIssues() throws IOException {
        Workspace workspace = workspace();
        SonarPlatformPlugin plugin = workspace.plugin(0);
        plugin.init(context(true, Map.of()));
        plugin.onScan(List.of());

        Assert.assertFalse(Files.exists(workspace.argumentsFile()));
        Assert.assertTrue(Files.exists(workspace.projectDir().resolve(Constants.ISSUES_FILE_PATH)));
    }

    @Test
    public void testOnScanWritesScannerOutputToStandardError() throws IOException {
        Workspace workspace = workspace();
        SonarPlatformPlugin plugin = workspace.plugin(0);
        plugin.init(context(false, Map.of()));
        CapturedStreams streams = capture(() -> plugin.onScan(List.of()));

        Assert.assertEquals(streams.out(), "");
        Assert.assertEquals(streams.err(), String.join(System.lineSeparator(),
                "scanner stdout", "scanner stderr", "Reporting successful!", ""));
    }

    @Test
    public void testOnScanReportsFailedScanner() throws IOException {
        Workspace workspace = workspace();
        SonarPlatformPlugin plugin = workspace.plugin(3);
        plugin.init(context(false, Map.of()));
        CapturedStreams streams = capture(() -> plugin.onScan(List.of()));

        Assert.assertEquals(streams.out(), "");
        Assert.assertEquals(streams.err(), String.join(System.lineSeparator(),
                "scanner stdout", "scanner stderr", "Reporting failed!", ""));
    }

    @Test
    public void testOnScanFailsWithoutScanner() throws IOException {
        Workspace workspace = workspace();
        SonarPlatformPlugin plugin = new SonarPlatformPlugin(workspace.root().resolve("missing").toString(), false,
                workspace.projectDir());
        plugin.init(context(false, Map.of()));

        RuntimeException exception = Assert.expectThrows(RuntimeException.class, () -> plugin.onScan(List.of()));
        Assert.assertTrue(exception.getMessage().startsWith("Unable to run " + workspace.root().resolve("missing")
                + ", which must be on the PATH: "), exception.getMessage());
    }

    private static CapturedStreams capture(Runnable runnable) {
        PrintStream systemOut = System.out;
        PrintStream systemErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            runnable.run();
        } finally {
            System.setOut(systemOut);
            System.setErr(systemErr);
        }
        return new CapturedStreams(out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    /**
     * The text written to the standard output and standard error streams.
     *
     * @param out the text written to standard output
     * @param err the text written to standard error
     */
    private record CapturedStreams(String out, String err) {
    }

    private Workspace workspace() throws IOException {
        if (SystemUtils.IS_OS_WINDOWS) {
            throw new SkipException("The fake scanner is a shell script");
        }
        Path root = Files.createTempDirectory("sonar platform plugin");
        tempDirs.add(root);
        return new Workspace(root, Files.createDirectories(root.resolve("project with spaces")),
                root.resolve("arguments.txt"));
    }

    /**
     * A project directory and a fake scanner that records its arguments, both in a path with spaces.
     *
     * @param root          the temporary directory that holds the others
     * @param projectDir    the directory of the project
     * @param argumentsFile the file the fake scanner writes its arguments to, one per line
     */
    private record Workspace(Path root, Path projectDir, Path argumentsFile) {

        SonarPlatformPlugin plugin(int scannerExitCode) throws IOException {
            Path scanner = Files.createDirectories(root.resolve("scanner bin")).resolve("sonar-scanner");
            Files.writeString(scanner, "#!/bin/sh\n"
                    + "for arg in \"$@\"; do printf '%s\\n' \"$arg\"; done > '" + argumentsFile + "'\n"
                    + "echo 'scanner stdout'\n"
                    + "echo 'scanner stderr' >&2\n"
                    + "exit " + scannerExitCode + "\n", StandardCharsets.UTF_8);
            Files.setPosixFilePermissions(scanner, PosixFilePermissions.fromString("rwx------"));
            return new SonarPlatformPlugin(scanner.toString(), false, projectDir);
        }
    }

    private static PlatformPluginContext context(boolean initiatedByPlatform, Map<String, String> platformArgs) {
        return new PlatformPluginContext() {
            @Override
            public Map<String, String> platformArgs() {
                return platformArgs;
            }

            @Override
            public boolean initiatedByPlatform() {
                return initiatedByPlatform;
            }
        };
    }
}
