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

import org.sonar.api.Plugin;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinition.ConfigScope;

import static io.ballerina.sonar.Constants.FILE_SUFFIXES_DEFAULT_VALUE;
import static io.ballerina.sonar.Constants.FILE_SUFFIXES_KEY;

/**
 * Represents the implementation of the {@link Plugin} class for Ballerina.
 *
 * @since 0.1.0
 */
public class BallerinaPlugin implements Plugin {
    @Override
    public void define(Context context) {
        context.addExtensions(
                PropertyDefinition.builder(FILE_SUFFIXES_KEY)
                        .name("File suffixes")
                        .description("List of suffixes of the Ballerina files to analyze.")
                        .category("Ballerina")
                        .defaultValue(FILE_SUFFIXES_DEFAULT_VALUE)
                        .multiValues(true)
                        .onConfigScopes(ConfigScope.PROJECT)
                        .build(),
                BallerinaLanguage.class,
                BallerinaSensor.class,
                BallerinaRulesDefinition.class,
                BallerinaProfileDefinition.class
        );
    }
}
