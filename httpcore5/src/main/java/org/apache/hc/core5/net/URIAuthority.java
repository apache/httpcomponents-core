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

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.LangUtils;
import org.apache.hc.core5.util.TextUtils;
import org.apache.hc.core5.util.Tokenizer;

/**
 * Represents authority component of request {@link java.net.URI}.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class URIAuthority implements NamedEndpoint, Serializable {

    private static final long serialVersionUID = 1L;
    private final String userInfo;
    private final Host host;

    static URIAuthority parse(final CharSequence s, final Tokenizer.Cursor cursor) throws URISyntaxException {
        final Tokenizer tokenizer = Tokenizer.INSTANCE;
        String userInfo = null;
        final int initPos = cursor.getPos();
        final String token = tokenizer.parseContent(s, cursor, URISupport.HOST_SEPARATORS);
        if (!cursor.atEnd() && s.charAt(cursor.getPos()) == '@') {
            cursor.updatePos(cursor.getPos() + 1);
            if (!TextUtils.isBlank(token)) {
                userInfo = token;
            }
        } else {
            //Rewind
            cursor.updatePos(initPos);
        }
        final Host host = Host.parse(s, cursor);
        return new URIAuthority(userInfo, host);
    }

    static URIAuthority parse(final CharSequence s) throws URISyntaxException {
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());
        return parse(s, cursor);
    }

    static void format(final StringBuilder buf, final URIAuthority uriAuthority) {
        if (uriAuthority.getUserInfo() != null) {
            buf.append(uriAuthority.getUserInfo());
            buf.append("@");
        }
        Host.format(buf, uriAuthority);
    }

    static String format(final URIAuthority uriAuthority) {
        final StringBuilder buf = new StringBuilder();
        format(buf, uriAuthority);
        return buf.toString();
    }

    /**
     * @throws IllegalArgumentException
     *             If the port parameter is outside the specified range of valid port values, which is between 0 and
     *             65535, inclusive. {@code -1} indicates the scheme default port.
     */
    public URIAuthority(final String userInfo, final String hostname, final int port) {
        super();
        this.userInfo = userInfo;
        this.host = new Host(hostname, port);
    }

    public URIAuthority(final String hostname, final int port) {
        this(null, hostname, port);
    }

    /**
     * @since 5.2
     */
    public URIAuthority(final String userInfo, final Host host) {
        super();
        Args.notNull(host, "Host");
        this.userInfo = userInfo;
        this.host = host;
    }

    /**
     * @since 5.2
     */
    public URIAuthority(final Host host) {
        this(null, host);
    }

    /**
     * @since 5.2
     */
    public URIAuthority(final String userInfo, final NamedEndpoint endpoint) {
        super();
        Args.notNull(endpoint, "Endpoint");
        this.userInfo = userInfo;
        this.host = new Host(endpoint.getHostName(), endpoint.getPort());
    }

    public URIAuthority(final NamedEndpoint namedEndpoint) {
        this(null, namedEndpoint);
    }

    /**
     * Creates {@code URIHost} instance from string. Text may not contain any blanks.
     */
    public static URIAuthority create(final String s) throws URISyntaxException {
        if (TextUtils.isBlank(s)) {
            return null;
        }
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());
        final URIAuthority uriAuthority = parse(s, cursor);
        if (!cursor.atEnd()) {
            throw URISupport.createException(s, cursor, "Unexpected content");
        }
        return uriAuthority;
    }

    public URIAuthority(final String hostname) {
        this(null, hostname, -1);
    }

    public String getUserInfo() {
        return userInfo;
    }

    @Override
    public String getHostName() {
        return host.getHostName();
    }

    @Override
    public int getPort() {
        return host.getPort();
    }

    @Override
    public String toString() {
        return format(this);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof URIAuthority) {
            final URIAuthority that = (URIAuthority) obj;
            return LangUtils.equals(this.userInfo, that.userInfo) &&
                    LangUtils.equals(this.host, that.host);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, userInfo);
        hash = LangUtils.hashCode(hash, host);
        return hash;
    }

}
