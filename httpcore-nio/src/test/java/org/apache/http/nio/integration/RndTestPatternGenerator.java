/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.nio.integration;

import java.util.Random;

final class RndTestPatternGenerator {

    private static final Random RND = new Random();
    private static final String TEST_CHARS = "0123456789ABCDEF";

    public static String generateText() {
        final StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            final char rndchar = TEST_CHARS.charAt(RND.nextInt(TEST_CHARS.length() - 1));
            buffer.append(rndchar);
        }
        return buffer.toString();
    }

    public static int generateCount(final int max) {
        return RND.nextInt(max - 1) + 1;
    }

}
