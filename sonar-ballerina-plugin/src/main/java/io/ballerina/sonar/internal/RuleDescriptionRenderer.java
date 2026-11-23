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
import io.ballerina.sonar.internal.ScanToolRuleInfo.Link;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.sonar.api.server.rule.RulesDefinition.OwaspTop10;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Renders the documentation of a rule in rule-info.json as the HTML of the SonarQube rule description sections.
 */
final class RuleDescriptionRenderer {

    static final URI RULE_DOCS_PAGE_URI = URI.create("https://ballerina.io/learn/scan-rules/");

    private static final List<Extension> MARKDOWN_EXTENSIONS = List.of(TablesExtension.create());
    private static final Parser MARKDOWN_PARSER = Parser.builder().extensions(MARKDOWN_EXTENSIONS).build();
    private static final HtmlRenderer HTML_RENDERER = HtmlRenderer.builder()
            .extensions(MARKDOWN_EXTENSIONS)
            .escapeHtml(true)
            .sanitizeUrls(true)
            .attributeProviderFactory(context -> RuleDescriptionRenderer::resolveRelativeUrls)
            .build();
    private static final List<String> STANDARDS_HOSTS = List.of("cwe.mitre.org", "owasp.org");
    private static final String CWE_URL_FORMAT = "https://cwe.mitre.org/data/definitions/%d.html";
    private static final String OWASP_TOP_10_URL_FORMAT = "https://owasp.org/Top10/2025/%s/";
    private static final Map<OwaspTop10, String> OWASP_TOP_10_2025_PAGES = Map.of(
            OwaspTop10.A1, "A01_2025-Broken_Access_Control",
            OwaspTop10.A2, "A02_2025-Security_Misconfiguration",
            OwaspTop10.A3, "A03_2025-Software_Supply_Chain_Failures",
            OwaspTop10.A4, "A04_2025-Cryptographic_Failures",
            OwaspTop10.A5, "A05_2025-Injection",
            OwaspTop10.A6, "A06_2025-Insecure_Design",
            OwaspTop10.A7, "A07_2025-Authentication_Failures",
            OwaspTop10.A8, "A08_2025-Software_or_Data_Integrity_Failures",
            OwaspTop10.A9, "A09_2025-Security_Logging_and_Alerting_Failures",
            OwaspTop10.A10, "A10_2025-Mishandling_of_Exceptional_Conditions");

    private RuleDescriptionRenderer() {
    }

    static RuleDescription render(Description description, String title, String helpUri, List<Integer> cwe,
                                  List<OwaspTop10> owaspTop10) {
        String rootCause = null;
        String howToFix = null;
        List<Link> extraLinks = List.of();
        if (description != null) {
            rootCause = renderMarkdown(description.whyIsThisAnIssue());
            if (description.howToFix() != null) {
                howToFix = renderExamples(orEmpty(description.howToFix().examples()));
            }
            extraLinks = orEmpty(description.moreInfo());
        }
        String resources = renderLinks(moreInfoLinks(title, helpUri, cwe, owaspTop10, extraLinks));
        if (rootCause == null && howToFix == null) {
            // SonarQube rejects a rule without a description, so an undocumented rule points to the rule docs
            URI docs = Objects.requireNonNullElse(toWebUri(helpUri), RULE_DOCS_PAGE_URI);
            rootCause = renderMarkdown("Visit <" + docs + "> for details.");
        }
        return new RuleDescription(rootCause, howToFix, resources);
    }

    static String renderMarkdown(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return null;
        }
        return HTML_RENDERER.render(MARKDOWN_PARSER.parse(markdown)).strip();
    }

    /**
     * Renders the examples the way the SonarQube rules for other languages do, so that each noncompliant code
     * example is shown as a diff against the compliant solution at the same position.
     */
    private static String renderExamples(List<Example> examples) {
        StringBuilder html = new StringBuilder();
        int diffId = 0;
        for (Example example : examples) {
            if (example == null) {
                continue;
            }
            if (example.title() != null && !example.title().isBlank()) {
                html.append("<h3>").append(escape(example.title().strip())).append("</h3>\n");
            }
            List<CodeBlock> noncompliant = withCode(example.noncompliant());
            List<CodeBlock> compliant = withCode(example.compliant());
            int firstDiffId = diffId + 1;
            int pairs = Math.min(noncompliant.size(), compliant.size());
            diffId += pairs;
            appendCodeBlocks(html, "Noncompliant code example", noncompliant, "noncompliant", firstDiffId, pairs);
            appendCodeBlocks(html, compliant.size() > 1 ? "Compliant solutions" : "Compliant solution", compliant,
                    "compliant", firstDiffId, pairs);
        }
        return html.isEmpty() ? null : html.toString().strip();
    }

    private static void appendCodeBlocks(StringBuilder html, String heading, List<CodeBlock> blocks, String diffType,
                                         int firstDiffId, int pairs) {
        if (blocks.isEmpty()) {
            return;
        }
        html.append("<h4>").append(heading).append("</h4>\n");
        for (int i = 0; i < blocks.size(); i++) {
            CodeBlock block = blocks.get(i);
            appendMarkdown(html, block.description());
            html.append("<pre");
            if (i < pairs) {
                html.append(" data-diff-id=\"").append(firstDiffId + i)
                        .append("\" data-diff-type=\"").append(diffType).append('"');
            }
            html.append('>').append(escape(block.code().stripTrailing())).append("</pre>\n");
            appendMarkdown(html, block.explanation());
        }
    }

    /**
     * Derives the "More info" links from the rule docs URL and the security standards of the rule, followed by the
     * links in rule-info.json that are not among them.
     */
    private static List<Link> moreInfoLinks(String title, String helpUri, List<Integer> cwe,
                                            List<OwaspTop10> owaspTop10, List<Link> extraLinks) {
        List<Link> links = new ArrayList<>();
        if (helpUri != null && !helpUri.isBlank()) {
            links.add(new Link("Ballerina scan rules - " + title, helpUri));
        }
        orEmpty(cwe).forEach(id -> links.add(new Link("CWE-" + id, String.format(Locale.ROOT, CWE_URL_FORMAT, id))));
        for (OwaspTop10 category : orEmpty(owaspTop10)) {
            String page = OWASP_TOP_10_2025_PAGES.get(category);
            String name = page.substring(page.indexOf('-') + 1).replace('_', ' ');
            String number = String.format(Locale.ROOT, "A%02d", Integer.parseInt(category.name().substring(1)));
            links.add(new Link("OWASP Top 10:2025 - " + number + " " + name,
                    String.format(Locale.ROOT, OWASP_TOP_10_URL_FORMAT, page)));
        }
        links.addAll(extraLinks);
        return links;
    }

    private static String renderLinks(List<Link> links) {
        List<String> documentation = new ArrayList<>();
        List<String> standards = new ArrayList<>();
        Set<URI> seen = new HashSet<>();
        for (Link link : links) {
            URI url = toWebUri(link == null ? null : link.url());
            if (url == null || !seen.add(url)) {
                continue;
            }
            String title = link.title() == null || link.title().isBlank() ? url.toString() : link.title().strip();
            String item = "<li><a href=\"" + escape(url.toString()) + "\">" + escape(title) + "</a></li>";
            String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
            boolean isStandard = STANDARDS_HOSTS.stream().anyMatch(h -> host.equals(h) || host.endsWith("." + h));
            (isStandard ? standards : documentation).add(item);
        }
        StringBuilder html = new StringBuilder();
        appendLinkList(html, "Documentation", documentation);
        appendLinkList(html, "Standards", standards);
        return html.isEmpty() ? null : html.toString().strip();
    }

    private static void appendLinkList(StringBuilder html, String heading, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        html.append("<h3>").append(heading).append("</h3>\n<ul>\n");
        items.forEach(item -> html.append("  ").append(item).append('\n'));
        html.append("</ul>\n");
    }

    private static URI toWebUri(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = RULE_DOCS_PAGE_URI.resolve(url.strip());
            String scheme = uri.getScheme();
            return "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme) ? uri : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void appendMarkdown(StringBuilder html, String markdown) {
        String rendered = renderMarkdown(markdown);
        if (rendered != null) {
            html.append(rendered).append('\n');
        }
    }

    private static List<CodeBlock> withCode(List<CodeBlock> blocks) {
        return orEmpty(blocks).stream().filter(b -> b != null && b.code() != null && !b.code().isBlank()).toList();
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    static String escape(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    /**
     * Resolves relative link and image URLs against the scan rules page, since the descriptions are shown in
     * SonarQube rather than on the Ballerina website.
     */
    private static void resolveRelativeUrls(Node node, String tagName, Map<String, String> attributes) {
        resolveRelativeUrl(attributes, "href");
        resolveRelativeUrl(attributes, "src");
    }

    private static void resolveRelativeUrl(Map<String, String> attributes, String attribute) {
        String url = attributes.get(attribute);
        if (url == null) {
            return;
        }
        try {
            attributes.put(attribute, RULE_DOCS_PAGE_URI.resolve(url).toString());
        } catch (IllegalArgumentException ignore) {
        }
    }
}
