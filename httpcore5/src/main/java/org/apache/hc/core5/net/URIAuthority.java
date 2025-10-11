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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.LangUtils;
import org.apache.hc.core5.util.TextUtils;
import org.apache.hc.core5.util.Tokenizer;

/**
 * Represents authority component of request {@link URI}.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class URIAuthority implements NamedEndpoint, Serializable {

    private static final long serialVersionUID = 1L;
    private final String userInfo;
    private final Host host;

    static URIAuthority parse(final CharSequence s) throws URISyntaxException {
        if (TextUtils.isBlank(s)) {
            return null;
        }
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());
        return parse(s, cursor); // intentionally no cursor.atEnd() check
    }

    static URIAuthority parse(final CharSequence s, final Tokenizer.Cursor cursor) throws URISyntaxException {
        final Tokenizer tokenizer = Tokenizer.INSTANCE;
        String userInfo = null;

        // optional userinfo@
        final int initPos = cursor.getPos();
        final String token = tokenizer.parseContent(s, cursor, URISupport.HOST_DELIMITERS);
        if (!cursor.atEnd() && s.charAt(cursor.getPos()) == '@') {
            cursor.updatePos(cursor.getPos() + 1);
            if (!TextUtils.isBlank(token)) {
                userInfo = token;
            }
        } else {
            cursor.updatePos(initPos);
        }

        if (!cursor.atEnd() && s.charAt(cursor.getPos()) == '[') {
            final int lb = cursor.getPos();
            final int upper = cursor.getUpperBound();
            int rb = -1;
            for (int i = lb + 1; i < upper; i++) {
                if (s.charAt(i) == ']') {
                    rb = i;
                    break;
                }
            }
            if (rb < 0) {
                throw URISupport.createException(s.toString(), cursor, "Expected closing bracket for IPv6 address");
            }

            final String literal = s.subSequence(lb + 1, rb).toString();
            final int zoneMark = literal.indexOf("%25");
            final String addrPart = zoneMark >= 0 ? literal.substring(0, zoneMark) : literal;

            int colons = 0;
            for (int i = 0; i < addrPart.length(); i++) {
                if (addrPart.charAt(i) == ':') {
                    if (++colons >= 2) {
                        break;
                    }
                }
            }
            if (colons < 2) {
                throw URISupport.createException(s.toString(), cursor, "Expected an IPv6 address");
            }

            if (zoneMark >= 0) {
                final String zoneEnc = literal.substring(zoneMark + 3);
                ZoneIdSupport.validateZoneIdEncoded(zoneEnc);
            }
            // Store host in friendly form: "...%<decoded-zone>"  (or literal as-is if no zone)
            final String hostName = ZoneIdSupport.decodeZoneId(literal);

            // optional :port
            int pos = rb + 1;
            int port = -1;
            if (pos < upper && s.charAt(pos) == ':') {
                pos++;
                if (pos >= upper || !Character.isDigit(s.charAt(pos))) {
                    throw URISupport.createException(s.toString(), cursor, "Invalid port");
                }
                long acc = 0;
                while (pos < upper && Character.isDigit(s.charAt(pos))) {
                    acc = acc * 10 + (s.charAt(pos) - '0');
                    if (acc > 65535) {
                        throw URISupport.createException(s.toString(), cursor, "Port out of range");
                    }
                    pos++;
                }
                port = (int) acc;
            }
            cursor.updatePos(pos);
            return new URIAuthority(userInfo, hostName, port);
        }

        {
            final int start = cursor.getPos();
            final int upper = cursor.getUpperBound();
            int i = start;
            int colonCount = 0;
            while (i < upper) {
                final char ch = s.charAt(i);
                if (ch == '/' || ch == '?' || ch == '#') {
                    break; // end of authority
                }
                if (ch == ']') {
                    break; // safety
                }
                if (ch == ':') {
                    if (++colonCount > 1) {
                        throw URISupport.createException(s.toString(), cursor, "Expected an IPv6 address");
                    }
                }
                i++;
            }
        }

        final Host host = Host.parse(s, cursor);
        return new URIAuthority(userInfo, host);
    }


    static void format(final StringBuilder buf, final URIAuthority uriAuthority) {
        if (uriAuthority.getUserInfo() != null) {
            buf.append(uriAuthority.getUserInfo()).append("@");
        }
        final String hostName = uriAuthority.getHostName();
        final int port = uriAuthority.getPort();

        if (ZoneIdSupport.appendBracketedIPv6(buf, hostName)) {
            if (port >= 0) {
                buf.append(':').append(port);
            }
        } else {
            Host.format(buf, uriAuthority);
        }
    }


    static String format(final URIAuthority uriAuthority) {
        final StringBuilder buf = new StringBuilder();
        format(buf, uriAuthority);
        return buf.toString();
    }

    /**
     * Constructs a new instance.
     *
     * @param userInfo The user info, may be null.
     * @param hostName The host name, not null.
     * @param port The port value, between 0 and 65535, inclusive. {@code -1} indicates the scheme default port.
     * @throws NullPointerException     If the {@code name} is {@code null}.
     * @throws IllegalArgumentException If the port is outside the specified range of valid port values, which is between 0 and 65535, inclusive.
     *                                  {@code -1} indicates the scheme default port.
     */
    public URIAuthority(final String userInfo, final String hostName, final int port) {
        super();
        this.userInfo = userInfo;
        this.host = new Host(hostName, port);
    }

    /**
     * Constructs a new instance.
     *
     * @param hostName The host name, not null.
     * @param port The port value, between 0 and 65535, inclusive. {@code -1} indicates the scheme default port.
     * @throws NullPointerException     If the {@code name} is {@code null}.
     * @throws IllegalArgumentException If the port is outside the specified range of valid port values, which is between 0 and 65535, inclusive.
     *                                  {@code -1} indicates the scheme default port.
     */
    public URIAuthority(final String hostName, final int port) {
        this(null, hostName, port);
    }

    /**
     * Constructs a new instance.
     *
     * @param userInfo The user info, may be null.
     * @param host     The host, never null.
     * @throws NullPointerException if the {@code host} is {@code null}.
     * @since 5.2
     */
    public URIAuthority(final String userInfo, final Host host) {
        super();
        this.host = Args.notNull(host, "Host");
        this.userInfo = userInfo;
    }

    /**
     * Constructs a new instance.
     *
     * @param host     The host, never null.
     * @throws NullPointerException if the {@code host} is {@code null}.
     * @since 5.2
     */
    public URIAuthority(final Host host) {
        this(null, host);
    }

    /**
     * Constructs a new instance.
     *
     * @param userInfo The user info, may be null.
     * @param endpoint The named end-point, never null.
     * @throws NullPointerException     If the end-point is {@code null}.
     * @throws NullPointerException     If the end-point {@code name} is {@code null}.
     * @throws IllegalArgumentException If the end-point port is outside the specified range of valid port values, which is between 0 and 65535, inclusive.
     *                                  {@code -1} indicates the scheme default port.
     * @since 5.2
     */
    public URIAuthority(final String userInfo, final NamedEndpoint endpoint) {
        super();
        Args.notNull(endpoint, "Endpoint");
        this.userInfo = userInfo;
        this.host = new Host(endpoint.getHostName(), endpoint.getPort());
    }

    /**
     * Constructs a new instance.
     *
     * @param namedEndpoint The named end-point, never null.
     * @throws NullPointerException     If the end-point is {@code null}.
     * @throws NullPointerException     If the end-point {@code name} is {@code null}.
     * @throws IllegalArgumentException If the end-point port is outside the specified range of valid port values, which is between 0 and 65535, inclusive.
     *                                  {@code -1} indicates the scheme default port.
     */
    public URIAuthority(final NamedEndpoint namedEndpoint) {
        this(null, namedEndpoint);
    }

    /**
     * Creates a {@code URIAuthority} instance from a string. Text may not contain any blanks.
     *
     * @param s The value to parse
     * @return The parsed URIAuthority.
     * @throws URISyntaxException Thrown if a string could not be parsed as a URIAuthority.
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

    /**
     * Constructs a new instance.
     *
     * @param hostName The host name, not null.
     * @throws NullPointerException     If the {@code name} is {@code null}.
     */
    public URIAuthority(final String hostName) {
        this(null, hostName, -1);
    }

    /**
     * Gets the user info String.
     *
     * @return the user info String.
     */
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
            return Objects.equals(this.userInfo, that.userInfo) &&
                    Objects.equals(this.host, that.host);
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
