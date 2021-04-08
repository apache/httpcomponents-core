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

package org.apache.hc.core5.http;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.net.Host;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.LangUtils;
import org.apache.hc.core5.util.TextUtils;

/**
 * Component that holds all details needed to describe an HTTP connection
 * to a host. This includes remote host name, port and protocol scheme.
 *
 * @see org.apache.hc.core5.net.Host
 *
 * @since 4.0
 * @since 5.0 For constructors that take a scheme as an argument, that argument is now the first one.
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class HttpHost implements NamedEndpoint, Serializable {

    private static final long serialVersionUID = -7529410654042457626L;

    /** The default scheme is "http". */
    public static final URIScheme DEFAULT_SCHEME = URIScheme.HTTP;

    private final String schemeName;
    private final Host host;
    private final InetAddress address;

    /**
     * Creates a new {@link HttpHost HttpHost}, specifying all values.
     * Constructor for HttpHost.
     * @param scheme    the name of the scheme.
     *                  {@code null} indicates the
     *                  {@link #DEFAULT_SCHEME default scheme}
     * @param address   the inet address. Can be {@code null}
     * @param hostname   the hostname (IP or DNS name)
     * @param port      the port number.
     *                  {@code -1} indicates the scheme default port.
     *
     * @throws IllegalArgumentException
     *             If the port parameter is outside the specified range of valid port values, which is between 0 and
     *             65535, inclusive. {@code -1} indicates the scheme default port.
     *
     * @since 5.0
     */
    public HttpHost(final String scheme, final InetAddress address, final String hostname, final int port) {
        Args.containsNoBlanks(hostname, "Host name");
        this.host = new Host(hostname, port);
        this.schemeName = scheme != null ? scheme.toLowerCase(Locale.ROOT) : DEFAULT_SCHEME.id;
        this.address = address;
    }

    /**
     * Creates {@code HttpHost} instance with the given scheme, hostname and port.
     * @param scheme    the name of the scheme.
     *                  {@code null} indicates the
     *                  {@link #DEFAULT_SCHEME default scheme}
     * @param hostname  the hostname (IP or DNS name)
     * @param port      the port number.
     *                  {@code -1} indicates the scheme default port.
     *
     * @throws IllegalArgumentException
     *             If the port parameter is outside the specified range of valid port values, which is between 0 and
     *             65535, inclusive. {@code -1} indicates the scheme default port.
     */
    public HttpHost(final String scheme, final String hostname, final int port) {
        this(scheme, null, hostname, port);
    }

    /**
     * Creates {@code HttpHost} instance with the default scheme and the given hostname and port.
     *
     * @param hostname  the hostname (IP or DNS name)
     * @param port      the port number.
     *                  {@code -1} indicates the scheme default port.
     *
     * @throws IllegalArgumentException
     *             If the port parameter is outside the specified range of valid port values, which is between 0 and
     *             65535, inclusive. {@code -1} indicates the scheme default port.
     */
    public HttpHost(final String hostname, final int port) {
        this(null, hostname, port);
    }

    /**
     * Creates {@code HttpHost} instance with the given hostname and scheme and the default port for that scheme.
     * @param scheme    the name of the scheme.
     *                  {@code null} indicates the
     *                  {@link #DEFAULT_SCHEME default scheme}
     * @param hostname  the hostname (IP or DNS name)
     *
     * @throws IllegalArgumentException
     *             If the port parameter is outside the specified range of valid port values, which is between 0 and
     *             65535, inclusive. {@code -1} indicates the scheme default port.
     */
    public HttpHost(final String scheme, final String hostname) {
        this(scheme, hostname, -1);
    }

    /**
     * Creates {@code HttpHost} instance from a string. Text may not contain any blanks.
     *
     * @since 4.4
     */
    public static HttpHost create(final String s) throws URISyntaxException {
        Args.notEmpty(s, "HTTP Host");
        String text = s;
        String scheme = null;
        final int schemeIdx = text.indexOf("://");
        if (schemeIdx > 0) {
            scheme = text.substring(0, schemeIdx);
            if (TextUtils.containsBlanks(scheme)) {
                throw new URISyntaxException(s, "scheme contains blanks");
            }
            text = text.substring(schemeIdx + 3);
        }
        final Host host = Host.create(text);
        return new HttpHost(scheme, host);
    }

    /**
     * Creates an {@code HttpHost} instance from the scheme, host, and port from the given URI. Other URI elements are ignored.
     *
     * @param uri scheme, host, and port.
     * @return a new HttpHost
     *
     * @since 5.0
     */
    public static HttpHost create(final URI uri) {
        final String scheme = uri.getScheme();
        return new HttpHost(scheme != null ? scheme : URIScheme.HTTP.getId(), uri.getHost(), uri.getPort());
    }

    /**
     * Creates {@code HttpHost} instance with the default scheme and port and the given hostname.
     *
     * @param hostname  the hostname (IP or DNS name)
     *
     * @throws IllegalArgumentException
     *             If the port parameter is outside the specified range of valid port values, which is between 0 and
     *             65535, inclusive. {@code -1} indicates the scheme default port.
     */
    public HttpHost(final String hostname) {
        this(null, hostname, -1);
    }

    /**
     * Creates {@code HttpHost} instance with the given scheme, inet address and port.
     * @param scheme    the name of the scheme.
     *                  {@code null} indicates the
     *                  {@link #DEFAULT_SCHEME default scheme}
     * @param address   the inet address.
     * @param port      the port number.
     *                  {@code -1} indicates the scheme default port.
     *
     * @throws IllegalArgumentException
     *             If the port parameter is outside the specified range of valid port values, which is between 0 and
     *             65535, inclusive. {@code -1} indicates the scheme default port.
     *
     * @since 5.0
     */
    public HttpHost(final String scheme, final InetAddress address, final int port) {
        this(scheme, Args.notNull(address,"Inet address"), address.getHostName(), port);
    }

    /**
     * Creates {@code HttpHost} instance with the default scheme and the given inet address
     * and port.
     *
     * @param address   the inet address.
     * @param port      the port number.
     *                  {@code -1} indicates the scheme default port.
     *
     * @throws IllegalArgumentException
     *             If the port parameter is outside the specified range of valid port values, which is between 0 and
     *             65535, inclusive. {@code -1} indicates the scheme default port.
     *
     * @since 4.3
     */
    public HttpHost(final InetAddress address, final int port) {
        this(null, address, port);
    }

    /**
     * Creates {@code HttpHost} instance with the default scheme and port and the given inet
     * address.
     *
     * @param address   the inet address.
     *
     * @throws IllegalArgumentException
     *             If the port parameter is outside the specified range of valid port values, which is between 0 and
     *             65535, inclusive. {@code -1} indicates the scheme default port.
     *
     * @since 4.3
     */
    public HttpHost(final InetAddress address) {
        this(null, address, -1);
    }

    /**
     * @throws IllegalArgumentException
     *             If the port parameter is outside the specified range of valid port values, which is between 0 and
     *             65535, inclusive. {@code -1} indicates the scheme default port.
     *
     * @since 5.0
     */
    public HttpHost(final String scheme, final NamedEndpoint namedEndpoint) {
        this(scheme, Args.notNull(namedEndpoint, "Named endpoint").getHostName(), namedEndpoint.getPort());
    }

    /**
     * @throws IllegalArgumentException
     *             If the port parameter is outside the specified range of valid port values, which is between 0 and
     *             65535, inclusive. {@code -1} indicates the scheme default port.
     *
     * @since 5.0
     */
    public HttpHost(final URIAuthority authority) {
        this(null, authority);
    }

    /**
     * Returns the host name.
     *
     * @return the host name (IP or DNS name)
     */
    @Override
    public String getHostName() {
        return this.host.getHostName();
    }

    /**
     * Returns the port.
     *
     * @return the host port, or {@code -1} if not set
     */
    @Override
    public int getPort() {
        return this.host.getPort();
    }

    /**
     * Returns the scheme name.
     *
     * @return the scheme name
     */
    public String getSchemeName() {
        return this.schemeName;
    }

    /**
     * Returns the inet address if explicitly set by a constructor,
     *   {@code null} otherwise.
     * @return the inet address
     *
     * @since 4.3
     */
    public InetAddress getAddress() {
        return this.address;
    }

    /**
     * Return the host URI, as a string.
     *
     * @return the host URI
     */
    public String toURI() {
        final StringBuilder buffer = new StringBuilder();
        buffer.append(this.schemeName);
        buffer.append("://");
        buffer.append(this.host.toString());
        return buffer.toString();
    }


    /**
     * Obtains the host string, without scheme prefix.
     *
     * @return  the host string, for example {@code localhost:8080}
     */
    public String toHostString() {
        return this.host.toString();
    }


    @Override
    public String toString() {
        return toURI();
    }


    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof HttpHost) {
            final HttpHost that = (HttpHost) obj;
            return this.schemeName.equals(that.schemeName) &&
                    this.host.equals(that.host) &&
                    LangUtils.equals(this.address, that.address);
        }
        return false;
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.schemeName);
        hash = LangUtils.hashCode(hash, this.host);
        hash = LangUtils.hashCode(hash, this.address);
        return hash;
    }

}
