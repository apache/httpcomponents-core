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
import java.util.BitSet;
import java.util.Locale;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
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
    private final String hostname;
    private final int port;

    private static final BitSet HOST_SEPARATORS = new BitSet(256);
    private static final BitSet PORT_SEPARATORS = new BitSet(256);
    private static final BitSet TERMINATORS = new BitSet(256);

    static {
        TERMINATORS.set('/');
        TERMINATORS.set('#');
        TERMINATORS.set('?');
        HOST_SEPARATORS.or(TERMINATORS);
        HOST_SEPARATORS.set('@');
        PORT_SEPARATORS.or(TERMINATORS);
        PORT_SEPARATORS.set(':');
    }

    static URISyntaxException createException(
            final CharSequence input, final Tokenizer.Cursor cursor, final String reason) {
        return new URISyntaxException(
                input.subSequence(cursor.getLowerBound(), cursor.getUpperBound()).toString(),
                reason,
                cursor.getPos());
    }

    static URIAuthority parse(final CharSequence s, final Tokenizer.Cursor cursor) throws URISyntaxException {
        final Tokenizer tokenizer = Tokenizer.INSTANCE;
        String userInfo = null;
        final int initPos = cursor.getPos();
        final String token = tokenizer.parseContent(s, cursor, HOST_SEPARATORS);
        if (!cursor.atEnd() && s.charAt(cursor.getPos()) == '@') {
            cursor.updatePos(cursor.getPos() + 1);
            if (!TextUtils.isBlank(token)) {
                userInfo = token;
            }
        } else {
            //Rewind
            cursor.updatePos(initPos);
        }
        final String hostName = tokenizer.parseContent(s, cursor, PORT_SEPARATORS);
        String portText = null;
        if (!cursor.atEnd() && s.charAt(cursor.getPos()) == ':') {
            cursor.updatePos(cursor.getPos() + 1);
            portText = tokenizer.parseContent(s, cursor, TERMINATORS);
        }
        final int port;
        if (!TextUtils.isBlank(portText)) {
            try {
                port = Integer.parseInt(portText);
            } catch (final NumberFormatException ex) {
                throw createException(s, cursor, "Authority port is invalid");
            }
        } else {
            port = -1;
        }
        return new URIAuthority(userInfo, hostName.toLowerCase(Locale.ROOT), port, true);
    }

    static URIAuthority parse(final CharSequence s) throws URISyntaxException {
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());
        return parse(s, cursor);
    }

    static void format(final StringBuilder buf, final URIAuthority uriAuthority) {
        if (uriAuthority.userInfo != null) {
            buf.append(uriAuthority.userInfo);
            buf.append("@");
        }
        buf.append(uriAuthority.hostname);
        if (uriAuthority.port != -1) {
            buf.append(":");
            buf.append(uriAuthority.port);
        }
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
    private URIAuthority(final String userInfo, final String hostname, final int port, final boolean internal) {
        super();
        this.userInfo = userInfo;
        this.hostname = hostname;
        this.port = Ports.checkWithDefault(port);
    }

    /**
     * @throws IllegalArgumentException
     *             If the port parameter is outside the specified range of valid port values, which is between 0 and
     *             65535, inclusive. {@code -1} indicates the scheme default port.
     */
    public URIAuthority(final String userInfo, final String hostname, final int port) {
        super();
        this.userInfo = userInfo;
        this.hostname = hostname != null ? hostname.toLowerCase(Locale.ROOT) : null;
        this.port = Ports.checkWithDefault(port);
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
        if (TextUtils.isBlank(s)) {
            return null;
        }
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());
        final URIAuthority uriAuthority = parse(s, cursor);
        if (!cursor.atEnd()) {
            throw createException(s, cursor, "Unexpected content");
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
        return hostname;
    }

    @Override
    public int getPort() {
        return port;
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
