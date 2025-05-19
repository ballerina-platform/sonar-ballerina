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

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;

import java.util.HashMap;
import java.util.Map;

/**
 * Visitor to extract rules documentation from a CommonMark document.
 * It collects rules defined in the "Rules" section and formats them into Markdown.
 *
 * @since 0.2.0
 */
class RuleVisitor extends AbstractVisitor {
    private final Map<String, String> ruleDocsInMd = new HashMap<>();
    private boolean inRulesSection = false;
    private final StringBuilder currentRule = new StringBuilder();
    private String currentRuleId = null;

    private static final String RULES_SECTION_TITLE = "Rules";

    public Map<String, String> getRuleDocsInMd() {
        updateRule(); // add the last rule
        return ruleDocsInMd;
    }

    @Override
    public void visit(Heading heading) {
        String headingText = getText(heading);
        if (foundRulesSectionStart(heading)) {
            inRulesSection = true;
            return;
        }
        if (foundRuleSectionEnd(heading)) {
            inRulesSection = false;
            return;
        }
        if (foundNewRule(heading)) {
            updateRule();
            String[] headingParts = headingText.split("-", 2);
            if (headingParts.length < 1) {
                return;
            }
            currentRuleId = headingParts[0].trim();
        }
        if (foundSubHeadingWithinARule(heading)) {
            int level = heading.getLevel() - 2;
            currentRule.append("#".repeat(level)).append(" ").append(headingText).append("\n\n");
        }
    }

    @Override
    public void visit(Paragraph paragraph) {
        if (inRulesSection) {
            currentRule.append(getText(paragraph)).append("\n\n");
        }
    }

    @Override
    public void visit(FencedCodeBlock codeBlock) {
        if (inRulesSection) {
            currentRule.append("```")
                    .append(codeBlock.getInfo()).append("\n")
                    .append(codeBlock.getLiteral())
                    .append("```\n\n");
        }
    }

    @Override
    public void visit(BlockQuote blockQuote) {
        if (inRulesSection) {
            currentRule.append("> ").append(getText(blockQuote)).append("\n\n");
        }
    }

    @Override
    public void visit(BulletList bulletList) {
        if (inRulesSection) {
            currentRule.append("- ").append(getText(bulletList)).append("\n\n");
        }
    }

    @Override
    public void visit(Code code) {
        if (inRulesSection) {
            currentRule.append("`").append(getText(code)).append("`\n\n");
        }
    }

    private String getText(Node node) {
        final StringBuilder sb = new StringBuilder();
        node.accept(new TextExtractorVisitor(sb));
        return sb.toString().trim();
    }

    private void updateRule() {
        if (!currentRule.isEmpty() && currentRuleId != null) {
            ruleDocsInMd.put(currentRuleId, currentRule.toString());
            currentRule.setLength(0);
        }
    }

    private boolean foundRulesSectionStart(Heading heading) {
        String headingText = getText(heading);
        return !inRulesSection && heading.getLevel() == 2 && headingText.equals(RULES_SECTION_TITLE);
    }

    private boolean foundRuleSectionEnd(Heading heading) {
        return inRulesSection && heading.getLevel() == 2;
    }

    private boolean foundNewRule(Heading heading) {
        return inRulesSection && heading.getLevel() == 3;
    }

    private boolean foundSubHeadingWithinARule(Heading heading) {
        return inRulesSection && heading.getLevel() > 3;
    }

    private static class TextExtractorVisitor extends AbstractVisitor {
        private final StringBuilder sb;

        TextExtractorVisitor(StringBuilder sb) {
            this.sb = sb;
        }

        @Override
        public void visit(Text text) {
            sb.append(text.getLiteral());
        }

        @Override
        public void visit(SoftLineBreak softLineBreak) {
            sb.append(" ");
        }

        @Override
        public void visit(HardLineBreak hardLineBreak) {
            sb.append(" ");
        }
    }
}
