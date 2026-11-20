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

package io.ballerina.sonar;

import io.ballerina.sonar.internal.RuleInfoParser;
import io.ballerina.sonar.internal.RuleMetadata;
import org.sonar.api.impl.server.RulesDefinitionContext;
import org.sonar.api.issue.impact.Severity;
import org.sonar.api.issue.impact.SoftwareQuality;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rules.CleanCodeAttribute;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RuleDescriptionSection;
import org.sonar.api.server.rule.RulesDefinition;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.ballerina.sonar.Constants.LANGUAGE_KEY;
import static io.ballerina.sonar.Constants.RULE_REPOSITORY_KEY;

public class BallerinaRulesDefinitionTest {

    @Test(description = "Rules are registered with description sections, impacts and security standards")
    void testDefineRules() throws IOException, SonarBallerinaException {
        List<RuleMetadata> rules = RuleInfoParser.parse(readBundledRuleInfo());
        RulesDefinition.Context context = new RulesDefinitionContext();
        RulesDefinition.NewRepository newRepository = context.createRepository(RULE_REPOSITORY_KEY, LANGUAGE_KEY);
        rules.forEach(rule -> BallerinaRulesDefinition.defineRule(newRepository, rule));
        newRepository.done();

        RulesDefinition.Repository repository = context.repository(RULE_REPOSITORY_KEY);
        Assert.assertNotNull(repository);
        Assert.assertEquals(repository.rules().size(), rules.size());

        RulesDefinition.Rule rule = repository.rule("ballerina/crypto:1");
        Assert.assertNotNull(rule);
        Assert.assertEquals(rule.type(), RuleType.VULNERABILITY);
        Assert.assertEquals(rule.severity(), "CRITICAL");
        Assert.assertEquals(rule.defaultImpacts(), Map.of(SoftwareQuality.SECURITY, Severity.HIGH));
        Assert.assertEquals(rule.cleanCodeAttribute(), CleanCodeAttribute.TRUSTWORTHY);
        Assert.assertTrue(rule.securityStandards().containsAll(List.of("cwe:327", "cwe:780", "owaspTop10-2025:a4")),
                rule.securityStandards().toString());
        Assert.assertNull(rule.debtRemediationFunction());
        Assert.assertEquals(rule.status(), RuleStatus.READY);
        Assert.assertTrue(rule.tags().containsAll(List.of("cwe-327", "owasp-2025-a04")), rule.tags().toString());
        String htmlDescription = Objects.requireNonNullElse(rule.htmlDescription(), "");
        Assert.assertTrue(htmlDescription.startsWith("<h2>Why is this an issue?</h2>"), htmlDescription);
        Assert.assertEquals(rule.ruleDescriptionSections().stream().map(RuleDescriptionSection::getKey).toList(),
                List.of("root_cause", "how_to_fix", "resources"));
        String howToFix = rule.ruleDescriptionSections().get(1).getHtmlContent();
        Assert.assertTrue(howToFix.contains("<h3>AES Encryption</h3>"), howToFix);
        Assert.assertTrue(howToFix.contains("data-diff-type=\"compliant\""), howToFix);
    }

    private static String readBundledRuleInfo() throws IOException {
        try (InputStream in = BallerinaRulesDefinitionTest.class.getResourceAsStream("/rule-info/rule-info.json")) {
            Assert.assertNotNull(in, "rule-info.json is not bundled");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
