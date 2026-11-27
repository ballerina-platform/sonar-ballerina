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
import org.sonar.api.SonarRuntime;
import org.sonar.api.config.Configuration;
import org.sonar.api.platform.Server;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Loads the rules of the Ballerina rule repository from the rule-info.json file of the scan tool.
 * <p>
 * The file is fetched from the URL bundled at build time, which the {@value #RULE_INFO_URL_PROPERTY} server property
 * overrides, and failing that, from the fallback URL bundled at build time. When both fetches fail, the rules are
 * loaded from the copy cached by the last successful fetch, and failing that, from the snapshot bundled with the
 * plugin.
 * <p>
 * The request to the primary URL carries the versions of the plugin, the SonarQube server and the scan tool, and the
 * edition of the SonarQube server, as query parameters, so that the endpoint can serve the rules that match them. The
 * server does not run the scan tool, so its version is taken from the {@value #SCAN_TOOL_VERSION_PROPERTY} server
 * property, and is {@value #LATEST_SCAN_TOOL_VERSION} when the property is not set.
 */
public final class RuleGenerator {

    public static final String RULE_INFO_URL_PROPERTY = "sonar.ballerina.ruleInfoUrl";
    public static final String SCAN_TOOL_VERSION_PROPERTY = "sonar.ballerina.scanToolVersion";

    static final String PLUGIN_VERSION_PARAM = "pluginVersion";
    static final String SONAR_VERSION_PARAM = "sonarVersion";
    static final String SONAR_EDITION_PARAM = "sonarEdition";
    static final String SCAN_TOOL_VERSION_PARAM = "scanToolVersion";
    static final String LATEST_SCAN_TOOL_VERSION = "latest";

    private static final Logger LOGGER = LoggerFactory.getLogger(RuleGenerator.class);

    private static final String RULE_INFO_SOURCE_PATH = "/rule-info/source.properties";
    private static final String BUNDLED_RULE_INFO_PATH = "/rule-info/rule-info.json";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(30);
    private static final Path DEFAULT_RULE_CACHE_PATH = Paths.get(System.getProperty("user.home"),
            ".sonar-ballerina", "rule-info.json");
    // The cache file written by the plugin before 0.4.0
    private static final String LEGACY_RULE_CACHE_FILE = "rule-cache.json";
    private static final RuleGenerator INSTANCE = new RuleGenerator();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private List<RuleMetadata> rules;
    private Runnable pendingCacheWrite = () -> { };

    private RuleGenerator() {
    }

    public static RuleGenerator getInstance() {
        return INSTANCE;
    }

    /**
     * Loads the rules once, and returns the same rules on later calls.
     *
     * @param configuration the server configuration, which may override the URL of rule-info.json and the scan tool
     *                      version
     * @param server        the SonarQube server, whose version is sent to the rule-info.json endpoint
     * @param runtime       the SonarQube runtime, whose edition is sent to the rule-info.json endpoint
     * @return the rules of the Ballerina rule repository
     * @throws SonarBallerinaException if the rules cannot be loaded from any source
     */
    public synchronized List<RuleMetadata> loadRules(Configuration configuration, Server server, SonarRuntime runtime)
            throws SonarBallerinaException {
        if (rules == null) {
            Map<String, String> endpointParams = getEndpointParams(configuration.get(SCAN_TOOL_VERSION_PROPERTY),
                    server.getVersion(), runtime.getEdition().name().toLowerCase(Locale.ROOT));
            List<URI> ruleInfoUris = getRuleInfoUris(configuration.get(RULE_INFO_URL_PROPERTY), endpointParams);
            rules = List.copyOf(loadRules(ruleInfoUris, DEFAULT_RULE_CACHE_PATH));
        }
        return List.copyOf(rules);
    }

    /**
     * Caches the fetched rule-info.json. Call it only after SonarQube has registered the rules, so that a file it
     * rejects never replaces a good cache.
     */
    public synchronized void cacheFetchedRules() {
        pendingCacheWrite.run();
        pendingCacheWrite = () -> { };
    }

    synchronized List<RuleMetadata> loadRules(List<URI> ruleInfoUris, Path ruleCachePath)
            throws SonarBallerinaException {
        pendingCacheWrite = () -> { };
        boolean interrupted = false;
        try {
            for (URI ruleInfoUri : ruleInfoUris) {
                try {
                    String ruleInfo = fetchRuleInfo(ruleInfoUri);
                    List<RuleMetadata> fetchedRules = RuleInfoParser.parse(ruleInfo);
                    pendingCacheWrite = () -> saveRuleInfoIntoCache(ruleInfo, ruleCachePath);
                    LOGGER.info("Loaded {} Ballerina rules from {}", fetchedRules.size(), ruleInfoUri);
                    return fetchedRules;
                } catch (SonarBallerinaException e) {
                    LOGGER.warn("Failed to load the Ballerina rules from {}: {}", ruleInfoUri, describe(e));
                    // A set interrupt flag fails the next fetch at once, so clear it until the rules are loaded
                    interrupted |= Thread.interrupted();
                }
            }

            Optional<List<RuleMetadata>> cachedRules = loadRulesFromCache(ruleCachePath);
            if (cachedRules.isPresent()) {
                LOGGER.warn("Loaded {} Ballerina rules from the copy cached at {} by the last successful fetch",
                        cachedRules.get().size(), ruleCachePath);
                return cachedRules.get();
            }

            List<RuleMetadata> bundledRules = RuleInfoParser.parse(readBundledResource(BUNDLED_RULE_INFO_PATH));
            LOGGER.warn("Failed to fetch rule-info.json from {}. Loaded {} Ballerina rules from the snapshot "
                    + "bundled with the plugin, which may be outdated", ruleInfoUris, bundledRules.size());
            return bundledRules;
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
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
        try {
            return Optional.of(RuleInfoParser.parse(Files.readString(ruleCachePath, StandardCharsets.UTF_8)));
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
            return;
        }
        if (cacheDir != null) {
            deleteLegacyCache(cacheDir.resolve(LEGACY_RULE_CACHE_FILE));
        }
    }

    private static void deleteLegacyCache(Path legacyCachePath) {
        try {
            Files.deleteIfExists(legacyCachePath);
        } catch (IOException e) {
            LOGGER.debug("Failed to delete the legacy rule cache at {}: {}", legacyCachePath, e.getMessage());
        }
    }

    /**
     * Returns the query parameters sent to the rule-info.json endpoint. A blank scan tool version counts as not set.
     */
    static Map<String, String> getEndpointParams(Optional<String> scanToolVersion, String sonarVersion,
                                                 String sonarEdition) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put(PLUGIN_VERSION_PARAM, getRequiredProperty(getBundledRuleInfoSource(), PLUGIN_VERSION_PARAM));
        params.put(SONAR_VERSION_PARAM, sonarVersion);
        params.put(SONAR_EDITION_PARAM, sonarEdition);
        params.put(SCAN_TOOL_VERSION_PARAM, scanToolVersion.map(String::strip)
                .filter(version -> !version.isEmpty())
                .orElse(LATEST_SCAN_TOOL_VERSION));
        return params;
    }

    /**
     * Returns the URLs to fetch rule-info.json from, in order: the overridden or bundled URL, then the fallback URL.
     * An invalid override is skipped with a warning. The endpoint parameters are added to the first URL only, unless
     * it is the fallback URL, which serves a static file.
     */
    static List<URI> getRuleInfoUris(Optional<String> override, Map<String, String> endpointParams)
            throws SonarBallerinaException {
        Properties source = getBundledRuleInfoSource();
        URI primary = toHttpUri(getRequiredProperty(source, "url"));
        Optional<String> overrideUrl = override.filter(u -> !u.isBlank());
        if (overrideUrl.isPresent()) {
            try {
                primary = toHttpUri(overrideUrl.get());
            } catch (SonarBallerinaException e) {
                LOGGER.warn("Ignoring the {} property and using {} instead: {}", RULE_INFO_URL_PROPERTY, primary,
                        e.getMessage());
            }
        }
        URI fallback = toHttpUri(getRequiredProperty(source, "fallbackUrl"));
        if (primary.equals(fallback)) {
            return List.of(fallback);
        }
        return List.of(withQueryParams(primary, endpointParams), fallback);
    }

    private static URI withQueryParams(URI uri, Map<String, String> params) {
        if (params.isEmpty()) {
            return uri;
        }
        String query = params.entrySet().stream()
                .map(param -> encode(param.getKey()) + "=" + encode(param.getValue()))
                .collect(Collectors.joining("&"));
        String base = uri.toString();
        if (uri.getRawFragment() != null) {
            base = base.substring(0, base.indexOf('#'));
        }
        return URI.create(base + (uri.getRawQuery() == null ? "?" : "&") + query);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static URI toHttpUri(String uri) throws SonarBallerinaException {
        try {
            URI ruleInfoUri = URI.create(uri.strip());
            if (!"https".equalsIgnoreCase(ruleInfoUri.getScheme()) && !"http".equalsIgnoreCase(
                    ruleInfoUri.getScheme())) {
                throw new SonarBallerinaException("The URL of rule-info.json must be an HTTP(S) URL: " + uri);
            }
            if (ruleInfoUri.getHost() == null) {
                throw new SonarBallerinaException("The URL of rule-info.json has no host: " + uri);
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
