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

import com.sun.net.httpserver.HttpServer;
import io.ballerina.sonar.SonarBallerinaException;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class RuleGeneratorTest {

    private static final String PRIMARY_RULE_INFO = "[{\"title\": \"Primary\", \"sqKey\": \"ballerina:1\"}]";
    private static final String FALLBACK_RULE_INFO = "[{\"title\": \"Fallback\", \"sqKey\": \"ballerina:1\"}]";

    private HttpServer server;
    private Path cachePath;

    @BeforeMethod
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        serve("/primary", 200, PRIMARY_RULE_INFO);
        serve("/fallback", 200, FALLBACK_RULE_INFO);
        serve("/missing", 404, "Not Found");
        serve("/invalid", 200, "<html></html>");
        server.start();
        cachePath = Files.createTempDirectory("sonar-ballerina").resolve("rule-info.json");
    }

    @AfterMethod
    void tearDown() throws IOException {
        server.stop(0);
        Files.deleteIfExists(cachePath);
        Files.deleteIfExists(cachePath.getParent());
    }

    @Test(description = "The rules are loaded from the first URL when it is available")
    void testPrimaryUrl() throws SonarBallerinaException {
        List<RuleMetadata> rules = load(List.of(uri("/primary"), uri("/fallback")));
        Assert.assertEquals(rules.get(0).name(), "Primary");
    }

    @Test(description = "The rules are loaded from the fallback URL when the first URL is unavailable or invalid")
    void testFallbackUrl() throws SonarBallerinaException {
        for (String path : List.of("/missing", "/invalid")) {
            List<RuleMetadata> rules = load(List.of(uri(path), uri("/fallback")));
            Assert.assertEquals(rules.get(0).name(), "Fallback", path);
        }
    }

    @Test(description = "The rules are loaded from the cache of the last successful fetch when no URL is available")
    void testCache() throws SonarBallerinaException {
        load(List.of(uri("/primary")));
        List<RuleMetadata> rules = load(List.of(uri("/missing")));
        Assert.assertEquals(rules.get(0).name(), "Primary");
    }

    @Test(description = "The rules are loaded from the bundled snapshot when no URL or cache is available")
    void testBundledSnapshot() throws SonarBallerinaException {
        List<RuleMetadata> rules = load(List.of(uri("/missing"), uri("/invalid")));
        String bundledRuleInfo = RuleGenerator.readBundledResource("/rule-info/rule-info.json");
        Assert.assertEquals(rules, RuleInfoParser.parse(bundledRuleInfo));
        Assert.assertFalse(Files.exists(cachePath));
    }

    private List<RuleMetadata> load(List<URI> ruleInfoUris) throws SonarBallerinaException {
        return RuleGenerator.getInstance().loadRules(ruleInfoUris, cachePath);
    }

    private void serve(String path, int status, String body) {
        server.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }

    private URI uri(String path) {
        return URI.create("http://" + server.getAddress().getAddress().getHostAddress() + ":"
                + server.getAddress().getPort() + path);
    }
}
