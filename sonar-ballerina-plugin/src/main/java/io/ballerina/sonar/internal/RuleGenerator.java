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

import io.ballerina.sonar.SonarBallerinaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.config.Configuration;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Loads the rules of the Ballerina rule repository from the rule-info.json file of the scan tool.
 * <p>
 * The file is fetched from the URL bundled at build time, which the {@value #RULE_INFO_URL_PROPERTY} server property
 * overrides, and failing that, from the fallback URL bundled at build time. When both fetches fail, the rules are
 * loaded from the copy cached by the last successful fetch, and failing that, from the snapshot bundled with the
 * plugin.
 */
public final class RuleGenerator {

    public static final String RULE_INFO_URL_PROPERTY = "sonar.ballerina.ruleInfoUrl";

    private static final Logger LOGGER = LoggerFactory.getLogger(RuleGenerator.class);

    private static final String RULE_INFO_SOURCE_PATH = "/rule-info/source.properties";
    private static final String BUNDLED_RULE_INFO_PATH = "/rule-info/rule-info.json";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(30);
    private static final Path DEFAULT_RULE_CACHE_PATH = Paths.get(System.getProperty("user.home"),
            ".sonar-ballerina", "rule-info.json");
    private static final RuleGenerator INSTANCE = new RuleGenerator();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private List<RuleMetadata> rules;

    private RuleGenerator() {
    }

    public static RuleGenerator getInstance() {
        return INSTANCE;
    }

    /**
     * Loads the rules once, and returns the same rules on later calls.
     *
     * @param configuration the server configuration, which may override the URL of rule-info.json
     * @return the rules of the Ballerina rule repository
     * @throws SonarBallerinaException if the rules cannot be loaded from any source
     */
    public synchronized List<RuleMetadata> loadRules(Configuration configuration) throws SonarBallerinaException {
        if (rules == null) {
            List<URI> ruleInfoUris = getRuleInfoUris(configuration.get(RULE_INFO_URL_PROPERTY));
            rules = List.copyOf(loadRules(ruleInfoUris, DEFAULT_RULE_CACHE_PATH));
        }
        return List.copyOf(rules);
    }

    List<RuleMetadata> loadRules(List<URI> ruleInfoUris, Path ruleCachePath) throws SonarBallerinaException {
        for (URI ruleInfoUri : ruleInfoUris) {
            try {
                String ruleInfo = fetchRuleInfo(ruleInfoUri);
                List<RuleMetadata> fetchedRules = RuleInfoParser.parse(ruleInfo);
                saveRuleInfoIntoCache(ruleInfo, ruleCachePath);
                LOGGER.info("Loaded {} Ballerina rules from {}", fetchedRules.size(), ruleInfoUri);
                return fetchedRules;
            } catch (SonarBallerinaException e) {
                LOGGER.warn("Failed to load the Ballerina rules from {}: {}", ruleInfoUri, describe(e));
            }
        }

        Optional<List<RuleMetadata>> cachedRules = loadRulesFromCache(ruleCachePath);
        if (cachedRules.isPresent()) {
            LOGGER.warn("Loaded {} Ballerina rules from the copy cached at {} by the last successful fetch",
                    cachedRules.get().size(), ruleCachePath);
            return cachedRules.get();
        }

        List<RuleMetadata> bundledRules = RuleInfoParser.parse(readBundledResource(BUNDLED_RULE_INFO_PATH));
        LOGGER.warn("Failed to fetch rule-info.json from {}. Loaded {} Ballerina rules from the snapshot bundled "
                + "with the plugin, which may be outdated", ruleInfoUris, bundledRules.size());
        return bundledRules;
    }

    private String fetchRuleInfo(URI ruleInfoUri) throws SonarBallerinaException {
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(ruleInfoUri)
                .timeout(FETCH_TIMEOUT)
                .header("Accept", "application/json")
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new SonarBallerinaException("Unexpected status code " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SonarBallerinaException("Interrupted while fetching rule-info.json", e);
        } catch (IOException e) {
            throw new SonarBallerinaException("Failed to fetch rule-info.json", e);
        }
    }

    private static Optional<List<RuleMetadata>> loadRulesFromCache(Path ruleCachePath) {
        if (!Files.exists(ruleCachePath)) {
            return Optional.empty();
        }
        // java.io is used instead of Files, since an NIO file channel fails on a thread whose interrupt flag is set,
        // which is the case after an interrupted fetch
        try (InputStream in = new FileInputStream(ruleCachePath.toFile())) {
            return Optional.of(RuleInfoParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException | SonarBallerinaException e) {
            LOGGER.warn("Failed to load the Ballerina rules cached at {}: {}", ruleCachePath, describe(e));
            return Optional.empty();
        }
    }

    private static void saveRuleInfoIntoCache(String ruleInfo, Path ruleCachePath) {
        Path cacheDir = ruleCachePath.getParent();
        try {
            if (cacheDir != null && !cacheDir.toFile().isDirectory() && !cacheDir.toFile().mkdirs()) {
                throw new IOException("Failed to create the directory " + cacheDir);
            }
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(ruleCachePath.toFile()),
                    StandardCharsets.UTF_8)) {
                writer.write(ruleInfo);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to cache the Ballerina rules at {}: {}", ruleCachePath, e.getMessage());
        }
    }

    /**
     * Returns the URLs to fetch rule-info.json from, in order: the overridden or bundled URL, then the fallback URL.
     */
    static List<URI> getRuleInfoUris(Optional<String> override) throws SonarBallerinaException {
        Properties source = getBundledRuleInfoSource();
        URI primary = toHttpUri(override.filter(u -> !u.isBlank())
                .orElseGet(() -> getRequiredProperty(source, "url")));
        URI fallback = toHttpUri(getRequiredProperty(source, "fallbackUrl"));
        return primary.equals(fallback) ? List.of(primary) : List.of(primary, fallback);
    }

    private static URI toHttpUri(String uri) throws SonarBallerinaException {
        try {
            URI ruleInfoUri = URI.create(uri.strip());
            if (!"https".equalsIgnoreCase(ruleInfoUri.getScheme()) && !"http".equalsIgnoreCase(
                    ruleInfoUri.getScheme())) {
                throw new SonarBallerinaException("The URL of rule-info.json must be an HTTP(S) URL: " + uri);
            }
            return ruleInfoUri;
        } catch (IllegalArgumentException e) {
            throw new SonarBallerinaException("Invalid URL of rule-info.json: " + uri, e);
        }
    }

    private static Properties getBundledRuleInfoSource() {
        Properties source = new Properties();
        try {
            source.load(new StringReader(readBundledResource(RULE_INFO_SOURCE_PATH)));
        } catch (SonarBallerinaException | IOException e) {
            throw new IllegalStateException("Failed to read the source of rule-info.json from "
                    + RULE_INFO_SOURCE_PATH, e);
        }
        return source;
    }

    private static String getRequiredProperty(Properties source, String key) {
        String value = source.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Failed to find '" + key + "' in " + RULE_INFO_SOURCE_PATH);
        }
        return value;
    }

    static String readBundledResource(String path) throws SonarBallerinaException {
        try (InputStream in = RuleGenerator.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new SonarBallerinaException("Failed to find the bundled resource at " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SonarBallerinaException("Failed to read the bundled resource at " + path, e);
        }
    }

    private static String describe(Exception e) {
        Throwable cause = e.getCause();
        return cause == null || cause.getMessage() == null ? e.getMessage() : e.getMessage() + ": " + cause;
    }
}
