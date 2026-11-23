# Writing rule-info.json entries

`rule-info.json` describes each scan rule to SonarQube. It is maintained at `<RULE_INFO_LOCATION>` (TBD).

Each entry of `rule-info.json` describes a rule defined in the `rules.json` file of an analyzer: the core rules in
`scan-command/src/main/resources/core-rules/rules.json` of the scan tool, and the library rules in
`compiler-plugin/src/main/resources/rules.json` of each library, such as `module-ballerina-http`.

## How to decide a value

Resolve the fields of a `rule-info.json` entry in this order:

1. Apply the [fields defined by the rule](#fields-defined-by-the-rule) directly from `rules.json`. The rule author has
   already decided these, so they take precedence over the SonarQube documentation.
2. Write the [fields documented by the rule](#fields-documented-by-the-rule) from `rules.json` and the documentation
   of the rule on Ballerina Central, adapted to the SonarQube style.
3. Decide the [remaining fields](#fields-without-a-mapping) from the SonarQube documentation listed in
   [References](#references).

## Fields defined by the rule

Apply these as they are in `rules.json`, irrespective of the SonarQube documentation:

| rule-info.json field | rules.json field | Mapping |
|----------------------|------------------|---------|
| `sqKey` | `id` | `ballerina:<id>` for a core rule, and `<org>/<module>:<id>` for a library rule, such as `ballerina/http:1`. Required. |
| `type` | `kind` | Same value: `CODE_SMELL`, `BUG` or `VULNERABILITY`. |
| `defaultSeverity` | `severity` | See [Severity](#severity). |
| `impacts[]` for the quality of `kind` | `kind`, `severity` | An impact on the quality of `kind`: `CODE_SMELL` → `MAINTAINABILITY`, `BUG` → `RELIABILITY`, `VULNERABILITY` → `SECURITY`, with `severity` mapped as in [Severity](#severity). Impacts on other qualities are decided from the documentation. |
| `securityStandards.cwe` | `standards.cwe` | Same list of CWE numbers. |
| `securityStandards.owaspTop10_2025` | `standards.owasp` | The `categories` of the entry whose `year` is `2025`, written as `A01` to `A10`. Leave out the categories of other years. |
| `tags` | `tags`, `standards` | `tags`, followed by `cwe` and `cwe-<n>` for each CWE number, and `owasp-2025-a<nn>` for each OWASP Top 10:2025 category, such as `owasp-2025-a01`. Use lowercase tags. Further tags may be added. |
| `helpUri` | `helpUri` | Same URL. |

## Fields documented by the rule

Base these on the rule's `rules.json` entry, paired with the documentation of the rule on Ballerina Central, and adapt
the wording to the SonarQube style without changing what the rule says:

| rule-info.json field | Source | Mapping |
|----------------------|--------|---------|
| `title` | `name` | `name`, reworded in the [SonarQube style](https://docs.sonarsource.com/sonarqube-community-build/extension-guide/adding-coding-rules/#guidelines-when-writing-rules-for-issues) that states what should or should not be done, such as `"checkpanic" should not be used` for `Avoid checkpanic`. Required. |
| `description.whyIsThisAnIssue` | `fullDescription`, `description`, Ballerina Central | `fullDescription`, or `description` if `fullDescription` is not set, together with the explanation of the rule on Ballerina Central. |
| `description.howToFix` | Ballerina Central | The noncompliant and compliant code examples of the rule, with their descriptions. |
| `description.moreInfo` | Ballerina Central | Extra links of the rule. Links to `helpUri`, CWE and OWASP pages are not needed, since the plugin adds them. |

### Severity

`severity` in `rules.json` uses the severities of the multi-quality rule (MQR) mode, which `impacts[].severity` uses as
is. `defaultSeverity` uses the severities of the Standard Experience:

| rules.json `severity` | `impacts[].severity` | `defaultSeverity` |
|-----------------------|----------------------|-------------------|
| `BLOCKER` | `BLOCKER` | `Blocker` |
| `HIGH` | `HIGH` | `Critical` |
| `MEDIUM` | `MEDIUM` | `Major` |
| `LOW` | `LOW` | `Minor` |
| `INFO` | `INFO` | `Info` |

A rule can impact more than one software quality. Besides the impact on the quality of `kind`, add an `impacts` entry
for each other quality the rule affects, with the severity decided from the [software qualities](#references)
documentation.

## Fields without a mapping

These fields have no counterpart in `rules.json`, so decide them from the SonarQube documentation:

| Field | Values |
|-------|--------|
| `cleanCodeAttribute` | One of the clean code attributes, such as `CONVENTIONAL`, `CLEAR`, `LOGICAL`, `COMPLETE` or `TRUSTWORTHY`. |
| `status` | `ready`, `beta` or `deprecated`. |
| `scope` | `Main`, `Tests` or `All`, the sources the rule applies to. |
| `remediation` | `func` is `Constant/Issue` with `constantCost`, `Linear` with `linearFactor`, or `Linear with offset` with `linearFactor` and `linearOffset`. Efforts are durations such as `5min` or `1h`. |

### Fields left out

Since `rules.json` does not enforce these fields, an entry can leave them out. A field left out takes the default
below, which may not suit the rule, so prefer deciding it from the documentation:

| Field | Default |
|-------|---------|
| `cleanCodeAttribute` | `CONVENTIONAL`, the SonarQube default |
| `status` | `ready` |
| `scope` | `All` |
| `remediation` | None |

## References

- [Rules](https://docs.sonarsource.com/sonarqube-community-build/quality-standards-administration/managing-rules/rules/):
  rule types, and the severities of the Standard Experience and the MQR mode.
- [Software qualities](https://docs.sonarsource.com/sonarqube-community-build/quality-standards-administration/managing-rules/software-qualities/):
  the software qualities a rule impacts, and the severity of each impact.
- [Clean code definition](https://docs.sonarsource.com/sonarqube-community-build/core-concepts/clean-code/definition/):
  the clean code attributes and their categories.
- [Adding coding rules](https://docs.sonarsource.com/sonarqube-community-build/extension-guide/adding-coding-rules/):
  rule titles and rule metadata, including the remediation effort.
- [OWASP Top 10:2025](https://owasp.org/Top10/2025/) and [CWE](https://cwe.mitre.org/) for the security standards.
