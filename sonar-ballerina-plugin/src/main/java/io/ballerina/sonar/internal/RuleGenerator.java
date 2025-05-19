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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.ballerina.sonar.SonarBallerinaException;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Generates rules for the Sonar Ballerina plugin by fetching metadata from the scan tool.
 *
 * @since 0.2.0
 */
public class RuleGenerator {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Map<String, RuleMetadata.Builder> rules = new HashMap<>();

    private static final RuleGenerator INSTANCE = new RuleGenerator();

    private static final String SCAN_TOOL_CENTRAL_URI = "https://api.central.ballerina.io/2.0/registry/tools/scan/";
    private static final String ACCEPT_HEADER_NAME = "Accept";
    private static final String ACCEPT_HEADER_VALUE = "application/json";
    private static final String CONTENT_DISPOSITION_HEADER_NAME = "Content-Disposition";
    private static final String CONTENT_DISPOSITION_HEADER_VALUE = "attachment; filename=scan-tool.bala";
    private static final String ACCEPT_ENCODING_HEADER_NAME = "Accept-Encoding";
    private static final String ACCEPT_ENCODING_HEADER_VALUE = "identity";
    private static final String RULE_INFO_FILE_PATH = "resources/rule-info.json";

    private RuleGenerator() {
    }

    /**
     * Returns the singleton instance of RuleGenerator.
     *
     * @return RuleGenerator instance
     */
    public static RuleGenerator getInstance() {
        return INSTANCE;
    }

    /**
     * Generates the rules for the Sonar Ballerina plugin by fetching metadata from the scan tool.
     *
     * @return List of RuleMetadata objects
     * @throws SonarBallerinaException if an error occurs while fetching or processing the rules
     */
    public synchronized List<RuleMetadata> generateRules() throws SonarBallerinaException {
        if (!rules.isEmpty()) {
            return rules.values().stream().map(RuleMetadata.Builder::build).toList();
        }
        ScanToolMetadata scanToolMetadata = getScanToolMetadata();
        extractRuleInfo(scanToolMetadata.getBalaURL());
        extractRuleDocs(scanToolMetadata.getReadme());
        return rules.values().stream().map(RuleMetadata.Builder::build).toList();
    }

    private ScanToolMetadata getScanToolMetadata() throws SonarBallerinaException {
        HttpRequest pullToolReq = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(SCAN_TOOL_CENTRAL_URI))
                .header(ACCEPT_HEADER_NAME, ACCEPT_HEADER_VALUE)
                .build();
        try {
            HttpResponse<String> response = httpClient.send(pullToolReq, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                String errorMsg = "Failed to fetch the scan tool metadata with status code: " + response.statusCode();
                throw new SonarBallerinaException(errorMsg);
            }
            Gson gson = new GsonBuilder().create();
            return gson.fromJson(response.body(), ScanToolMetadata.class);
        } catch (IOException | InterruptedException e) {
            String errorMsg = "Failed to fetch the scan tool metadata";
            throw new SonarBallerinaException(errorMsg, e);
        }
    }

    private void extractRuleInfo(String balaUrl) throws SonarBallerinaException {
        HttpRequest pullBalaRequest = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(balaUrl))
                .header(ACCEPT_ENCODING_HEADER_NAME, ACCEPT_ENCODING_HEADER_VALUE)
                .setHeader(CONTENT_DISPOSITION_HEADER_NAME, CONTENT_DISPOSITION_HEADER_VALUE)
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(
                    pullBalaRequest,
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new SonarBallerinaException("Failed to download the scan tool with status code: "
                        + response.statusCode());
            }
            try (ZipInputStream zipIn = new ZipInputStream(response.body())) {
                ZipEntry entry;
                while ((entry = zipIn.getNextEntry()) != null) {
                    if (entry.isDirectory() || !entry.getName().equals(RULE_INFO_FILE_PATH)) {
                        continue;
                    }
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zipIn.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                    String jsonString = out.toString(StandardCharsets.UTF_8);
                    Gson gson = new Gson();
                    Type ruleInfoListType = new ScanToolRuleInfoListTypeToken().getType();
                    List<ScanToolRuleInfo> ruleInfo = gson.fromJson(jsonString, ruleInfoListType);
                    for (ScanToolRuleInfo rule : ruleInfo) {
                        String ruleId = rule.getSqKey();
                        RuleMetadata.Builder ruleBuilder = RuleMetadata.builder()
                                .setId(ruleId)
                                .setName(rule.getTitle())
                                .setType(rule.getType().toUpperCase(Locale.ROOT))
                                .setSeverity(rule.getDefaultSeverity().toUpperCase(Locale.ROOT))
                                .setTags(rule.getTags());
                        rules.put(ruleId, ruleBuilder);
                    }
                    zipIn.closeEntry();
                    return;
                }
                throw new SonarBallerinaException("Failed to find rule-info.json in the scan tool archive");
            }
        } catch (IOException | InterruptedException e) {
            throw new SonarBallerinaException("Failed to download the scan tool", e);
        }
    }

    private void extractRuleDocs(String readme) {
        Map<String, String> ruleDocsInMd = scrapeRulesFromReadme(readme);
        convertRuleDocsToHtml(ruleDocsInMd);
    }

    private Map<String, String> scrapeRulesFromReadme(String readme) {
        Parser markdownParser = Parser.builder().build();
        Node document = markdownParser.parse(readme);
        RuleVisitor ruleVisitor = new RuleVisitor();
        document.accept(ruleVisitor);
        return ruleVisitor.getRuleDocsInMd();
    }

    private void convertRuleDocsToHtml(Map<String, String> ruleDocsInMd) {
        rules.forEach((ruleId, rule) -> {
            String docInMd = ruleDocsInMd.get(ruleId);
            if (docInMd == null) {
                rule.setDescription("");
                return;
            }
            String docInHtml = convertMdToHtml(docInMd);
            rule.setDescription(docInHtml);
        });
    }

    private static String convertMdToHtml(String markdown) {
        Parser markdownParser = Parser.builder().build();
        Node document = markdownParser.parse(markdown);
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        return renderer.render(document);
    }

    private static class ScanToolRuleInfoListTypeToken extends TypeToken<List<ScanToolRuleInfo>> { }
}
