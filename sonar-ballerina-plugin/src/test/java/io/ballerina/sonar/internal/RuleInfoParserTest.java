/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.sonar.internal;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import io.ballerina.sonar.SonarBallerinaException;
import org.sonar.api.issue.impact.Severity;
import org.sonar.api.issue.impact.SoftwareQuality;
import org.sonar.api.rule.RuleScope;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rules.CleanCodeAttribute;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.debt.DebtRemediationFunction;
import org.sonar.api.server.rule.RulesDefinition.OwaspTop10;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RuleInfoParserTest {

    @Test(description = "The bundled rule-info.json documents every rule in all three description sections")
    void testBundledRuleInfo() throws SonarBallerinaException {
        List<RuleMetadata> rules = RuleInfoParser.parse(RuleGenerator.readBundledResource("/rule-info/rule-info.json"));
        Assert.assertFalse(rules.isEmpty());
        for (RuleMetadata rule : rules) {
            Assert.assertNotNull(rule.description().rootCause(), rule.id());
            Assert.assertFalse(rule.description().rootCause().contains("Visit <a"), rule.id() + " is undocumented");
            Assert.assertNotNull(rule.description().howToFix(), rule.id());
            Assert.assertTrue(rule.description().howToFix().contains("data-diff-type=\"noncompliant\"")
                    || rule.description().howToFix().contains("<pre>"), rule.id());
            Assert.assertNotNull(rule.description().resources(), rule.id());
            Assert.assertFalse(rule.impacts().isEmpty(), rule.id());
            Assert.assertNotNull(rule.cleanCodeAttribute(), rule.id());
        }

        RuleMetadata checkpanic = rules.stream().filter(r -> r.id().equals("ballerina:1")).findFirst().orElseThrow();
        Assert.assertEquals(checkpanic.type(), RuleType.CODE_SMELL);
        Assert.assertEquals(checkpanic.severity(), "MINOR");
        Assert.assertEquals(checkpanic.impacts(), Map.of(SoftwareQuality.MAINTAINABILITY, Severity.LOW));
        Assert.assertEquals(checkpanic.cwe(), List.of(248, 636));
        Assert.assertEquals(checkpanic.owaspTop10Y2025(), List.of(OwaspTop10.A10));
        Assert.assertNull(checkpanic.remediation());
        Assert.assertEquals(checkpanic.scope(), RuleScope.ALL);
        Assert.assertEquals(checkpanic.status(), RuleStatus.READY);
        Assert.assertEquals(checkpanic.tags(),
                List.of("error-handling", "cwe", "cwe-248", "cwe-636", "owasp-2025-a10"));
    }

    @Test(description = "The derived links of every bundled rule cover all the links listed in rule-info.json")
    void testBundledLinksAreDerived() throws SonarBallerinaException {
        String ruleInfo = RuleGenerator.readBundledResource("/rule-info/rule-info.json");
        JsonArray withoutLinks = JsonParser.parseString(ruleInfo).getAsJsonArray();
        withoutLinks.forEach(rule -> rule.getAsJsonObject().getAsJsonObject("description").remove("moreInfo"));
        List<RuleMetadata> rules = RuleInfoParser.parse(ruleInfo);
        List<RuleMetadata> derived = RuleInfoParser.parse(withoutLinks.toString());
        for (int i = 0; i < rules.size(); i++) {
            Assert.assertEquals(derived.get(i).description().resources(), rules.get(i).description().resources(),
                    rules.get(i).id());
        }
    }

    @Test(description = "Every field of a rule is mapped to its SonarQube counterpart")
    void testParseRule() throws SonarBallerinaException {
        RuleMetadata rule = RuleInfoParser.parse("""
                [{
                  "title": " Avoid X ", "type": "VULNERABILITY", "defaultSeverity": "Critical",
                  "sqKey": "ballerina/x:1", "status": "beta", "quickfix": "unknown", "ruleSpecification": "N/A",
                  "scope": "Main", "remediation": {"func": "Constant/Issue", "constantCost": "10min"},
                  "tags": ["a", " ", "b"],
                  "cleanCodeAttribute": "trustworthy",
                  "impacts": [{"softwareQuality": "SECURITY", "severity": "BLOCKER"},
                              {"softwareQuality": "RELIABILITY", "severity": "LOW"}],
                  "securityStandards": {"cwe": [22], "owaspTop10_2025": ["A01", "A5"]},
                  "description": {"whyIsThisAnIssue": "Because."}
                }]
                """).get(0);
        Assert.assertEquals(rule.id(), "ballerina/x:1");
        Assert.assertEquals(rule.name(), "Avoid X");
        Assert.assertEquals(rule.type(), RuleType.VULNERABILITY);
        Assert.assertEquals(rule.severity(), "CRITICAL");
        Assert.assertEquals(rule.scope(), RuleScope.MAIN);
        Assert.assertEquals(rule.status(), RuleStatus.BETA);
        Assert.assertEquals(rule.remediation(), new RuleMetadata.Remediation(
                DebtRemediationFunction.Type.CONSTANT_ISSUE, "10min", null, null));
        Assert.assertEquals(rule.tags(), List.of("a", "b"));
        Assert.assertEquals(rule.cleanCodeAttribute(), CleanCodeAttribute.TRUSTWORTHY);
        Assert.assertEquals(rule.impacts(), Map.of(SoftwareQuality.SECURITY, Severity.BLOCKER,
                SoftwareQuality.RELIABILITY, Severity.LOW));
        Assert.assertEquals(rule.cwe(), List.of(22));
        Assert.assertEquals(rule.owaspTop10Y2025(), List.of(OwaspTop10.A1, OwaspTop10.A5));
        Assert.assertEquals(rule.description().rootCause(), "<p>Because.</p>");
        Assert.assertNull(rule.description().howToFix());
        Assert.assertTrue(rule.description().resources().contains("data/definitions/22.html"),
                rule.description().resources());
        Assert.assertTrue(rule.description().resources().contains("A05 Injection"), rule.description().resources());
    }

    @Test(description = "Missing and invalid values fall back to defaults, and impacts are derived from the type")
    void testDefaults() throws SonarBallerinaException {
        RuleMetadata rule = RuleInfoParser.parse("""
                [{"title": "T", "sqKey": "ballerina:9", "type": "nope", "defaultSeverity": "Huge",
                  "scope": "everywhere", "cleanCodeAttribute": "SHINY", "status": "removed",
                  "remediation": {"func": "Exponential", "constantCost": "1min"},
                  "impacts": [{"softwareQuality": "SPEED", "severity": "HIGH"}],
                  "securityStandards": {"owaspTop10_2025": ["A11", "B1"]}}]
                """).get(0);
        Assert.assertEquals(rule.type(), RuleType.CODE_SMELL);
        Assert.assertEquals(rule.severity(), "MAJOR");
        Assert.assertEquals(rule.scope(), RuleScope.ALL);
        Assert.assertNull(rule.cleanCodeAttribute());
        Assert.assertNull(rule.remediation());
        Assert.assertEquals(rule.status(), RuleStatus.READY);
        Assert.assertEquals(rule.impacts(), Map.of(SoftwareQuality.MAINTAINABILITY, Severity.MEDIUM));
        Assert.assertTrue(rule.owaspTop10Y2025().isEmpty());
        Assert.assertTrue(rule.tags().isEmpty());
        Assert.assertTrue(rule.description().rootCause().contains("https://ballerina.io/learn/scan-rules/"));
    }

    @Test(description = "Linear remediation functions are mapped with their gap description")
    void testLinearRemediation() throws SonarBallerinaException {
        List<RuleMetadata> rules = RuleInfoParser.parse("""
                [{"title": "L", "sqKey": "ballerina:1",
                  "remediation": {"func": "Linear", "linearFactor": "2min", "linearDesc": "per param"}},
                 {"title": "O", "sqKey": "ballerina:2", "remediation": {"func": "Linear with offset",
                  "linearFactor": "2min", "linearOffset": "5min"}},
                 {"title": "M", "sqKey": "ballerina:3", "remediation": {"func": "Linear"}},
                 {"title": "E", "sqKey": "ballerina:4", "remediation": {}},
                 {"title": "U", "sqKey": "ballerina:5", "remediation": {"func": "Square", "constantCost": "5min"}}]
                """);
        Assert.assertEquals(rules.get(0).remediation(), new RuleMetadata.Remediation(
                DebtRemediationFunction.Type.LINEAR, null, "2min", "per param"));
        Assert.assertEquals(rules.get(1).remediation(), new RuleMetadata.Remediation(
                DebtRemediationFunction.Type.LINEAR_OFFSET, "5min", "2min", null));
        Assert.assertNull(rules.get(2).remediation());
        Assert.assertNull(rules.get(3).remediation());
        Assert.assertNull(rules.get(4).remediation());
    }

    @Test(description = "Rules without a key or title, and duplicate rules, are skipped")
    void testInvalidRulesAreSkipped() throws SonarBallerinaException {
        List<RuleMetadata> rules = RuleInfoParser.parse("""
                [{"title": "No key"}, {"sqKey": "ballerina:1"}, null,
                 {"title": "First", "sqKey": "ballerina:2"}, {"title": "Second", "sqKey": "ballerina:2"},
                 {"title": "Third", "sqKey": " ballerina:2 "}]
                """);
        Assert.assertEquals(rules.size(), 1);
        Assert.assertEquals(rules.get(0).name(), "First");
    }

    @Test(description = "A file with a value that SonarQube would reject is rejected as a whole")
    void testValuesSonarQubeRejects() {
        for (String rule : List.of(
                "{\"title\": \"T\", \"sqKey\": \"ballerina:9\", \"tags\": [\"Error-Handling\"]}",
                "{\"title\": \"T\", \"sqKey\": \"ballerina:9\", \"remediation\": {\"constantCost\": \"5 minutes\"}}",
                "{\"title\": \"T\", \"sqKey\": \"ballerina:9\", \"remediation\": {\"func\": \"Linear\", "
                        + "\"linearFactor\": \"often\"}}")) {
            String json = "[{\"title\": \"Valid\", \"sqKey\": \"ballerina:1\"}, " + rule + "]";
            try {
                RuleInfoParser.parse(json);
                Assert.fail("Accepted " + rule);
            } catch (SonarBallerinaException e) {
                Assert.assertTrue(e.getMessage().contains("ballerina:9"), e.getMessage());
            }
        }
    }

    @Test(description = "Content that is not a list of valid rules is rejected")
    void testInvalidContent() {
        for (String json : List.of("{\"title\": \"x\"}", "[", "", "[]", "[{\"title\": \"x\"}]")) {
            Assert.assertThrows(SonarBallerinaException.class, () -> RuleInfoParser.parse(json));
        }
    }

    @Test(description = "The rule-info.json URL is bundled, can be overridden by a valid HTTP(S) URL, and is "
            + "followed by the fallback URL")
    void testRuleInfoUris() throws SonarBallerinaException {
        List<URI> bundled = RuleGenerator.getRuleInfoUris(Optional.empty(), Map.of());
        Assert.assertFalse(bundled.isEmpty());
        for (URI uri : bundled) {
            Assert.assertEquals(uri.getScheme(), "https");
            Assert.assertTrue(uri.getPath().endsWith("/rule-info.json"), uri.toString());
        }
        URI official = bundled.get(bundled.size() - 1);
        Assert.assertEquals(RuleGenerator.getRuleInfoUris(Optional.of(" "), Map.of()), bundled);
        Assert.assertEquals(RuleGenerator.getRuleInfoUris(Optional.of("http://localhost:8080/rule-info.json"),
                        Map.of()),
                List.of(URI.create("http://localhost:8080/rule-info.json"), official));
        Assert.assertEquals(RuleGenerator.getRuleInfoUris(Optional.of(official.toString()), Map.of()),
                List.of(official));
        Assert.assertEquals(RuleGenerator.getRuleInfoUris(Optional.of(official.toString()), Map.of("a", "b")),
                List.of(official));
        Assert.assertEquals(RuleGenerator.getRuleInfoUris(Optional.of("http://localhost:8080/rule-info.json"),
                        Map.of("a", "b c")),
                List.of(URI.create("http://localhost:8080/rule-info.json?a=b+c"), official));
        for (String invalid : List.of("file:///etc/passwd", "https://exa mple.com", "example.com/rule-info.json",
                "https:foo")) {
            Assert.assertEquals(RuleGenerator.getRuleInfoUris(Optional.of(invalid), Map.of()), bundled, invalid);
        }
    }
}
