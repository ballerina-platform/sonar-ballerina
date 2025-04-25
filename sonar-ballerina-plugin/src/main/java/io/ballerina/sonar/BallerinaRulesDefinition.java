/*
 * Copyright (c) 2024, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.ballerina.sonar;

import com.google.gson.Gson;
import org.sonar.api.SonarRuntime;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonarsource.analyzer.commons.RuleMetadataLoader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static io.ballerina.sonar.Constants.JSON_PROFILE_PATH;
import static io.ballerina.sonar.Constants.LANGUAGE_KEY;
import static io.ballerina.sonar.Constants.RULE_REPOSITORY_KEY;
import static io.ballerina.sonar.Constants.RULE_REPOSITORY_NAME;
import static io.ballerina.sonar.Constants.RULE_RESOURCE_FOLDER;

/**
 * Represents the implementation of the {@link RulesDefinition} class for Ballerina.
 *
 * @since 0.1.0
 */
public class BallerinaRulesDefinition implements RulesDefinition {
    private final SonarRuntime runtime;
    private final Gson gson = new Gson();

    public BallerinaRulesDefinition(SonarRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void define(Context context) {
        NewRepository repository = context.createRepository(RULE_REPOSITORY_KEY, LANGUAGE_KEY);
        repository.setName(RULE_REPOSITORY_NAME);
        RuleMetadataLoader ruleMetadataLoader = new RuleMetadataLoader(RULE_RESOURCE_FOLDER, JSON_PROFILE_PATH,
                runtime);
        ruleMetadataLoader.addRulesByRuleKey(repository, loadRuleKeys());
        repository.done();
    }

    private List<String> loadRuleKeys() {
        InputStream inputStream = BallerinaRulesDefinition.class.getResourceAsStream(JSON_PROFILE_PATH);
        if (inputStream == null) {
            throw new RuntimeException("Resource not found: " + JSON_PROFILE_PATH);
        }
        InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        Gson gson = new Gson();
        Profile profile = gson.fromJson(reader, Profile.class);
        return profile.ruleKeys();
    }

    record Profile(String name, List<String> ruleKeys) {
    }
}
