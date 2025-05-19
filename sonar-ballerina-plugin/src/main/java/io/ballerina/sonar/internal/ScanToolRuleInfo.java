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
 * Represents a rule in the rule-info.json in the scan tool.
 *
 * @since 0.2.0
 */
class ScanToolRuleInfo {
    private String title;
    private String type;
    private String status;
    private Remediation remediation;
    private String[] tags;
    private String defaultSeverity;
    private String ruleSpecification;
    private String sqKey;
    private String scope;
    private String quickfix;

    // Getters and setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Remediation getRemediation() {
        return remediation;
    }

    public void setRemediation(Remediation remediation) {
        this.remediation = remediation;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }

    public String getDefaultSeverity() {
        return defaultSeverity;
    }

    public void setDefaultSeverity(String defaultSeverity) {
        this.defaultSeverity = defaultSeverity;
    }

    public String getRuleSpecification() {
        return ruleSpecification;
    }

    public void setRuleSpecification(String ruleSpecification) {
        this.ruleSpecification = ruleSpecification;
    }

    public String getSqKey() {
        return sqKey;
    }

    public void setSqKey(String sqKey) {
        this.sqKey = sqKey;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getQuickfix() {
        return quickfix;
    }

    public void setQuickfix(String quickfix) {
        this.quickfix = quickfix;
    }

    static class Remediation {
        private String func;
        private String constantCost;

        public String getFunc() {
            return func;
        }

        public void setFunc(String func) {
            this.func = func;
        }

        public String getConstantCost() {
            return constantCost;
        }

        public void setConstantCost(String constantCost) {
            this.constantCost = constantCost;
        }
    }
}
