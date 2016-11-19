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
import java.net.URISyntaxException;
import java.util.Locale;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.LangUtils;
import org.apache.hc.core5.util.TextUtils;

/**
 * Represents authority component of request URI.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class URIAuthority implements NamedEndpoint, Serializable {

    private final String userInfo;
    private final String hostname;
    private final int port;

    private URIAuthority(final String userInfo, final String hostname, final int port, final boolean internal) {
        super();
        this.userInfo = userInfo;
        this.hostname = hostname;
        this.port = port;
    }

    public URIAuthority(final String userInfo, final String hostname, final int port) {
        super();
        Args.containsNoBlanks(hostname, "Host name");
        if (userInfo != null) {
            Args.containsNoBlanks(userInfo, "User info");
        }
        this.userInfo = userInfo;
        this.hostname = hostname.toLowerCase(Locale.ROOT);
        this.port = port;
    }

    public URIAuthority(final String hostname, final int port) {
        this(null, hostname, port);
    }

    public URIAuthority(final NamedEndpoint namedEndpoint) {
        this(null, namedEndpoint.getHostName(), namedEndpoint.getPort());
    }

    /**
     * Creates {@code URIHost} instance from string. Text may not contain any blanks.
     */
    public static URIAuthority create(final String s) throws URISyntaxException {
        if (s == null) {
            return null;
        }
        String userInfo = null;
        String hostname = s;
        int port = -1;
        final int portIdx = hostname.lastIndexOf(":");
        if (portIdx > 0) {
            try {
                port = Integer.parseInt(hostname.substring(portIdx + 1));
            } catch (final NumberFormatException ex) {
                throw new URISyntaxException(s, "invalid port");
            }
            hostname = hostname.substring(0, portIdx);
        }
        final int atIdx = hostname.lastIndexOf("@");
        if (atIdx > 0) {
            userInfo = hostname.substring(0, atIdx);
            if (TextUtils.containsBlanks(userInfo)) {
                throw new URISyntaxException(s, "user info contains blanks");
            }
            hostname = hostname.substring(atIdx + 1);
        }
        if (TextUtils.containsBlanks(hostname)) {
            throw new URISyntaxException(s, "hostname contains blanks");
        }
        return new URIAuthority(userInfo, hostname.toLowerCase(Locale.ROOT), port, true);
    }

    public URIAuthority(final String hostname) {
        this(null, hostname, -1);
    }

    public String getUserInfo() {
        return userInfo;
    }

    @Override
    public String getHostName() {
        return hostname;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder();
        if (userInfo != null) {
            buffer.append(userInfo);
            buffer.append("@");
        }
        buffer.append(hostname);
        if (port != -1) {
            buffer.append(":");
            buffer.append(Integer.toString(port));
        }
        return buffer.toString();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof URIAuthority) {
            final URIAuthority that = (URIAuthority) obj;
            return LangUtils.equals(this.userInfo, that.userInfo) &&
                    LangUtils.equals(this.hostname, that.hostname) &&
                    this.port == that.port;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, userInfo);
        hash = LangUtils.hashCode(hash, hostname);
        hash = LangUtils.hashCode(hash, port);
        return hash;
    }

}
