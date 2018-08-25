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
package org.apache.hc.core5.http2.config;

import org.apache.hc.core5.util.Args;

/**
 * HTTP/2 protocol settings.
 *
 * @since 5.0
 */
public final class H2Setting {

    private final H2Param param;
    private final int value;

    public H2Setting(final H2Param param, final int value) {
        Args.notNull(param, "Setting parameter");
        Args.notNegative(value, "Setting value must be a non-negative value");
        this.param = param;
        this.value = value;
    }

    public int getCode() {
        return param.code;
    }

    public int getValue() {
        return value;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder().append(param).append(": ").append(value);
        return sb.toString();
    }
}
