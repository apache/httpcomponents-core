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
package org.apache.hc.core5.http2.setting;

import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.LangUtils;

public final class H2Setting {

    private final H2Param param;
    private final long value;

    public H2Setting(final H2Param param, final long value) {
        Args.notNull(param, "Setting parameter");
        Args.notNegative(value, "Setting value must be a non-negative value");
        this.param = param;
        this.value = value;
    }

    public H2Setting(final H2Param param) {
        Args.notNull(param, "Setting parameter");
        this.param = param;
        this.value = param.initialValue;
    }

    public int getCode() {
        return param.code;
    }

    public long getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof H2Setting) {
            final H2Setting that = (H2Setting) obj;
            return this.param.equals(that.param);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.param);
        return hash;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder()
                .append("[").append(param).append(":").append(value).append(']');
        return sb.toString();
    }
};
