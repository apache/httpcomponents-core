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
import java.net.URISyntaxException;
import java.util.Locale;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.LangUtils;
import org.apache.hc.core5.util.TextUtils;

/**
 * Holds all of the variables needed to describe an HTTP connection to a host.
 * This includes remote host name, port and scheme.
 *
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class HttpHost implements NamedEndpoint, Serializable {

    private static final long serialVersionUID = -7529410654042457626L;

    /** The default scheme is "http". */
    public static final String DEFAULT_SCHEME_NAME = "http";

    /** The host to use. */
    private final String hostname;

    /** The lowercase host, for {@link #equals} and {@link #hashCode}. */
    private final String lcHostname;

    /** The port to use, defaults to -1 if not set. */
    private final int port;

    /** The scheme (lowercased) */
    private final String schemeName;

    private final InetAddress address;

    private HttpHost(final String hostname, final int port, final String scheme, final boolean internal) {
        super();
        this.hostname = hostname;
        this.lcHostname = hostname;
        this.schemeName = scheme;
        this.port = port;
        this.address = null;
    }

    /**
     * Creates {@code HttpHost} instance with the given scheme, hostname and port.
     *
     * @param hostname  the hostname (IP or DNS name)
     * @param port      the port number.
     *                  {@code -1} indicates the scheme default port.
     * @param scheme    the name of the scheme.
     *                  {@code null} indicates the
     *                  {@link #DEFAULT_SCHEME_NAME default scheme}
     */
    public HttpHost(final String hostname, final int port, final String scheme) {
        super();
        this.hostname   = Args.containsNoBlanks(hostname, "Host name");
        this.lcHostname = hostname.toLowerCase(Locale.ROOT);
        if (scheme != null) {
            this.schemeName = scheme.toLowerCase(Locale.ROOT);
        } else {
            this.schemeName = DEFAULT_SCHEME_NAME;
        }
        this.port = port;
        this.address = null;
    }

    /**
     * Creates {@code HttpHost} instance with the default scheme and the given hostname and port.
     *
     * @param hostname  the hostname (IP or DNS name)
     * @param port      the port number.
     *                  {@code -1} indicates the scheme default port.
     */
    public HttpHost(final String hostname, final int port) {
        this(hostname, port, null);
    }

    /**
     * Creates {@code HttpHost} instance with the given hostname and scheme and the default port for that scheme.
     *
     * @param hostname  the hostname (IP or DNS name)
     * @param scheme    the name of the scheme.
     *                  {@code null} indicates the
     *                  {@link #DEFAULT_SCHEME_NAME default scheme}
     */
    public HttpHost(final String hostname, final String scheme) {
        this(hostname, -1, scheme);
    }

    /**
     * Creates {@code HttpHost} instance from string. Text may not contain any blanks.
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
        int port = -1;
        final int portIdx = text.lastIndexOf(":");
        if (portIdx > 0) {
            try {
                port = Integer.parseInt(text.substring(portIdx + 1));
            } catch (final NumberFormatException ex) {
                throw new URISyntaxException(s, "invalid port");
            }
            text = text.substring(0, portIdx);
        }
        if (TextUtils.containsBlanks(text)) {
            throw new URISyntaxException(s, "hostname contains blanks");
        }
        return new HttpHost(
                text.toLowerCase(Locale.ROOT),
                port,
                scheme != null ? scheme.toLowerCase(Locale.ROOT) : DEFAULT_SCHEME_NAME, true);
    }

    /**
     * Creates {@code HttpHost} instance with the default scheme and port and the given hostname.
     *
     * @param hostname  the hostname (IP or DNS name)
     */
    public HttpHost(final String hostname) {
        this(hostname, -1, null);
    }

    /**
     * Creates {@code HttpHost} instance with the given scheme, inet address and port.
     *
     * @param address   the inet address.
     * @param port      the port number.
     *                  {@code -1} indicates the scheme default port.
     * @param scheme    the name of the scheme.
     *                  {@code null} indicates the
     *                  {@link #DEFAULT_SCHEME_NAME default scheme}
     *
     * @since 4.3
     */
    public HttpHost(final InetAddress address, final int port, final String scheme) {
        this(Args.notNull(address,"Inet address"), address.getHostName(), port, scheme);
    }
    /**
     * Creates a new {@link HttpHost HttpHost}, specifying all values.
     * Constructor for HttpHost.
     *
     * @param address   the inet address.
     * @param hostname   the hostname (IP or DNS name)
     * @param port      the port number.
     *                  {@code -1} indicates the scheme default port.
     * @param scheme    the name of the scheme.
     *                  {@code null} indicates the
     *                  {@link #DEFAULT_SCHEME_NAME default scheme}
     *
     * @since 4.4
     */
    public HttpHost(final InetAddress address, final String hostname, final int port, final String scheme) {
        super();
        this.address = Args.notNull(address, "Inet address");
        this.hostname = Args.notNull(hostname, "Hostname");
        this.lcHostname = this.hostname.toLowerCase(Locale.ROOT);
        if (scheme != null) {
            this.schemeName = scheme.toLowerCase(Locale.ROOT);
        } else {
            this.schemeName = DEFAULT_SCHEME_NAME;
        }
        this.port = port;
    }

    /**
     * Creates {@code HttpHost} instance with the default scheme and the given inet address
     * and port.
     *
     * @param address   the inet address.
     * @param port      the port number.
     *                  {@code -1} indicates the scheme default port.
     *
     * @since 4.3
     */
    public HttpHost(final InetAddress address, final int port) {
        this(address, port, null);
    }

    /**
     * Creates {@code HttpHost} instance with the default scheme and port and the given inet
     * address.
     *
     * @param address   the inet address.
     *
     * @since 4.3
     */
    public HttpHost(final InetAddress address) {
        this(address, -1, null);
    }

    /**
     * @since 5.0
     */
    public HttpHost(final NamedEndpoint namedEndpoint, final String scheme) {
        this(namedEndpoint.getHostName(), namedEndpoint.getPort(), scheme);
    }

    /**
     * @since 5.0
     */
    public HttpHost(final URIAuthority authority) {
        this(authority, null);
    }

    /**
     * Returns the host name.
     *
     * @return the host name (IP or DNS name)
     */
    @Override
    public String getHostName() {
        return this.hostname;
    }

    /**
     * Returns the port.
     *
     * @return the host port, or {@code -1} if not set
     */
    @Override
    public int getPort() {
        return this.port;
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
        buffer.append(this.hostname);
        if (this.port != -1) {
            buffer.append(':');
            buffer.append(Integer.toString(this.port));
        }
        return buffer.toString();
    }


    /**
     * Obtains the host string, without scheme prefix.
     *
     * @return  the host string, for example {@code localhost:8080}
     */
    public String toHostString() {
        if (this.port != -1) {
            //the highest port number is 65535, which is length 6 with the addition of the colon
            final StringBuilder buffer = new StringBuilder(this.hostname.length() + 6);
            buffer.append(this.hostname);
            buffer.append(":");
            buffer.append(Integer.toString(this.port));
            return buffer.toString();
        }
        return this.hostname;
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
            return this.lcHostname.equals(that.lcHostname)
                && this.port == that.port
                && this.schemeName.equals(that.schemeName)
                && LangUtils.equals(this.address, that.address);
        }
        return false;
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.lcHostname);
        hash = LangUtils.hashCode(hash, this.port);
        hash = LangUtils.hashCode(hash, this.schemeName);
        hash = LangUtils.hashCode(hash, address);
        return hash;
    }

}
