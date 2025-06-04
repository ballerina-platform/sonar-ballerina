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

/**
 * Custom exception class for handling errors in the Sonar Ballerina plugin.
 *
 * @since 0.2.0
 */
public class SonarBallerinaException extends Exception {

    /**
     * Constructs a new SonarBallerinaException with the specified detail message.
     * @param message the detail message
     */
    public SonarBallerinaException(String message) {
        super(message);
    }

    /**
     * Constructs a new SonarBallerinaException with the specified detail message and cause.
     * @param message the detail message
     * @param cause the original throwable that caused this exception
     */
    public SonarBallerinaException(String message, Throwable cause) {
        super(message, cause);
    }
}
