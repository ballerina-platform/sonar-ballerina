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

import com.google.gson.annotations.SerializedName;

import java.util.List;

record ScanToolRuleInfo(String title, String type, String status, Remediation remediation, List<String> tags,
                        String defaultSeverity, String sqKey, String scope, String cleanCodeAttribute,
                        List<Impact> impacts, SecurityStandards securityStandards, String helpUri,
                        Description description) {

    record Remediation(String func, String constantCost, String linearFactor, String linearOffset,
                       String linearDesc) {
    }

    record Impact(String softwareQuality, String severity) {
    }

    record SecurityStandards(List<Integer> cwe, @SerializedName("owaspTop10_2025") List<String> owaspTop10Y2025) {
    }

    record Description(String whyIsThisAnIssue, HowToFix howToFix, List<Link> moreInfo) {
    }

    record HowToFix(List<Example> examples) {
    }

    record Example(String title, List<CodeBlock> noncompliant, List<CodeBlock> compliant) {
    }

    record CodeBlock(String description, String language, String code, String explanation) {
    }

    record Link(String title, String url) {
    }
}
