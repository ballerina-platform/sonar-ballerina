# Ballerina SonarQube Plugin

## Overview

SonarQube is an open-source code quality and security platform. It analyzes code for issues, tracks metrics, and integrates with CI/CD. This helps developers write cleaner, more secure code. [SonarQube plugins](https://docs.sonarsource.com/sonarqube/latest/extension-guide/developing-a-plugin/supporting-new-languages/) allow developers to extend SonarQube analysis  support for  new languages.The sonar-ballerina plugin extends SonarQube to analyze Ballerina code. It leverages the Ballerina static code analysis tool to identify issues and report them to SonarQube.

## Prerequisites

1. OpenJDK 21 ([Adopt OpenJDK](https://adoptium.net/temurin/releases/?version=21) or any other OpenJDK distribution)

2. [Ballerina](https://ballerina.io/)

3. [Ballerina Static Code Analysis Tool](https://github.com/ballerina-platform/static-code-analysis-tool)

4. [SonarQube Community Build 26.11](https://www.sonarsource.com/products/sonarqube/downloads/) or later (Sonar Plugin API 14.1 or later)

5. [Sonar Scanner CLI](https://docs.sonarsource.com/sonarqube-community-build/analyzing-source-code/scanners/sonarscanner/)

## Rule metadata

The plugin loads its rules, including the "Why is this an issue?", "How can I fix it?" and "More info" sections shown
for each rule, from the `rule-info.json` file of the Ballerina static code analysis tool. When the SonarQube server
starts, the plugin uses the first of these sources that is available:

1. The `ruleInfoUrl` set in `sonar-ballerina-plugin/build.gradle`, or the `sonar.ballerina.ruleInfoUrl` property in
   `conf/sonar.properties` of the SonarQube server, which overrides it without rebuilding the plugin.
2. The `ruleInfoFallbackUrl` set in `sonar-ballerina-plugin/build.gradle`, which points to the `main` branch of the
   official scan tool repository. Rules without documentation there link to the
   [scan rules page](https://ballerina.io/learn/scan-rules/) instead.
3. The copy cached at `~/.sonar-ballerina/rule-info.json` by the last successful fetch.
4. The snapshot bundled from `sonar-ballerina-plugin/src/main/rule-info/rule-info.json`, with a warning that the rules
   may be outdated.

A source that cannot be reached within the timeout, does not respond with HTTP 200, or does not contain valid rules is
skipped with a warning. If none of the sources can be loaded, the SonarQube server fails to start. The rules are loaded
once per server start, so restart the server to pick up changes to `rule-info.json`.

To refresh the bundled snapshot from `ruleInfoUrl`, run `./gradlew :sonar-ballerina-plugin:updateRuleInfoSnapshot`, and
review the diff before committing it.

See [Writing rule-info.json entries](sonar-ballerina-plugin/src/main/rule-info/README.md) for how to decide the
value of each field, and how to derive it from the `rules.json` file of each analyzer.

## Contribute to Ballerina

As an open-source project, Ballerina welcomes contributions from the community.

For more information, go to the [contribution guidelines](https://github.com/ballerina-platform/ballerina-lang/blob/master/CONTRIBUTING.md).

## Code of conduct

All the contributors are encouraged to read the [Ballerina Code of Conduct](https://ballerina.io/code-of-conduct).

## Useful links

* Chat live with us via our [Discord server](https://discord.gg/ballerinalang).
* Post all technical questions on Stack Overflow with the [#ballerina](https://stackoverflow.com/questions/tagged/ballerina) tag.