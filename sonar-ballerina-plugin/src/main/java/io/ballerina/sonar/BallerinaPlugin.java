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

public class BallerinaPlugin implements Plugin {

    /**
     * <p> Represents the entry point for introducing SonarQube plugins. </p>
     *
     * <p>
     * This class should be defined in the MANIFEST file for SonarQube to engage the plugin. This method is engaged only
     * after the SonarQube:
     * </p>
     * <ul>
     *     <li>Web server starts</li>
     *     <li>Compute engine starts</li>
     *     <li>Sonar Scanner starts</li>
     * </ul>
     *
     * @param context The sonar plugin context that accepts configurations
     */
    @Override
    public void define(Context context) {
        context.addExtensions(
                BallerinaLanguage.class,
                BallerinaSensor.class,
                BallerinaRulesDefinition.class,
                BallerinaProfileDefinition.class
        );
    }
}
