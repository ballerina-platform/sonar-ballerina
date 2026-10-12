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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the Markdown documentation of each rule from the scan rules page of the Ballerina website.
 * <p>
 * The page groups rules under level-2 headings (e.g. "Language rules", "Library rules"). Each rule starts with a
 * level-3 heading followed by a property table containing a {@code **Rule ID**} row, which is used to identify the
 * rule. Deeper headings within a rule are promoted by two levels.
 *
 * @since 0.2.0
 */
final class RuleDocParser {
    private static final Pattern RULE_ID_ROW = Pattern.compile("^\\|\\s*\\*\\*Rule ID\\*\\*\\s*\\|\\s*([^|]+?)\\s*\\|");
    private static final Pattern SUB_HEADING = Pattern.compile("^#{4,6} ");
    private static final Pattern CODE_FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");
    private static final String SECTION_HEADING_PREFIX = "## ";
    private static final String RULE_HEADING_PREFIX = "### ";

    private final Map<String, String> ruleDocsInMd = new HashMap<>();
    private StringBuilder currentRule = null;
    private String currentRuleId = null;

    private RuleDocParser() {
    }

    static Map<String, String> parse(String markdown) {
        RuleDocParser parser = new RuleDocParser();
        // The fence that opened the current code block, or null when outside a code block
        String openFence = null;
        for (String line : markdown.split("\\R", -1)) {
            Matcher fenceMatcher = CODE_FENCE.matcher(line);
            boolean isFence = fenceMatcher.matches();
            if (openFence == null && !isFence) {
                if (line.startsWith(SECTION_HEADING_PREFIX)) {
                    parser.endRule();
                    continue;
                }
                if (line.startsWith(RULE_HEADING_PREFIX)) {
                    parser.endRule();
                    parser.currentRule = new StringBuilder();
                    continue;
                }
                if (SUB_HEADING.matcher(line).find()) {
                    line = line.substring(2);
                }
            }
            if (isFence) {
                String fence = fenceMatcher.group(1);
                String rest = fenceMatcher.group(2);
                if (openFence == null) {
                    // The info string of a backtick fence cannot contain backticks
                    if (fence.charAt(0) == '~' || rest.indexOf('`') < 0) {
                        openFence = fence;
                    }
                } else if (closesFence(openFence, fence, rest)) {
                    openFence = null;
                }
            }
            parser.appendLine(line);
        }
        parser.endRule();
        return parser.ruleDocsInMd;
    }

    /**
     * A closing fence uses the same character as the opening fence, is at least as long, and has nothing after it.
     */
    private static boolean closesFence(String openFence, String fence, String rest) {
        return fence.charAt(0) == openFence.charAt(0) && fence.length() >= openFence.length() && rest.isBlank();
    }

    private void appendLine(String line) {
        if (currentRule == null) {
            return;
        }
        if (currentRuleId == null) {
            Matcher matcher = RULE_ID_ROW.matcher(line);
            if (matcher.find()) {
                currentRuleId = matcher.group(1);
            }
        }
        currentRule.append(line).append("\n");
    }

    private void endRule() {
        if (currentRule != null && currentRuleId != null) {
            ruleDocsInMd.put(currentRuleId, currentRule.toString().strip());
        }
        currentRule = null;
        currentRuleId = null;
    }
}
