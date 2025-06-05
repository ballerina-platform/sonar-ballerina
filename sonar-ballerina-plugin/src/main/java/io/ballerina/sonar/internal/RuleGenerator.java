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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Generates rules for the Sonar Ballerina plugin by fetching metadata from the scan tool.
 *
 * @since 0.2.0
 */
public class RuleGenerator {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final List<RuleMetadata> rules = new ArrayList<>();
    Logger logger = Logger.getLogger(RuleGenerator.class.getName());

    private static final RuleGenerator INSTANCE = new RuleGenerator();

    private static final String SCAN_TOOL_CENTRAL_URI = "https://api.central.ballerina.io/2.0/registry/tools/scan/";
    private static final String ACCEPT_HEADER_NAME = "Accept";
    private static final String ACCEPT_HEADER_VALUE = "application/json";
    private static final String CONTENT_DISPOSITION_HEADER_NAME = "Content-Disposition";
    private static final String CONTENT_DISPOSITION_HEADER_VALUE = "attachment; filename=scan-tool.bala";
    private static final String ACCEPT_ENCODING_HEADER_NAME = "Accept-Encoding";
    private static final String ACCEPT_ENCODING_HEADER_VALUE = "identity";
    private static final String RULE_INFO_FILE_PATH = "resources/rule-info.json";
    private static final Path RULE_CACHE_PATH = Paths.get(System.getProperty("user.home"), ".sonar-ballerina",
            "rule-cache.json");

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
     * Loads the rules for the Sonar Ballerina plugin by fetching metadata from the scan tool in Ballerina Central.
     * If the rules are already loaded, it returns the rules.
     * If there is an error while fetching the rules from the scan tool, it attempts to load them from a cache file.
     *
     * @return List of RuleMetadata objects
     * @throws SonarBallerinaException if an error occurs while fetching or processing the rules
     */
    public synchronized List<RuleMetadata> loadRules() throws SonarBallerinaException {
        if (rules.isEmpty()) {
            try {
                generateRules();
                saveRulesIntoCache();
            } catch (SonarBallerinaException e) {
                boolean rulesLoadedFromCache = loadRulesFromCache();
                if (!rulesLoadedFromCache) {
                    throw e;
                }
            }
        }
        return Collections.unmodifiableList(rules);
    }

    private void generateRules() throws SonarBallerinaException {
        ScanToolMetadata scanToolMetadata = getScanToolMetadata();
        Map<String, RuleMetadata.Builder> ruleBuilders = extractRuleInfo(scanToolMetadata.getBalaURL());
        extractRuleDocs(ruleBuilders, scanToolMetadata.getReadme());
        rules.addAll(ruleBuilders.values().stream().map(RuleMetadata.Builder::build).toList());
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

    private Map<String, RuleMetadata.Builder> extractRuleInfo(String balaUrl) throws SonarBallerinaException {
        Map<String, RuleMetadata.Builder> ruleBuilders = new HashMap<>();
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
                        ruleBuilders.put(ruleId, ruleBuilder);
                    }
                    zipIn.closeEntry();
                    return ruleBuilders;
                }
                throw new SonarBallerinaException("Failed to find rule-info.json in the scan tool archive");
            }
        } catch (IOException | InterruptedException e) {
            throw new SonarBallerinaException("Failed to download the scan tool", e);
        }
    }

    private void extractRuleDocs(Map<String, RuleMetadata.Builder> builders, String readme) {
        Map<String, String> ruleDocsInMd = scrapeRulesFromReadme(readme);
        Map<String, String> ruleDocsInHtml = convertRuleDocsToHtml(ruleDocsInMd);
        for (Map.Entry<String, RuleMetadata.Builder> entry : builders.entrySet()) {
            String id = entry.getKey();
            RuleMetadata.Builder builder = entry.getValue();
            String doc = ruleDocsInHtml.get(id);
            builder.setDescription(Objects.requireNonNullElse(doc, ""));
        }
    }

    private void saveRulesIntoCache() {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(rules);
            Path cachePath = Objects.requireNonNull(RULE_CACHE_PATH.getParent(),
                    "RULE_CACHE_PATH must have a parent directory");
            if (!cachePath.toFile().exists()) {
                if (!cachePath.toFile().mkdirs()) {
                   logger.warning("Failed to create sonar-ballerina cache directory at " + cachePath.toAbsolutePath() +
                            ". The rules will not be cached for future use.");
                   return;
                }
            }
            Files.writeString(RULE_CACHE_PATH, json, StandardCharsets.UTF_8);
        } catch (IOException ignore) {
            logger.warning("Failed to save rules into cache at " + RULE_CACHE_PATH.toAbsolutePath() +
                    ". The rules will not be cached for future use.");
        }
    }

    private boolean loadRulesFromCache() {
        boolean rulesLoadedFromCache = false;
        rules.clear();
        if (!Files.exists(RULE_CACHE_PATH)) {
            logger.severe("Rule cache file does not exist at " + RULE_CACHE_PATH.toAbsolutePath() +
                    ". Unable to load rules from cache.");
            return rulesLoadedFromCache;
        }
        try {
            String json = Files.readString(RULE_CACHE_PATH, StandardCharsets.UTF_8);
            Gson gson = new GsonBuilder().create();
            Type ruleListType = new RuleMetadataListTypeToken().getType();
            List<RuleMetadata> cachedRules = gson.fromJson(json, ruleListType);
            rules.addAll(cachedRules);
            rulesLoadedFromCache = true;
        } catch (IOException e) {
            logger.severe("Failed to read rule cache file at " + RULE_CACHE_PATH.toAbsolutePath() + ". Error: "
                    + e.getMessage());
        }
        return rulesLoadedFromCache;
    }

    private Map<String, String> scrapeRulesFromReadme(String readme) {
        Parser markdownParser = Parser.builder().build();
        Node document = markdownParser.parse(readme);
        RuleVisitor ruleVisitor = new RuleVisitor();
        document.accept(ruleVisitor);
        return ruleVisitor.getRuleDocsInMd();
    }

    private Map<String, String> convertRuleDocsToHtml(Map<String, String> ruleDocsInMd) {
        Map<String, String> ruleDocsInHtml = new HashMap<>();
        for (Map.Entry<String, String> entry : ruleDocsInMd.entrySet()) {
            String id = entry.getKey();
            String docInMd = entry.getValue();
            ruleDocsInHtml.put(id, convertMdToHtml(docInMd));
        }
        return ruleDocsInHtml;
    }

    private static String convertMdToHtml(String markdown) {
        Parser markdownParser = Parser.builder().build();
        Node document = markdownParser.parse(markdown);
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        return renderer.render(document);
    }

    private static class ScanToolRuleInfoListTypeToken extends TypeToken<List<ScanToolRuleInfo>> { }

    private static class RuleMetadataListTypeToken extends TypeToken<List<RuleMetadata>> { }
}
