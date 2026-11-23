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

package io.ballerina.sonar;

import org.sonar.api.Plugin;
import org.sonar.api.SonarEdition;
import org.sonar.api.SonarQubeSide;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.internal.PluginContextImpl;
import org.sonar.api.internal.SonarRuntimeImpl;
import org.sonar.api.utils.Version;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

import static io.ballerina.sonar.Constants.FILE_SUFFIXES_KEY;

public class BallerinaPluginTest {

    @Test(description = "The plugin defines the file suffixes, which scanners need to detect Ballerina files")
    void testFileSuffixesProperty() {
        Plugin.Context context = new PluginContextImpl.Builder()
                .setSonarRuntime(SonarRuntimeImpl.forSonarQube(Version.create(14, 1), SonarQubeSide.SERVER,
                        SonarEdition.COMMUNITY))
                .build();
        new BallerinaPlugin().define(context);

        List<?> extensions = context.getExtensions();
        PropertyDefinition suffixes = extensions.stream()
                .filter(PropertyDefinition.class::isInstance)
                .map(PropertyDefinition.class::cast)
                .filter(definition -> definition.key().equals(FILE_SUFFIXES_KEY))
                .findFirst()
                .orElseThrow();
        Assert.assertEquals(suffixes.defaultValue(), "bal");
        Assert.assertTrue(suffixes.multiValues());
        Assert.assertTrue(extensions.contains(BallerinaSensor.class));
    }
}
