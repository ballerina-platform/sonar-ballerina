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

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * Tests for {@link RuleDocParser}.
 *
 * @since 0.2.0
 */
public class RuleDocParserTest {

    private static String rule(String title, String ruleId, String body) {
        return "### " + title + "\n\n" +
                "| Property    | Description |\n" +
                "|-------------|-------------|\n" +
                "| **Rule ID** | " + ruleId + " |\n\n" +
                body + "\n";
    }

    @Test(description = "Rules are keyed by the Rule ID row, without their heading")
    void testParseRules() {
        String markdown = "---\ntitle: Scan rules\n---\n\nIntro\n\n## Language rules\n\n" +
                rule("Avoid checkpanic", "ballerina:1", "Body one.") +
                rule("Unused parameter", "ballerina:2", "Body two.") +
                "## Library rules\n\n" +
                rule("Weak hash", "ballerina/crypto:1", "Body three.");

        Map<String, String> docs = RuleDocParser.parse(markdown);

        Assert.assertEquals(docs.keySet(), Set.of("ballerina:1", "ballerina:2", "ballerina/crypto:1"));
        String doc = docs.get("ballerina:1");
        Assert.assertFalse(doc.contains("Avoid checkpanic"));
        Assert.assertTrue(doc.startsWith("| Property"));
        Assert.assertTrue(doc.endsWith("Body one."));
        Assert.assertFalse(doc.contains("Body two."));
        Assert.assertFalse(docs.get("ballerina:2").contains("Library rules"));
    }

    @Test(description = "Sub-headings of a rule are promoted by two levels")
    void testSubHeadingsArePromoted() {
        Map<String, String> docs = RuleDocParser.parse(rule("Rule", "ballerina:1",
                "#### Noncompliant code example\n\ntext\n\n##### Details"));

        String doc = docs.get("ballerina:1");
        Assert.assertTrue(doc.contains("\n## Noncompliant code example\n"));
        Assert.assertTrue(doc.contains("\n### Details"));
    }

    @Test(description = "Rules without a Rule ID row are skipped")
    void testRuleWithoutIdIsSkipped() {
        Map<String, String> docs = RuleDocParser.parse("### No ID\n\nSome text\n\n" +
                rule("Rule", "ballerina:1", "Body."));

        Assert.assertEquals(docs.keySet(), Set.of("ballerina:1"));
    }

    @Test(description = "Indented and level-1 headings are rule boundaries, and indented sub-headings are promoted")
    void testHeadingVariants() {
        String markdown = rule("First", "ballerina:1", "  #### Indented sub-heading\n\nBody one.") +
                "  ### Indented rule\n\n" +
                "| Property    | Description |\n" +
                "|-------------|-------------|\n" +
                "| **Rule ID** | ballerina:2 |\n\n" +
                "Body two.\n\n" +
                "# Appendix\n\nNot part of a rule.\n\n" +
                "###\tTab-separated rule\n\n| **Rule ID** | ballerina:3 |\n\n" +
                "####### Not a heading\n";

        Map<String, String> docs = RuleDocParser.parse(markdown);

        Assert.assertEquals(docs.keySet(), Set.of("ballerina:1", "ballerina:2", "ballerina:3"));
        Assert.assertTrue(docs.get("ballerina:1").contains("\n  ## Indented sub-heading\n"));
        Assert.assertTrue(docs.get("ballerina:1").endsWith("Body one."));
        Assert.assertTrue(docs.get("ballerina:2").endsWith("Body two."));
        Assert.assertTrue(docs.get("ballerina:3").endsWith("####### Not a heading"));
    }

    @Test(description = "Headings inside backtick and tilde code blocks are kept as they are")
    void testHeadingsInCodeBlocks() {
        String body = "```md\n## Not a section\n### Not a rule\n#### Not promoted\n```\n\n" +
                "~~~\n### Not a rule either\n~~~\n\n" +
                "````\n```\n### Still in the code block\n```\n````\n\n" +
                "#### After the code blocks";
        Map<String, String> docs = RuleDocParser.parse(rule("Rule", "ballerina:1", body) +
                rule("Next", "ballerina:2", "Body."));

        Assert.assertEquals(docs.keySet(), Set.of("ballerina:1", "ballerina:2"));
        String doc = docs.get("ballerina:1");
        Assert.assertTrue(doc.contains("\n## Not a section\n### Not a rule\n#### Not promoted\n"));
        Assert.assertTrue(doc.contains("\n### Not a rule either\n"));
        Assert.assertTrue(doc.contains("\n### Still in the code block\n"));
        Assert.assertTrue(doc.endsWith("\n## After the code blocks"));
    }

    @Test(description = "A shorter or different fence does not close a code block")
    void testFenceMustMatchToClose() {
        String body = "~~~\n```\n### Inside\n~~~\n\n#### After";
        String doc = RuleDocParser.parse(rule("Rule", "ballerina:1", body)).get("ballerina:1");

        Assert.assertTrue(doc.contains("\n### Inside\n"));
        Assert.assertTrue(doc.endsWith("\n## After"));
    }

    @Test(description = "The bundled rule docs document every scan tool rule")
    void testBundledRuleDocs() throws IOException {
        String markdown;
        try (InputStream in = RuleDocParserTest.class.getResourceAsStream("/rule-docs/scan-rules.md")) {
            Assert.assertNotNull(in, "The rule docs are not bundled");
            markdown = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        Map<String, String> docs = RuleDocParser.parse(markdown);

        for (int i = 1; i <= 14; i++) {
            Assert.assertTrue(docs.containsKey("ballerina:" + i), "Missing docs for ballerina:" + i);
        }
    }
}
