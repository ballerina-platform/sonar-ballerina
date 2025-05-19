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
import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition;

import java.util.List;

import static io.ballerina.sonar.Constants.LANGUAGE_KEY;
import static io.ballerina.sonar.Constants.PROFILE_NAME;
import static io.ballerina.sonar.Constants.RULE_REPOSITORY_KEY;

/**
 * Represents the implementation of the {@link BuiltInQualityProfilesDefinition} class for Ballerina.
 *
 * @since 0.1.0
 */
public class BallerinaProfileDefinition implements BuiltInQualityProfilesDefinition {
    @Override
    public void define(Context context) {
        NewBuiltInQualityProfile ballerinaQualityProfile = context.createBuiltInQualityProfile(PROFILE_NAME,
                LANGUAGE_KEY);
        RuleGenerator ruleGenerator = RuleGenerator.getInstance();
        try {
            List<RuleMetadata> ruleMetadata = ruleGenerator.generateRules();
            for (RuleMetadata metadata : ruleMetadata) {
                ballerinaQualityProfile.activateRule(RULE_REPOSITORY_KEY, metadata.id());
            }
            ballerinaQualityProfile.done();
        } catch (SonarBallerinaException e) {
            throw new RuntimeException("Error registering Ballerina profile", e);
        }
    }
}
