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
import org.apache.hc.core5.util.Tokenizer;

/**
 * Component that holds all details needed to describe a network connection
 * to a host. This includes remote host name and port.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class Host implements NamedEndpoint, Serializable {

    private static final long serialVersionUID = 1L;
    private final String name;
    private final String lcName;
    private final int port;

    public Host(final String name, final int port) {
        super();
        this.name = Args.notNull(name, "Host name");
        this.port = Ports.checkWithDefault(port);
        this.lcName = this.name.toLowerCase(Locale.ROOT);
    }

    static Host parse(final CharSequence s, final Tokenizer.Cursor cursor) throws URISyntaxException {
        final Tokenizer tokenizer = Tokenizer.INSTANCE;
        final String hostName;
        final boolean ipv6Brackets = !cursor.atEnd() && s.charAt(cursor.getPos()) == '[';
        if (ipv6Brackets) {
            cursor.updatePos(cursor.getPos() + 1);
            hostName = tokenizer.parseContent(s, cursor, URISupport.IPV6_HOST_TERMINATORS);
            if (cursor.atEnd() || !(s.charAt(cursor.getPos()) == ']')) {
                throw URISupport.createException(s, cursor, "Expected an IPv6 closing bracket ']'");
            }
            cursor.updatePos(cursor.getPos() + 1);
            if (!InetAddressUtils.isIPv6Address(hostName)) {
                throw URISupport.createException(s, cursor, "Expected an IPv6 address");
            }
        } else {
            hostName = tokenizer.parseContent(s, cursor, URISupport.PORT_SEPARATORS);
        }
        String portText = null;
        if (!cursor.atEnd() && s.charAt(cursor.getPos()) == ':') {
            cursor.updatePos(cursor.getPos() + 1);
            portText = tokenizer.parseContent(s, cursor, URISupport.TERMINATORS);
        }
        final int port;
        if (!TextUtils.isBlank(portText)) {
            if (!ipv6Brackets && portText.contains(":")) {
                throw URISupport.createException(s, cursor, "Expected IPv6 address to be enclosed in brackets");
            }
            try {
                port = Integer.parseInt(portText);
            } catch (final NumberFormatException ex) {
                throw URISupport.createException(s, cursor, "Port is invalid");
            }
        } else {
            port = -1;
        }
        return new Host(hostName, port);
    }

    static Host parse(final CharSequence s) throws URISyntaxException {
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());
        return parse(s, cursor);
    }

    static void format(final StringBuilder buf, final NamedEndpoint endpoint) {
        final String hostName = endpoint.getHostName();
        if (InetAddressUtils.isIPv6Address(hostName)) {
            buf.append('[').append(hostName).append(']');
        } else {
            buf.append(hostName);
        }
        if (endpoint.getPort() != -1) {
            buf.append(":");
            buf.append(endpoint.getPort());
        }
    }

    static void format(final StringBuilder buf, final Host host) {
        format(buf, (NamedEndpoint) host);
    }

    static String format(final Host host) {
        final StringBuilder buf = new StringBuilder();
        format(buf, host);
        return buf.toString();
    }

    public static Host create(final String s) throws URISyntaxException {
        Args.notEmpty(s, "HTTP Host");
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, s.length());
        final Host host = parse(s, cursor);
        if (TextUtils.isBlank(host.getHostName())) {
            throw URISupport.createException(s, cursor, "Hostname is invalid");
        }
        if (!cursor.atEnd()) {
            throw URISupport.createException(s, cursor, "Unexpected content");
        }
        return host;
    }

    @Override
    public String getHostName() {
        return name;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof Host) {
            final Host that = (Host) o;
            return this.lcName.equals(that.lcName) && this.port == that.port;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.lcName);
        hash = LangUtils.hashCode(hash, this.port);
        return hash;
    }

    @Override
    public String toString() {
        return format(this);
    }

}
