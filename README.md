# Ballerina SonarQube Plugin

## Overview

SonarQube is an open-source code quality and security platform. It analyzes code for issues, tracks metrics, and integrates with CI/CD. This helps developers write cleaner, more secure code. [SonarQube plugins](https://docs.sonarsource.com/sonarqube/latest/extension-guide/developing-a-plugin/supporting-new-languages/) allow developers to extend SonarQube analysis  support for  new languages.The sonar-ballerina plugin extends SonarQube to analyze Ballerina code. It leverages the Ballerina static code analysis tool to identify issues and report them to SonarQube.

## Prerequisites

1. OpenJDK 21 ([Adopt OpenJDK](https://adoptium.net/temurin/releases/?version=21) or any other OpenJDK distribution)

2. [Ballerina](https://ballerina.io/)

3. [Ballerina Static Code Analysis Tool](https://github.com/ballerina-platform/static-code-analysis-tool)

4. SonarQube with Sonar Plugin API 13.4 or later. The plugin is tested on
   [SonarQube Community Build 26.11](https://www.sonarsource.com/products/sonarqube/downloads/).

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

Both `bal` and `sonar-scanner` must be on the `PATH`.

## Installing the plugins

The repository builds two plugins:

| Plugin | Built at | Used by |
|---|---|---|
| `sonar-ballerina-plugin` | `sonar-ballerina-plugin/build/libs/sonar-ballerina-plugin-<version>.jar` | The SonarQube server. It defines the Ballerina language, rules and quality profile, and reports the issues found. |
| `sonar-platform-plugin` | `sonar-platform-plugin/build/libs/sonar-platform-plugin-<version>.jar` | The `bal scan` tool. It saves the issues found and runs `sonar-scanner` to upload them. |

1. Build both plugins with `./gradlew build`.
2. Copy `sonar-ballerina-plugin-<version>.jar` to `<SONARQUBE_HOME>/extensions/plugins`, and restart the SonarQube
   server.
3. Keep `sonar-platform-plugin-<version>.jar` where your Ballerina projects can refer to it, as shown below.

## Configuring a Ballerina project

A Ballerina project needs a `Scan.toml` and a `sonar-project.properties` file to report to SonarQube.

### `Scan.toml`

The scan tool reads `Scan.toml` from the project root. To keep it elsewhere, set its path in `Ballerina.toml`:

```toml
[scan]
configPath = "config/Scan.toml"
```

A `[scan]` table without `configPath` is an error, so leave the table out when `Scan.toml` is at the project root.

In `Scan.toml`, register the SonarQube platform plugin:

```toml
[[platform]]
name = "sonarqube"
path = "/path/to/sonar-platform-plugin-<version>.jar"
# Optional: the sonar-project.properties file to use instead of the one at the project root
sonarProjectPropertiesPath = "/path/to/sonar-project.properties"
```

| Field | Required | Description |
|---|---|---|
| `name` | Yes | Must be `sonarqube`. |
| `path` | Yes | The path of `sonar-platform-plugin-<version>.jar`, absolute or relative to the project root. |
| `sonarProjectPropertiesPath` | No | The path of the properties file, passed to `sonar-scanner` as `project.settings`. A relative path is looked up next to `Scan.toml` first, then in the project. When it is not set, `sonar-scanner` reads `sonar-project.properties` from the directory `bal scan` runs in, so run it from the project root. |

`Scan.toml` can also add external analyzers with `[[analyzer]]` entries. Their issues appear in SonarQube as external
issues. See the [scan tool documentation](https://github.com/ballerina-platform/static-code-analysis-tool) for these
and the other fields of `Scan.toml`.

### `sonar-project.properties`

Tell `sonar-scanner` which SonarQube server and project to report to:

```properties
sonar.projectKey=my_ballerina_project
sonar.host.url=http://localhost:9000
sonar.projectName=My Ballerina Project
sonar.sources=.
```

| Property | Required | Description |
|---|---|---|
| `sonar.projectKey` | Yes | The unique key of the project in SonarQube. The project is created on the first analysis if it does not exist. |
| `sonar.token` | Yes, unless anonymous analysis is allowed | A token to authenticate with the server. Set the `SONAR_TOKEN` environment variable instead, so that the token is not committed. |
| `sonar.host.url` | No | The URL of the SonarQube server. Defaults to `http://localhost:9000`. |
| `sonar.projectName` | No | The name shown in SonarQube. Defaults to the project key. |

When run through `bal scan`, the platform plugin sets these properties on the `sonar-scanner` command line, which
overrides any value set for them in `sonar-project.properties`:

| Property | Value |
|---|---|
| `sonar.exclusions` | Non-Ballerina files, such as `**/*.java`, `**/*.xml` and `**/*.py`, and the Ballerina test directories `tests/**` and `modules/*/tests/**`. |
| `sonar.tests` | `.` |
| `sonar.test.inclusions` | `tests/**,modules/*/tests/**`, so that the Ballerina tests are indexed as test code, not as source code. |
| `sonar.test.exclusions` | The same non-Ballerina files as `sonar.exclusions`. |
| `sonar.projectVersion` | The `version` of the `[package]` table in `Ballerina.toml`. It is not set when `Ballerina.toml` has no version. |
| `analyzedResultsPath` | The path of the issues found by `bal scan`. When it is set, the plugin reports the issues in that file instead of running `bal scan` again. |

When you run `sonar-scanner` directly, these properties are not set for you, so set the test directories in
`sonar-project.properties` if you want them indexed as test code.

The issues are saved to `ballerina-static-code-analysis-results.json` at the project root, so add it to `.gitignore`.

## Analyzing a Ballerina project

Run either of these commands from the project root:

- `bal scan --platforms=sonarqube` analyzes the project, and runs `sonar-scanner` to upload the issues found.
- `sonar-scanner` runs `bal scan --platform-triggered --platforms=sonarqube` to analyze the project, and uploads the
  issues found. Use it when `sonar-scanner` already runs in your CI pipeline.

Once the analysis is done, the issues appear in the project on the SonarQube server.

### Workspaces

A workspace `Scan.toml` cannot have `[[platform]]` entries, so give each member package its own `Scan.toml` with the
`sonarqube` entry. `bal scan` runs `sonar-scanner` once for each member, always in the directory `bal scan` runs in.
When you run it from the workspace root, every member reads the workspace root's `sonar-project.properties` and reports
to the same SonarQube project, so each member's analysis replaces the previous one. To report each member to its own
project, set `sonarProjectPropertiesPath` in each member's `Scan.toml` to a properties file with its own
`sonar.projectKey`, or run `bal scan --platforms=sonarqube` from each member's directory.

Running `sonar-scanner` from the workspace root reports only the issues of the last member scanned, since every member
writes its issues to the same `ballerina-static-code-analysis-results.json`.

## Contribute to Ballerina

As an open-source project, Ballerina welcomes contributions from the community.

For more information, go to the [contribution guidelines](https://github.com/ballerina-platform/ballerina-lang/blob/master/CONTRIBUTING.md).

## Code of conduct

All the contributors are encouraged to read the [Ballerina Code of Conduct](https://ballerina.io/code-of-conduct).

## Useful links

* Chat live with us via our [Discord server](https://discord.gg/ballerinalang).
* Post all technical questions on Stack Overflow with the [#ballerina](https://stackoverflow.com/questions/tagged/ballerina) tag.