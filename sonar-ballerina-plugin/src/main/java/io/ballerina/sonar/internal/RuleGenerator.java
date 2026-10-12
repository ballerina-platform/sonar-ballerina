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
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import io.ballerina.sonar.SonarBallerinaException;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
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
import java.time.Duration;
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
    private static final String BALA_URL_FIELD = "balaURL";
    private static final String ACCEPT_HEADER_NAME = "Accept";
    private static final String ACCEPT_HEADER_VALUE = "application/json";
    private static final String CONTENT_DISPOSITION_HEADER_NAME = "Content-Disposition";
    private static final String CONTENT_DISPOSITION_HEADER_VALUE = "attachment; filename=scan-tool.bala";
    private static final String ACCEPT_ENCODING_HEADER_NAME = "Accept-Encoding";
    private static final String ACCEPT_ENCODING_HEADER_VALUE = "identity";
    private static final String RULE_INFO_FILE_PATH = "resources/rule-info.json";
    private static final String RULE_DOCS_URI = "https://raw.githubusercontent.com/ballerina-platform/" +
            "ballerina-dev-website/master/swan-lake/development-tutorials/static-code-analysis/scan-rules.md";
    private static final String BUNDLED_RULE_DOCS_PATH = "/rule-docs/scan-rules.md";
    private static final Duration RULE_DOCS_FETCH_TIMEOUT = Duration.ofSeconds(30);
    private static final String BALLERINA_WEBSITE_URL = "https://ballerina.io";
    private static final String RULE_DOCS_PAGE_URL = BALLERINA_WEBSITE_URL + "/learn/scan-rules/";
    private static final List<Extension> MARKDOWN_EXTENSIONS = List.of(TablesExtension.create());
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
        Map<String, RuleMetadata.Builder> ruleBuilders = extractRuleInfo(getScanToolBalaUrl());
        extractRuleDocs(ruleBuilders);
        rules.addAll(ruleBuilders.values().stream().map(RuleMetadata.Builder::build).toList());
    }

    private String getScanToolBalaUrl() throws SonarBallerinaException {
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
            JsonElement balaUrl = JsonParser.parseString(response.body()).getAsJsonObject().get(BALA_URL_FIELD);
            if (balaUrl == null || !balaUrl.isJsonPrimitive()) {
                throw new SonarBallerinaException("Failed to find the bala URL in the scan tool metadata");
            }
            return balaUrl.getAsString();
        } catch (JsonParseException | IllegalStateException e) {
            throw new SonarBallerinaException("Failed to parse the scan tool metadata", e);
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

    private void extractRuleDocs(Map<String, RuleMetadata.Builder> builders) {
        Map<String, String> ruleDocsInHtml = convertRuleDocsToHtml(loadRuleDocs());
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

    /**
     * Loads the rule docs from the Ballerina website, falling back to the docs bundled with the plugin when they
     * cannot be fetched or contain no rules.
     *
     * @return rule docs in Markdown, keyed by rule ID
     */
    private Map<String, String> loadRuleDocs() {
        try {
            Map<String, String> ruleDocsInMd = RuleDocParser.parse(fetchRuleDocs());
            if (!ruleDocsInMd.isEmpty()) {
                return ruleDocsInMd;
            }
            logger.warning("No rules were found in the rule documentation at " + RULE_DOCS_URI + ".");
        } catch (SonarBallerinaException e) {
            logger.warning(e.getMessage() + ".");
        }

        logger.warning("Using the rule documentation bundled with the plugin.");
        try {
            return RuleDocParser.parse(readBundledRuleDocs());
        } catch (SonarBallerinaException e) {
            logger.warning(e.getMessage() + ". Rules will be loaded without descriptions.");
            return Collections.emptyMap();
        }
    }

    private String readBundledRuleDocs() throws SonarBallerinaException {
        try (InputStream in = RuleGenerator.class.getResourceAsStream(BUNDLED_RULE_DOCS_PATH)) {
            if (in == null) {
                throw new SonarBallerinaException("Failed to find the bundled rule documentation at "
                        + BUNDLED_RULE_DOCS_PATH);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SonarBallerinaException("Failed to read the bundled rule documentation", e);
        }
    }

    private String fetchRuleDocs() throws SonarBallerinaException {
        HttpRequest ruleDocsRequest = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(RULE_DOCS_URI))
                .timeout(RULE_DOCS_FETCH_TIMEOUT)
                .build();
        try {
            HttpResponse<String> response = httpClient.send(ruleDocsRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new SonarBallerinaException("Failed to fetch the rule documentation with status code: "
                        + response.statusCode());
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            throw new SonarBallerinaException("Failed to fetch the rule documentation", e);
        }
    }

    private Map<String, String> convertRuleDocsToHtml(Map<String, String> ruleDocsInMd) {
        Map<String, String> ruleDocsInHtml = new HashMap<>();
        for (Map.Entry<String, String> entry : ruleDocsInMd.entrySet()) {
            String id = entry.getKey();
            String docInMd = resolveRelativeLinks(entry.getValue());
            ruleDocsInHtml.put(id, convertMdToHtml(docInMd));
        }
        return ruleDocsInHtml;
    }

    private static String resolveRelativeLinks(String markdown) {
        return markdown
                .replace("](#", "](" + RULE_DOCS_PAGE_URL + "#")
                .replace("](/", "](" + BALLERINA_WEBSITE_URL + "/");
    }

    private static String convertMdToHtml(String markdown) {
        Parser markdownParser = Parser.builder().extensions(MARKDOWN_EXTENSIONS).build();
        Node document = markdownParser.parse(markdown);
        HtmlRenderer renderer = HtmlRenderer.builder().extensions(MARKDOWN_EXTENSIONS).build();
        return renderer.render(document);
    }

    private static class ScanToolRuleInfoListTypeToken extends TypeToken<List<ScanToolRuleInfo>> { }

    private static class RuleMetadataListTypeToken extends TypeToken<List<RuleMetadata>> { }
}
