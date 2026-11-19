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

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import io.ballerina.sonar.SonarBallerinaException;
import io.ballerina.sonar.internal.RuleMetadata.Remediation;
import io.ballerina.sonar.internal.ScanToolRuleInfo.Impact;
import io.ballerina.sonar.internal.ScanToolRuleInfo.SecurityStandards;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.issue.impact.Severity;
import org.sonar.api.issue.impact.SoftwareQuality;
import org.sonar.api.rule.RuleScope;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rules.CleanCodeAttribute;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.debt.DebtRemediationFunction;
import org.sonar.api.server.rule.RuleTagFormat;
import org.sonar.api.server.rule.RulesDefinition.OwaspTop10;
import org.sonar.api.utils.Duration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the rule-info.json file of the scan tool into the rules of the Ballerina rule repository.
 */
public final class RuleInfoParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuleInfoParser.class);
    private static final Gson GSON = new Gson();
    private static final Pattern OWASP_CATEGORY = Pattern.compile("A0*(\\d{1,2})");
    private static final int OWASP_TOP_10_SIZE = 10;
    private static final String DEFAULT_SEVERITY = org.sonar.api.rule.Severity.MAJOR;
    private static final int HOURS_IN_DAY = 24;

    private RuleInfoParser() {
    }

    /**
     * Parses rule-info.json, skipping a rule that has no key or title, and falling back to defaults for invalid
     * values so that a single bad entry does not stop SonarQube from starting.
     *
     * @param json the content of rule-info.json
     * @return the rules, in the order of the file
     * @throws SonarBallerinaException if the content is not a JSON array of rules, has no valid rule, or has a tag
     *                                 or remediation effort that SonarQube would reject
     */
    public static List<RuleMetadata> parse(String json) throws SonarBallerinaException {
        List<ScanToolRuleInfo> ruleInfo;
        try {
            ruleInfo = GSON.fromJson(json, new TypeToken<List<ScanToolRuleInfo>>() { }.getType());
        } catch (JsonParseException | IllegalStateException e) {
            throw new SonarBallerinaException("Failed to parse rule-info.json", e);
        }
        if (ruleInfo == null) {
            throw new SonarBallerinaException("rule-info.json is empty");
        }
        List<RuleMetadata> rules = new ArrayList<>();
        Set<String> ruleKeys = new HashSet<>();
        for (ScanToolRuleInfo rule : ruleInfo) {
            if (rule == null || isBlank(rule.sqKey()) || isBlank(rule.title())) {
                LOGGER.warn("Skipping a rule in rule-info.json without a key or a title");
                continue;
            }
            if (!ruleKeys.add(rule.sqKey())) {
                LOGGER.warn("Skipping the duplicate rule {} in rule-info.json", rule.sqKey());
                continue;
            }
            RuleMetadata metadata = toRuleMetadata(rule);
            checkSonarQubeAccepts(metadata);
            rules.add(metadata);
        }
        if (rules.isEmpty()) {
            throw new SonarBallerinaException("rule-info.json has no valid rules");
        }
        return rules;
    }

    private static RuleMetadata toRuleMetadata(ScanToolRuleInfo rule) {
        String key = rule.sqKey().strip();
        RuleType type = parseEnum(RuleType.class, rule.type(), key, "type");
        if (type == null) {
            type = RuleType.CODE_SMELL;
        }
        String severity = parseSeverity(rule.defaultSeverity(), key);
        Map<SoftwareQuality, Severity> impacts = parseImpacts(rule.impacts(), key);
        if (impacts.isEmpty() && type != RuleType.SECURITY_HOTSPOT) {
            impacts.put(toSoftwareQuality(type), toImpactSeverity(severity));
        }
        SecurityStandards standards = rule.securityStandards();
        List<Integer> cwe = standards == null || standards.cwe() == null ? List.of()
                : standards.cwe().stream().filter(Objects::nonNull).distinct().toList();
        List<OwaspTop10> owasp = standards == null ? List.of()
                : parseOwaspCategories(standards.owaspTop10Y2025(), key);
        return new RuleMetadata(
                key,
                rule.title().strip(),
                type,
                parseStatus(rule.status(), key),
                severity,
                impacts,
                parseEnum(CleanCodeAttribute.class, rule.cleanCodeAttribute(), key, "clean code attribute"),
                parseScope(rule.scope(), key),
                parseRemediation(rule.remediation(), key),
                nonBlank(rule.tags()),
                cwe,
                owasp,
                RuleDescriptionRenderer.render(rule.description(), rule.title().strip(), rule.helpUri(), cwe, owasp));
    }

    // SonarQube fails to start on these values, so the whole file is rejected and the next source is tried instead
    private static void checkSonarQubeAccepts(RuleMetadata rule) throws SonarBallerinaException {
        for (String tag : rule.tags()) {
            if (!RuleTagFormat.isValid(tag)) {
                throw new SonarBallerinaException("The rule " + rule.id() + " has the invalid tag '" + tag + "'");
            }
        }
        Remediation remediation = rule.remediation();
        if (remediation != null) {
            checkEffort(remediation.baseEffort(), rule.id());
            checkEffort(remediation.gapMultiplier(), rule.id());
        }
    }

    private static void checkEffort(String effort, String key) throws SonarBallerinaException {
        if (effort == null) {
            return;
        }
        try {
            Duration.decode(effort, HOURS_IN_DAY);
        } catch (IllegalArgumentException e) {
            throw new SonarBallerinaException("The rule " + key + " has the invalid remediation effort '" + effort
                    + "'", e);
        }
    }

    private static RuleStatus parseStatus(String status, String key) {
        RuleStatus parsed = parseEnum(RuleStatus.class, status, key, "status");
        if (parsed == RuleStatus.REMOVED) {
            LOGGER.warn("Ignoring the status '{}' of the rule {}, which only SonarQube can set", status, key);
            return RuleStatus.READY;
        }
        return parsed == null ? RuleStatus.READY : parsed;
    }

    private static Remediation parseRemediation(ScanToolRuleInfo.Remediation remediation, String key) {
        if (remediation == null) {
            return null;
        }
        String constantCost = strip(remediation.constantCost());
        String linearFactor = strip(remediation.linearFactor());
        String linearOffset = strip(remediation.linearOffset());
        String linearDesc = strip(remediation.linearDesc());
        String func = remediation.func() == null ? "" : remediation.func().strip().toLowerCase(Locale.ROOT);
        Remediation parsed = switch (func) {
            case "constant/issue", "" -> constantCost == null ? null
                    : new Remediation(DebtRemediationFunction.Type.CONSTANT_ISSUE, constantCost, null, null);
            case "linear" -> linearFactor == null ? null
                    : new Remediation(DebtRemediationFunction.Type.LINEAR, null, linearFactor, linearDesc);
            case "linear with offset" -> linearFactor == null || linearOffset == null ? null
                    : new Remediation(DebtRemediationFunction.Type.LINEAR_OFFSET, linearOffset, linearFactor,
                    linearDesc);
            default -> null;
        };
        if (parsed == null) {
            LOGGER.warn("Ignoring the invalid remediation '{}' of the rule {}", remediation.func(), key);
        }
        return parsed;
    }

    private static Map<SoftwareQuality, Severity> parseImpacts(List<Impact> impacts, String key) {
        Map<SoftwareQuality, Severity> parsed = new EnumMap<>(SoftwareQuality.class);
        if (impacts == null) {
            return parsed;
        }
        for (Impact impact : impacts) {
            if (impact == null) {
                continue;
            }
            SoftwareQuality quality = parseEnum(SoftwareQuality.class, impact.softwareQuality(), key,
                    "software quality");
            Severity severity = parseEnum(Severity.class, impact.severity(), key, "impact severity");
            if (quality != null && severity != null) {
                parsed.put(quality, severity);
            }
        }
        return parsed;
    }

    private static String parseSeverity(String severity, String key) {
        if (isBlank(severity)) {
            return DEFAULT_SEVERITY;
        }
        String normalized = severity.strip().toUpperCase(Locale.ROOT);
        if (org.sonar.api.rule.Severity.ALL.contains(normalized)) {
            return normalized;
        }
        LOGGER.warn("Ignoring the invalid severity '{}' of the rule {}", severity, key);
        return DEFAULT_SEVERITY;
    }

    private static RuleScope parseScope(String scope, String key) {
        if (isBlank(scope)) {
            return RuleScope.MAIN;
        }
        return switch (scope.strip().toUpperCase(Locale.ROOT)) {
            case "ALL" -> RuleScope.ALL;
            case "MAIN" -> RuleScope.MAIN;
            case "TEST", "TESTS" -> RuleScope.TEST;
            default -> {
                LOGGER.warn("Ignoring the invalid scope '{}' of the rule {}", scope, key);
                yield RuleScope.MAIN;
            }
        };
    }

    private static List<OwaspTop10> parseOwaspCategories(List<String> categories, String key) {
        if (categories == null) {
            return List.of();
        }
        List<OwaspTop10> parsed = new ArrayList<>();
        for (String category : nonBlank(categories)) {
            Matcher matcher = OWASP_CATEGORY.matcher(category.toUpperCase(Locale.ROOT));
            int number = matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
            if (number < 1 || number > OWASP_TOP_10_SIZE) {
                LOGGER.warn("Ignoring the invalid OWASP Top 10 category '{}' of the rule {}", category, key);
                continue;
            }
            parsed.add(OwaspTop10.valueOf("A" + number));
        }
        return parsed;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> enumType, String value, String key, String field) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Ignoring the invalid {} '{}' of the rule {}", field, value, key);
            return null;
        }
    }

    static SoftwareQuality toSoftwareQuality(RuleType type) {
        return switch (type) {
            case BUG -> SoftwareQuality.RELIABILITY;
            case VULNERABILITY, SECURITY_HOTSPOT -> SoftwareQuality.SECURITY;
            case CODE_SMELL -> SoftwareQuality.MAINTAINABILITY;
        };
    }

    private static Severity toImpactSeverity(String severity) {
        return switch (severity) {
            case org.sonar.api.rule.Severity.BLOCKER -> Severity.BLOCKER;
            case org.sonar.api.rule.Severity.CRITICAL -> Severity.HIGH;
            case org.sonar.api.rule.Severity.MINOR -> Severity.LOW;
            case org.sonar.api.rule.Severity.INFO -> Severity.INFO;
            default -> Severity.MEDIUM;
        };
    }

    private static List<String> nonBlank(List<String> values) {
        return values == null ? List.of() : values.stream().filter(v -> !isBlank(v)).map(String::strip).toList();
    }

    private static String strip(String value) {
        return isBlank(value) ? null : value.strip();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
