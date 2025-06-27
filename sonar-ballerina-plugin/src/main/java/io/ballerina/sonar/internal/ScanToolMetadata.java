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

/**
 * Represents metadata of the scan tool, including its README and BALA URL.
 *
 * @since 0.2.0
 */
class ScanToolMetadata {
    private String readme;
    private String balaURL;

    public ScanToolMetadata(String readme, String balaUrl) {
        this.readme = readme;
        this.balaURL = balaUrl;
    }

    public String getReadme() {
        return readme;
    }

    public void setReadme(String readme) {
        this.readme = readme;
    }

    public String getBalaURL() {
        return balaURL;
    }

    public void setBalaURL(String balaURL) {
        this.balaURL = balaURL;
    }
}
