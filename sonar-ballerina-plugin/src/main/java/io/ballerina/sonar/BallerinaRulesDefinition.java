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

import io.ballerina.sonar.internal.RuleGenerator;
import io.ballerina.sonar.internal.RuleMetadata;
import io.ballerina.sonar.internal.RuleMetadata.Remediation;
import io.ballerina.sonar.internal.RuleMetadata.RuleDescription;
import org.sonar.api.SonarRuntime;
import org.sonar.api.config.Configuration;
import org.sonar.api.platform.Server;
import org.sonar.api.server.rule.RuleDescriptionSectionBuilder;
import org.sonar.api.server.rule.RulesDefinition;

import java.util.List;

import static io.ballerina.sonar.Constants.LANGUAGE_KEY;
import static io.ballerina.sonar.Constants.RULE_REPOSITORY_KEY;
import static io.ballerina.sonar.Constants.RULE_REPOSITORY_NAME;
import static org.sonar.api.server.rule.RuleDescriptionSection.RuleDescriptionSectionKeys.HOW_TO_FIX_SECTION_KEY;
import static org.sonar.api.server.rule.RuleDescriptionSection.RuleDescriptionSectionKeys.RESOURCES_SECTION_KEY;
import static org.sonar.api.server.rule.RuleDescriptionSection.RuleDescriptionSectionKeys.ROOT_CAUSE_SECTION_KEY;

public class BallerinaRulesDefinition implements RulesDefinition {

    private final Configuration configuration;
    private final Server server;
    private final SonarRuntime runtime;

    public BallerinaRulesDefinition(Configuration configuration, Server server, SonarRuntime runtime) {
        this.configuration = configuration;
        this.server = server;
        this.runtime = runtime;
    }

    @Override
    public void define(Context context) {
        NewRepository repository = context.createRepository(RULE_REPOSITORY_KEY, LANGUAGE_KEY);
        repository.setName(RULE_REPOSITORY_NAME);
        List<RuleMetadata> rules;
        try {
            rules = RuleGenerator.getInstance().loadRules(configuration, server, runtime);
        } catch (SonarBallerinaException e) {
            throw new IllegalStateException("Error generating rules for Ballerina", e);
        }
        for (RuleMetadata rule : rules) {
            defineRule(repository, rule);
        }
        repository.done();
        RuleGenerator.getInstance().cacheFetchedRules();
    }

    static void defineRule(NewRepository repository, RuleMetadata rule) {
        NewRule newRule = repository.createRule(rule.id())
                .setName(rule.name())
                .setType(rule.type())
                .setStatus(rule.status())
                .setSeverity(rule.severity())
                .setScope(rule.scope())
                .addTags(rule.tags().toArray(String[]::new));
        rule.impacts().forEach(newRule::addDefaultImpact);
        if (rule.cleanCodeAttribute() != null) {
            newRule.setCleanCodeAttribute(rule.cleanCodeAttribute());
        }
        Remediation remediation = rule.remediation();
        if (remediation != null) {
            DebtRemediationFunctions functions = newRule.debtRemediationFunctions();
            newRule.setDebtRemediationFunction(switch (remediation.type()) {
                case CONSTANT_ISSUE -> functions.constantPerIssue(remediation.baseEffort());
                case LINEAR -> functions.linear(remediation.gapMultiplier());
                case LINEAR_OFFSET -> functions.linearWithOffset(remediation.gapMultiplier(),
                        remediation.baseEffort());
            });
            if (remediation.gapDescription() != null) {
                newRule.setGapDescription(remediation.gapDescription());
            }
        }
        if (!rule.cwe().isEmpty()) {
            newRule.addCwe(rule.cwe().stream().mapToInt(Integer::intValue).toArray());
        }
        if (!rule.owaspTop10Y2025().isEmpty()) {
            newRule.addOwaspTop10(OwaspTop10Version.Y2025, rule.owaspTop10Y2025().toArray(OwaspTop10[]::new));
        }

        RuleDescription description = rule.description();
        // SonarQube still requires the legacy description, though it shows the sections when a rule has them
        newRule.setHtmlDescription(description.toHtml());
        addDescriptionSection(newRule, ROOT_CAUSE_SECTION_KEY, description.rootCause());
        addDescriptionSection(newRule, HOW_TO_FIX_SECTION_KEY, description.howToFix());
        addDescriptionSection(newRule, RESOURCES_SECTION_KEY, description.resources());
    }

    private static void addDescriptionSection(NewRule rule, String sectionKey, String html) {
        if (html != null) {
            rule.addDescriptionSection(new RuleDescriptionSectionBuilder()
                    .sectionKey(sectionKey)
                    .htmlContent(html)
                    .build());
        }
    }
}
