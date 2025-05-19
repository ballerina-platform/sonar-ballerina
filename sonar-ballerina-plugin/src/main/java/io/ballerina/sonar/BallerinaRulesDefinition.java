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

import io.ballerina.sonar.internal.RuleGenerator;
import io.ballerina.sonar.internal.RuleMetadata;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition;

import java.util.List;

import static io.ballerina.sonar.Constants.LANGUAGE_KEY;
import static io.ballerina.sonar.Constants.RULE_REPOSITORY_KEY;
import static io.ballerina.sonar.Constants.RULE_REPOSITORY_NAME;

/**
 * Represents the implementation of the {@link RulesDefinition} class for Ballerina.
 *
 * @since 0.1.0
 */
public class BallerinaRulesDefinition implements RulesDefinition {

    @Override
    public void define(Context context) {
        NewRepository repository = context.createRepository(RULE_REPOSITORY_KEY, LANGUAGE_KEY);
        repository.setName(RULE_REPOSITORY_NAME);
        RuleGenerator ruleMetadataGenerator = RuleGenerator.getInstance();
        try {
            List<RuleMetadata> ruleDocs = ruleMetadataGenerator.generateRules();
            for (RuleMetadata ruleDoc : ruleDocs) {
                repository.createRule(ruleDoc.id())
                        .setName(ruleDoc.name())
                        .setType(RuleType.valueOf(ruleDoc.type()))
                        .setHtmlDescription(ruleDoc.description())
                        .setSeverity(ruleDoc.severity())
                        .addTags(ruleDoc.tags());
            }
        } catch (SonarBallerinaException e) {
            throw new RuntimeException("Error generating rules for Ballerina", e);
        }
        repository.done();
    }
}
