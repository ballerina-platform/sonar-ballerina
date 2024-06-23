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

import org.sonar.api.SonarRuntime;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonarsource.analyzer.commons.RuleMetadataLoader;

import static io.ballerina.sonar.BallerinaChecks.CORE_RULES;
import static io.ballerina.sonar.Constants.JSON_PROFILE_PATH;
import static io.ballerina.sonar.Constants.LANGUAGE_KEY;
import static io.ballerina.sonar.Constants.RULE_REPOSITORY_KEY;
import static io.ballerina.sonar.Constants.RULE_REPOSITORY_NAME;
import static io.ballerina.sonar.Constants.RULE_RESOURCE_FOLDER;

public class BallerinaRulesDefinition implements RulesDefinition {
    private final SonarRuntime runtime;
    public BallerinaRulesDefinition(SonarRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * Define the rules for the Ballerina language.
     * <p>There are two ways to define a rule for a language: </p>
     *  <ol>
     *     <li>Add rules by the annotated class (Requires defining the Java class visitors in a checks folder)</li>
     *     <li>Add rules by the rule keys</li>
     *  </ol>
     * <p>Since in Ballerina the visitors are defined in the Ballerina static code analysis tool rule keys are used </p>
     *
     * @param context The rule definition context
     */
    @Override
    public void define(Context context) {
        NewRepository repository = context.createRepository(RULE_REPOSITORY_KEY, LANGUAGE_KEY);
        repository.setName(RULE_REPOSITORY_NAME);
        RuleMetadataLoader ruleMetadataLoader = new RuleMetadataLoader(RULE_RESOURCE_FOLDER, JSON_PROFILE_PATH,
                runtime);
        ruleMetadataLoader.addRulesByRuleKey(repository, CORE_RULES);
        repository.done();
    }
}
