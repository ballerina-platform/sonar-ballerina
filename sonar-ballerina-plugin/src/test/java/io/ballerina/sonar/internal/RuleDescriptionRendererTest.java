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

import io.ballerina.sonar.internal.RuleMetadata.RuleDescription;
import io.ballerina.sonar.internal.ScanToolRuleInfo.CodeBlock;
import io.ballerina.sonar.internal.ScanToolRuleInfo.Description;
import io.ballerina.sonar.internal.ScanToolRuleInfo.Example;
import io.ballerina.sonar.internal.ScanToolRuleInfo.HowToFix;
import io.ballerina.sonar.internal.ScanToolRuleInfo.Link;
import org.sonar.api.server.rule.RulesDefinition.OwaspTop10;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class RuleDescriptionRendererTest {

    @Test(description = "Each noncompliant example is paired with the compliant solution at the same position")
    void testExamplesArePairedForDiffs() {
        Example first = new Example(null,
                List.of(code("Bad.", "bad();\n", null)),
                List.of(code("Good.", "good();\n", null), code("Also good.", "alsoGood();\n", "Why.")));
        Example second = new Example("RSA", List.of(code(null, "rsaBad();\n", "Explained.")),
                List.of(code(null, "rsaGood();\n", null)));
        String html = render(new Description(null, new HowToFix(List.of(first, second)), null)).howToFix();

        Assert.assertEquals(html, """
                <h4>Noncompliant code example</h4>
                <p>Bad.</p>
                <pre data-diff-id="1" data-diff-type="noncompliant">bad();</pre>
                <h4>Compliant solutions</h4>
                <p>Good.</p>
                <pre data-diff-id="1" data-diff-type="compliant">good();</pre>
                <p>Also good.</p>
                <pre>alsoGood();</pre>
                <p>Why.</p>
                <h3>RSA</h3>
                <h4>Noncompliant code example</h4>
                <pre data-diff-id="2" data-diff-type="noncompliant">rsaBad();</pre>
                <p>Explained.</p>
                <h4>Compliant solution</h4>
                <pre data-diff-id="2" data-diff-type="compliant">rsaGood();</pre>""");
    }

    @Test(description = "A noncompliant example without a compliant solution is shown without a diff")
    void testUnpairedExample() {
        Example example = new Example(null, List.of(code(null, "x == x\n", null)), null);
        String html = render(new Description("Why.", new HowToFix(List.of(example)), null)).howToFix();
        Assert.assertEquals(html, "<h4>Noncompliant code example</h4>\n<pre>x == x</pre>");
    }

    @Test(description = "Code is escaped rather than interpreted as HTML")
    void testCodeIsEscaped() {
        Example example = new Example("<b>T</b>", List.of(code(null, "string s = \"<script>&\";\n", null)), null);
        String html = render(new Description(null, new HowToFix(List.of(example)), null)).howToFix();
        Assert.assertTrue(html.contains("<h3>&lt;b&gt;T&lt;/b&gt;</h3>"), html);
        Assert.assertTrue(html.contains("string s = &quot;&lt;script&gt;&amp;&quot;;"), html);
    }

    @Test(description = "The links to the rule docs, CWE entries and OWASP Top 10 categories are derived from the "
            + "rule, followed by the other links in rule-info.json")
    void testDerivedLinks() {
        String html = RuleDescriptionRenderer.render(new Description("Why.", null, List.of(
                        new Link("Duplicate", "https://cwe.mitre.org/data/definitions/22.html"),
                        new Link("Module docs", "https://central.ballerina.io/ballerina/http/latest"))),
                "Avoid X", "https://ballerina.io/learn/scan-rules/#ballerina-1", List.of(22, 73),
                List.of(OwaspTop10.A1, OwaspTop10.A8)).resources();
        Assert.assertEquals(html, """
                <h3>Documentation</h3>
                <ul>
                  <li><a href="https://ballerina.io/learn/scan-rules/#ballerina-1">\
                Ballerina scan rules - Avoid X</a></li>
                  <li><a href="https://central.ballerina.io/ballerina/http/latest">Module docs</a></li>
                </ul>
                <h3>Standards</h3>
                <ul>
                  <li><a href="https://cwe.mitre.org/data/definitions/22.html">CWE-22</a></li>
                  <li><a href="https://cwe.mitre.org/data/definitions/73.html">CWE-73</a></li>
                  <li><a href="https://owasp.org/Top10/2025/A01_2025-Broken_Access_Control/">\
                OWASP Top 10:2025 - A01 Broken Access Control</a></li>
                  <li><a href="https://owasp.org/Top10/2025/A08_2025-Software_or_Data_Integrity_Failures/">\
                OWASP Top 10:2025 - A08 Software or Data Integrity Failures</a></li>
                </ul>""");
    }

    @Test(description = "Every OWASP Top 10 category has a page")
    void testAllOwaspCategories() {
        String html = RuleDescriptionRenderer.render(null, "T", null, List.of(), List.of(OwaspTop10.values()))
                .resources();
        for (OwaspTop10 category : OwaspTop10.values()) {
            String number = String.format("A%02d_2025-", Integer.parseInt(category.name().substring(1)));
            Assert.assertTrue(html.contains("https://owasp.org/Top10/2025/" + number), category + ": " + html);
        }
    }

    @Test(description = "Links are grouped into documentation and standards, and unsafe URLs are dropped")
    void testLinks() {
        String html = render(new Description("Why.", null, List.of(
                new Link("Rule docs", "https://ballerina.io/learn/scan-rules/#ballerina-1"),
                new Link("CWE-22", "https://cwe.mitre.org/data/definitions/22.html"),
                new Link("OWASP", "https://owasp.org/Top10/2025/A01_2025-Broken_Access_Control/"),
                new Link("Relative", "/learn/x/"),
                new Link("Script", "javascript:alert(1)"),
                new Link("<i>No URL</i>", null)))).resources();
        Assert.assertEquals(html, """
                <h3>Documentation</h3>
                <ul>
                  <li><a href="https://ballerina.io/learn/scan-rules/#ballerina-1">Rule docs</a></li>
                  <li><a href="https://ballerina.io/learn/x/">Relative</a></li>
                </ul>
                <h3>Standards</h3>
                <ul>
                  <li><a href="https://cwe.mitre.org/data/definitions/22.html">CWE-22</a></li>
                  <li><a href="https://owasp.org/Top10/2025/A01_2025-Broken_Access_Control/">OWASP</a></li>
                </ul>""");
    }

    @Test(description = "Markdown is rendered, with raw HTML escaped and relative links resolved")
    void testMarkdown() {
        String html = RuleDescriptionRenderer.renderMarkdown("Use `check` [here](#ballerina-1) and "
                + "[there](/learn/x/).\n\n<script>alert(1)</script>\n\nA [bad](javascript:alert(1)) link.");
        Assert.assertTrue(html.contains("<code>check</code>"), html);
        Assert.assertTrue(html.contains("href=\"https://ballerina.io/learn/scan-rules/#ballerina-1\""), html);
        Assert.assertTrue(html.contains("href=\"https://ballerina.io/learn/x/\""), html);
        Assert.assertFalse(html.contains("<script"), html);
        Assert.assertFalse(html.contains("javascript:"), html);
    }

    @Test(description = "An undocumented rule links to its documentation, since SonarQube needs a description")
    void testUndocumentedRule() {
        RuleDescription description = RuleDescriptionRenderer.render(null, "T",
                "https://ballerina.io/learn/scan-rules/#ballerina-1", null, null);
        Assert.assertTrue(description.rootCause().contains(
                "href=\"https://ballerina.io/learn/scan-rules/#ballerina-1\""), description.rootCause());
        Assert.assertNull(description.howToFix());
        Assert.assertTrue(description.resources().contains("Ballerina scan rules - T"), description.resources());

        description = RuleDescriptionRenderer.render(new Description(" ", null, List.of()), "T", "javascript:x",
                null, null);
        Assert.assertTrue(description.rootCause().contains("href=\"https://ballerina.io/learn/scan-rules/\""));
        Assert.assertNull(description.resources());
    }

    private static RuleDescription render(Description description) {
        return RuleDescriptionRenderer.render(description, "T", null, List.of(), List.of());
    }

    private static CodeBlock code(String description, String code, String explanation) {
        return new CodeBlock(description, "ballerina", code, explanation);
    }
}
