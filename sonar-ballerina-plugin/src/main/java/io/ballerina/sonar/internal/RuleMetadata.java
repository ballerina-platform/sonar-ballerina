/*
 * Copyright (c) 2025, WSO2 LLC. (https://www.wso2.com).
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

import org.sonar.api.issue.impact.Severity;
import org.sonar.api.issue.impact.SoftwareQuality;
import org.sonar.api.rule.RuleScope;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rules.CleanCodeAttribute;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.debt.DebtRemediationFunction;
import org.sonar.api.server.rule.RulesDefinition.OwaspTop10;

import java.util.List;
import java.util.Map;

/**
 * A rule of the Ballerina rule repository, validated and ready to be registered in SonarQube.
 *
 * @param id                 the rule key
 * @param name               the rule title
 * @param type               the rule type shown in the standard experience
 * @param status             the lifecycle status of the rule
 * @param severity           the rule severity shown in the standard experience, one of
 *                           {@link org.sonar.api.rule.Severity#ALL}
 * @param impacts            the software qualities the rule impacts, shown in the multi-quality rule mode
 * @param cleanCodeAttribute the clean code attribute of the rule, or {@code null} for the SonarQube default
 * @param scope              the kind of sources the rule applies to
 * @param remediation        the effort to fix an issue, or {@code null} if unknown
 * @param tags               the rule tags
 * @param cwe                the CWE identifiers of the rule
 * @param owaspTop10Y2025    the OWASP Top 10:2025 categories of the rule
 * @param description        the description of the rule, as SonarQube description sections
 */
public record RuleMetadata(String id, String name, RuleType type, RuleStatus status, String severity,
                           Map<SoftwareQuality, Severity> impacts, CleanCodeAttribute cleanCodeAttribute,
                           RuleScope scope, Remediation remediation, List<String> tags, List<Integer> cwe,
                           List<OwaspTop10> owaspTop10Y2025, RuleDescription description) {

    public RuleMetadata {
        impacts = Map.copyOf(impacts);
        tags = List.copyOf(tags);
        cwe = List.copyOf(cwe);
        owaspTop10Y2025 = List.copyOf(owaspTop10Y2025);
    }

    /**
     * The effort to fix an issue, as a SonarQube debt remediation function.
     *
     * @param type            the kind of function
     * @param baseEffort      the effort per issue, such as {@code 5min}, or {@code null} for a linear function
     * @param gapMultiplier   the effort per unit of the issue gap, or {@code null} for a constant function
     * @param gapDescription  what the unit of the issue gap is, or {@code null} if not described
     */
    public record Remediation(DebtRemediationFunction.Type type, String baseEffort, String gapMultiplier,
                              String gapDescription) {
    }

    /**
     * The HTML of the description sections that SonarQube shows as the "Why is this an issue?", "How can I fix it?"
     * and "More info" tabs of a rule. A section is {@code null} when the rule has nothing to show in it.
     *
     * @param rootCause the "Why is this an issue?" section
     * @param howToFix  the "How can I fix it?" section
     * @param resources the "More info" section
     */
    public record RuleDescription(String rootCause, String howToFix, String resources) {

        /**
         * Joins the sections into a single HTML description, for SonarQube clients that do not show the sections.
         *
         * @return the HTML of all sections, each under its own heading
         */
        public String toHtml() {
            StringBuilder html = new StringBuilder();
            appendSection(html, "Why is this an issue?", rootCause);
            appendSection(html, "How to fix it", howToFix);
            appendSection(html, "Resources", resources);
            return html.toString().strip();
        }

        private static void appendSection(StringBuilder html, String heading, String content) {
            if (content != null) {
                html.append("<h2>").append(heading).append("</h2>\n").append(content).append('\n');
            }
        }
    }
}
