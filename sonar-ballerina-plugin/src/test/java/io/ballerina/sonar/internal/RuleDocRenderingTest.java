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
import java.util.Map;
import java.util.Properties;

/**
 * Tests for how {@link RuleGenerator} merges and renders the rule docs.
 *
 * @since 0.2.0
 */
public class RuleDocRenderingTest {

    @Test(description = "Remote docs override bundled docs, and bundled docs fill in rules missing remotely")
    void testMergeRuleDocs() {
        Map<String, String> bundled = Map.of("ballerina:1", "bundled 1", "ballerina:2", "bundled 2");
        Map<String, String> remote = Map.of("ballerina:1", "remote 1", "ballerina:3", "remote 3");

        Map<String, String> merged = RuleGenerator.mergeRuleDocs(bundled, remote);

        Assert.assertEquals(merged, Map.of("ballerina:1", "remote 1", "ballerina:2", "bundled 2",
                "ballerina:3", "remote 3"));
        Assert.assertEquals(RuleGenerator.mergeRuleDocs(bundled, Map.of()), bundled);
    }

    @Test(description = "Relative links are resolved against the scan rules page")
    void testRelativeLinksAreResolved() {
        String html = RuleGenerator.convertMdToHtml("[a](#ballerina-1) [b](/learn/x/) [c](//example.com/y) " +
                "[d][ref] [e](https://example.com/z) ![f](/img.png)\n\n[ref]: /learn/ref/\n");

        Assert.assertTrue(html.contains("href=\"https://ballerina.io/learn/scan-rules/#ballerina-1\""), html);
        Assert.assertTrue(html.contains("href=\"https://ballerina.io/learn/x/\""), html);
        Assert.assertTrue(html.contains("href=\"https://example.com/y\""), html);
        Assert.assertTrue(html.contains("href=\"https://ballerina.io/learn/ref/\""), html);
        Assert.assertTrue(html.contains("href=\"https://example.com/z\""), html);
        Assert.assertTrue(html.contains("src=\"https://ballerina.io/img.png\""), html);
    }

    @Test(description = "Link-like text inside code blocks is not changed")
    void testCodeBlocksAreNotChanged() {
        String html = RuleGenerator.convertMdToHtml("```ballerina\nstring s = \"[x](/y)\";\n```\n");

        Assert.assertTrue(html.contains("[x](/y)"), html);
        Assert.assertFalse(html.contains("ballerina.io"), html);
    }

    @Test(description = "Raw HTML is escaped and unsafe URLs are dropped")
    void testUnsafeContentIsNeutralized() {
        String html = RuleGenerator.convertMdToHtml("<script>alert(1)</script>\n\n" +
                "Text <img src=x onerror=alert(1)> [link](javascript:alert(1))\n");

        Assert.assertFalse(html.contains("<script"), html);
        Assert.assertFalse(html.contains("<img"), html);
        Assert.assertFalse(html.contains("javascript:"), html);
    }

    @Test(description = "Rule property tables are rendered as HTML tables")
    void testTablesAreRendered() {
        String html = RuleGenerator.convertMdToHtml("| Property | Description |\n|---|---|\n" +
                "| **Rule ID** | ballerina:1 |\n");

        Assert.assertTrue(html.contains("<table>"), html);
        Assert.assertTrue(html.contains("<td>ballerina:1</td>"), html);
    }

    @Test(description = "The rule docs URLs are bundled from the build script")
    void testRuleDocsSourceIsBundled() throws IOException {
        Properties source = new Properties();
        try (InputStream in = RuleDocRenderingTest.class.getResourceAsStream("/rule-docs/source.properties")) {
            Assert.assertNotNull(in, "The rule docs source is not bundled");
            source.load(in);
        }

        Assert.assertTrue(source.getProperty("url", "").endsWith("/scan-rules.md"));
        Assert.assertEquals(source.getProperty("pageUrl"), "https://ballerina.io/learn/scan-rules/");
    }
}
