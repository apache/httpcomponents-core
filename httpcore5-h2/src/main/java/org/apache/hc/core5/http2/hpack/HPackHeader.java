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

package org.apache.hc.core5.http2.hpack;

import org.apache.hc.core5.http.Header;

/**
 * Internal HPack header representation that also contains binary length of
 * header name and header value.
 */
final class HPackHeader implements Header {

    static private final int ENTRY_SIZE_OVERHEAD = 32;

    private final String name;
    private final int nameLen;
    private final String value;
    private final int valueLen;
    private final boolean sensitive;

    HPackHeader(final String name, final int nameLen, final String value, final int valueLen, final boolean sensitive) {
        this.name = name;
        this.nameLen = nameLen;
        this.value = value;
        this.valueLen = valueLen;
        this.sensitive = sensitive;
    }

    HPackHeader(final String name, final String value, final boolean sensitive) {
        this(name, name.length(), value, value.length(), sensitive);
    }

    HPackHeader(final String name, final String value) {
        this(name, value, false);
    }

    HPackHeader(final Header header) {
        this(header.getName(), header.getValue(), header.isSensitive());
    }

    @Override
    public String getName() {
        return name;
    }

    public int getNameLen() {
        return nameLen;
    }

    @Override
    public String getValue() {
        return value;
    }

    public int getValueLen() {
        return valueLen;
    }

    @Override
    public boolean isSensitive() {
        return sensitive;
    }

    public int getTotalSize() {
        return nameLen + valueLen + ENTRY_SIZE_OVERHEAD;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append(this.name).append(": ");
        if (this.value != null) {
            buf.append(this.value);
        }
        return buf.toString();
    }

}
