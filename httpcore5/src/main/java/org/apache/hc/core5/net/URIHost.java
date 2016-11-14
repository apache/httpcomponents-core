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

package org.apache.hc.core5.net;

import java.io.Serializable;
import java.util.Locale;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.LangUtils;

/**
 * Represents authority component of HTTP request URI.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class URIHost implements NamedEndpoint, Serializable {

    private final String hostname;
    private final int port;

    public URIHost(final String hostname, final int port) {
        super();
        this.hostname = Args.containsNoBlanks(hostname, "Host name").toLowerCase(Locale.ROOT);
        this.port = port;
    }

    /**
     * Creates {@code URIHost} instance from string. Text may not contain any blanks.
     */
    public static URIHost create(final String s) {
        if (s == null) {
            return null;
        }
        String hostname = s;
        int port = -1;
        final int portIdx = hostname.lastIndexOf(":");
        if (portIdx > 0) {
            try {
                port = Integer.parseInt(hostname.substring(portIdx + 1));
            } catch (final NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid URI host: " + s);
            }
            hostname = hostname.substring(0, portIdx);
        }
        return new URIHost(hostname, port);
    }

    public URIHost(final String hostname) {
        this(hostname, -1);
    }

    public String getHostName() {
        return this.hostname;
    }

    public int getPort() {
        return this.port;
    }

    @Override
    public String toString() {
        if (this.port != -1) {
            final StringBuilder buffer = new StringBuilder();
            buffer.append(hostname);
            buffer.append(":");
            buffer.append(Integer.toString(port));
            return buffer.toString();
        }
        return hostname;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof URIHost) {
            final URIHost that = (URIHost) obj;
            return this.hostname.equals(that.hostname) && this.port == that.port;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.hostname);
        hash = LangUtils.hashCode(hash, this.port);
        return hash;
    }

}
