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

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.TextUtils;

/**
 * Builder for {@link URI} instances.
 *
 * @since 5.0
 */
public class URIBuilder {

    /**
     * Creates a new builder for the host {@link InetAddress#getLocalHost()}.
     *
     * @return a new builder.
     * @throws UnknownHostException if the local host name could not be resolved into an address.
     */
    public static URIBuilder localhost() throws UnknownHostException {
        return new URIBuilder().setHost(InetAddress.getLocalHost());
    }

    /**
     * Creates a new builder for the host {@link InetAddress#getLoopbackAddress()}.
     */
    public static URIBuilder loopbackAddress() {
        return new URIBuilder().setHost(InetAddress.getLoopbackAddress());
    }

    private String scheme;
    private String encodedSchemeSpecificPart;
    private String encodedAuthority;
    private String userInfo;
    private String encodedUserInfo;
    private String host;
    private int port;
    private String encodedPath;
    private List<String> pathSegments;
    private String encodedQuery;
    private List<NameValuePair> queryParams;
    private String query;
    private Charset charset;
    private String fragment;
    private String encodedFragment;

    /**
     * Constructs an empty instance.
     */
    public URIBuilder() {
        super();
        this.port = -1;
    }

    /**
     * Construct an instance from the string which must be a valid URI.
     *
     * @param string a valid URI in string form
     * @throws URISyntaxException if the input is not a valid URI
     */
    public URIBuilder(final String string) throws URISyntaxException {
        this(new URI(string), null);
    }

    /**
     * Construct an instance from the provided URI.
     * @param uri
     */
    public URIBuilder(final URI uri) {
        this(uri, null);
    }

    /**
     * Construct an instance from the string which must be a valid URI.
     *
     * @param string a valid URI in string form
     * @throws URISyntaxException if the input is not a valid URI
     */
    public URIBuilder(final String string, final Charset charset) throws URISyntaxException {
        this(new URI(string), charset);
    }

    /**
     * Construct an instance from the provided URI.
     * @param uri
     */
    public URIBuilder(final URI uri, final Charset charset) {
        super();
        setCharset(charset);
        digestURI(uri);
    }

    public URIBuilder setCharset(final Charset charset) {
        this.charset = charset;
        return this;
    }

    public Charset getCharset() {
        return charset;
    }

    private List <NameValuePair> parseQuery(final String query, final Charset charset) {
        if (query != null && !query.isEmpty()) {
            return URLEncodedUtils.parse(query, charset);
        }
        return null;
    }

    private List <String> parsePath(final String path, final Charset charset) {
        if (path != null && !path.isEmpty()) {
            return URLEncodedUtils.parsePathSegments(path, charset);
        }
        return null;
    }

    /**
     * Builds a {@link URI} instance.
     */
    public URI build() throws URISyntaxException {
        return new URI(buildString());
    }

    private String buildString() {
        final StringBuilder sb = new StringBuilder();
        if (this.scheme != null) {
            sb.append(this.scheme).append(':');
        }
        if (this.encodedSchemeSpecificPart != null) {
            sb.append(this.encodedSchemeSpecificPart);
        } else {
            if (this.encodedAuthority != null) {
                sb.append("//").append(this.encodedAuthority);
            } else if (this.host != null) {
                sb.append("//");
                if (this.encodedUserInfo != null) {
                    sb.append(this.encodedUserInfo).append("@");
                } else if (this.userInfo != null) {
                    encodeUserInfo(sb, this.userInfo);
                    sb.append("@");
                }
                if (InetAddressUtils.isIPv6Address(this.host)) {
                    sb.append("[").append(this.host).append("]");
                } else {
                    sb.append(this.host);
                }
                if (this.port >= 0) {
                    sb.append(":").append(this.port);
                }
            }
            if (this.encodedPath != null) {
                sb.append(normalizePath(this.encodedPath, sb.length() == 0));
            } else if (this.pathSegments != null) {
                encodePath(sb, this.pathSegments);
            }
            if (this.encodedQuery != null) {
                sb.append("?").append(this.encodedQuery);
            } else if (this.queryParams != null && !this.queryParams.isEmpty()) {
                sb.append("?");
                encodeUrlForm(sb, this.queryParams);
            } else if (this.query != null) {
                sb.append("?");
                encodeUric(sb, this.query);
            }
        }
        if (this.encodedFragment != null) {
            sb.append("#").append(this.encodedFragment);
        } else if (this.fragment != null) {
            sb.append("#");
            encodeUric(sb, this.fragment);
        }
        return sb.toString();
    }

    private static String normalizePath(final String path, final boolean relative) {
        String s = path;
        if (TextUtils.isBlank(s)) {
            return "";
        }
        if (!relative && !s.startsWith("/")) {
            s = "/" + s;
        }
        return s;
    }

    private void digestURI(final URI uri) {
        this.scheme = uri.getScheme();
        this.encodedSchemeSpecificPart = uri.getRawSchemeSpecificPart();
        this.encodedAuthority = uri.getRawAuthority();
        this.host = uri.getHost();
        this.port = uri.getPort();
        this.encodedUserInfo = uri.getRawUserInfo();
        this.userInfo = uri.getUserInfo();
        this.encodedPath = uri.getRawPath();
        this.pathSegments = parsePath(uri.getRawPath(), this.charset != null ? this.charset : StandardCharsets.UTF_8);
        this.encodedQuery = uri.getRawQuery();
        this.queryParams = parseQuery(uri.getRawQuery(), this.charset != null ? this.charset : StandardCharsets.UTF_8);
        this.encodedFragment = uri.getRawFragment();
        this.fragment = uri.getFragment();
    }

    private void encodeUserInfo(final StringBuilder buf, final String userInfo) {
        URLEncodedUtils.encUserInfo(buf, userInfo, this.charset != null ? this.charset : StandardCharsets.UTF_8);
    }

    private void encodePath(final StringBuilder buf, final List<String> pathSegments) {
        URLEncodedUtils.formatSegments(buf, pathSegments, this.charset != null ? this.charset : StandardCharsets.UTF_8);
    }

    private void encodeUrlForm(final StringBuilder buf, final List<NameValuePair> params) {
        URLEncodedUtils.formatParameters(buf, params, this.charset != null ? this.charset : StandardCharsets.UTF_8);
    }

    private void encodeUric(final StringBuilder buf, final String fragment) {
        URLEncodedUtils.encUric(buf, fragment, this.charset != null ? this.charset : StandardCharsets.UTF_8);
    }

    /**
     * Sets URI scheme.
     *
     * @return this.
     */
    public URIBuilder setScheme(final String scheme) {
        this.scheme = !TextUtils.isBlank(scheme) ? scheme : null;
        return this;
    }

    /**
     * Sets the URI scheme specific part.
     *
     * @param schemeSpecificPart
     * @return this.
     * @since 5.1
     */
    public URIBuilder setSchemeSpecificPart(final String schemeSpecificPart) {
        this.encodedSchemeSpecificPart = schemeSpecificPart;
        return this;
    }

    /**
     * Sets the URI scheme specific part and append a variable arguments list of NameValuePair instance(s) to this part.
     *
     * @param schemeSpecificPart
     * @param nvps Optional, can be null. Variable arguments list of NameValuePair query parameters to be reused by the specific scheme part
     * @return this.
     * @since 5.1
     */
    public URIBuilder setSchemeSpecificPart(final String schemeSpecificPart, final NameValuePair... nvps) {
        return setSchemeSpecificPart(schemeSpecificPart, nvps != null ? Arrays.asList(nvps) : null);
    }

    /**
     * Sets the URI scheme specific part and append a list of NameValuePair to this part.
     *
     * @param schemeSpecificPart
     * @param nvps Optional, can be null. List of query parameters to be reused by the specific scheme part
     * @return this.
     * @since 5.1
     */
    public URIBuilder setSchemeSpecificPart(final String schemeSpecificPart, final List <NameValuePair> nvps) {
        this.encodedSchemeSpecificPart = null;
        if (!TextUtils.isBlank(schemeSpecificPart)) {
            final StringBuilder sb = new StringBuilder(schemeSpecificPart);
            if (nvps != null && !nvps.isEmpty()) {
                sb.append("?");
                encodeUrlForm(sb, nvps);
            }
            this.encodedSchemeSpecificPart = sb.toString();
        }
        return this;
    }

    /**
     * Sets URI user info. The value is expected to be unescaped and may contain non ASCII
     * characters.
     *
     * @return this.
     */
    public URIBuilder setUserInfo(final String userInfo) {
        this.userInfo = !TextUtils.isBlank(userInfo) ? userInfo : null;
        this.encodedSchemeSpecificPart = null;
        this.encodedAuthority = null;
        this.encodedUserInfo = null;
        return this;
    }

    /**
     * Sets URI user info as a combination of username and password. These values are expected to
     * be unescaped and may contain non ASCII characters.
     *
     * @return this.
     */
    public URIBuilder setUserInfo(final String username, final String password) {
        return setUserInfo(username + ':' + password);
    }

    /**
     * Sets URI host.
     *
     * @return this.
     */
    public URIBuilder setHost(final InetAddress host) {
        this.host = host != null ? host.getHostAddress() : null;
        this.encodedSchemeSpecificPart = null;
        this.encodedAuthority = null;
        return this;
    }

    /**
     * Sets URI host.
     *
     * @return this.
     */
    public URIBuilder setHost(final String host) {
        this.host = !TextUtils.isBlank(host) ? host : null;
        this.encodedSchemeSpecificPart = null;
        this.encodedAuthority = null;
        return this;
    }

    /**
     * Sets the scheme, host name, and port.
     *
     * @param httpHost the scheme, host name, and port.
     * @return this.
     */
    public URIBuilder setHttpHost(final HttpHost httpHost ) {
        setScheme(httpHost.getSchemeName());
        setHost(httpHost.getHostName());
        setPort(httpHost.getPort());
        return this;
    }

    /**
     * Sets URI port.
     *
     * @return this.
     */
    public URIBuilder setPort(final int port) {
        this.port = port < 0 ? -1 : port;
        this.encodedSchemeSpecificPart = null;
        this.encodedAuthority = null;
        return this;
    }

    /**
     * Sets URI path. The value is expected to be unescaped and may contain non ASCII characters.
     *
     * @return this.
     */
    public URIBuilder setPath(final String path) {
        return setPathSegments(path != null ? URLEncodedUtils.splitPathSegments(path) : null);
    }

    /**
     * Sets URI path. The value is expected to be unescaped and may contain non ASCII characters.
     *
     * @return this.
     */
    public URIBuilder setPathSegments(final String... pathSegments) {
        this.pathSegments = pathSegments.length > 0 ? Arrays.asList(pathSegments) : null;
        this.encodedSchemeSpecificPart = null;
        this.encodedPath = null;
        return this;
    }

    /**
     * Sets URI path. The value is expected to be unescaped and may contain non ASCII characters.
     *
     * @return this.
     */
    public URIBuilder setPathSegments(final List<String> pathSegments) {
        this.pathSegments = pathSegments != null && pathSegments.size() > 0 ? new ArrayList<>(pathSegments) : null;
        this.encodedSchemeSpecificPart = null;
        this.encodedPath = null;
        return this;
    }

    /**
     * Removes URI query.
     *
     * @return this.
     */
    public URIBuilder removeQuery() {
        this.queryParams = null;
        this.query = null;
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        return this;
    }

    /**
     * Sets URI query parameters. The parameter name / values are expected to be unescaped
     * and may contain non ASCII characters.
     * <p>
     * Please note query parameters and custom query component are mutually exclusive. This method
     * will remove custom query if present.
     * </p>
     *
     * @return this.
     */
    public URIBuilder setParameters(final List <NameValuePair> nvps) {
        if (this.queryParams == null) {
            this.queryParams = new ArrayList<>();
        } else {
            this.queryParams.clear();
        }
        this.queryParams.addAll(nvps);
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        this.query = null;
        return this;
    }

    /**
     * Adds URI query parameters. The parameter name / values are expected to be unescaped
     * and may contain non ASCII characters.
     * <p>
     * Please note query parameters and custom query component are mutually exclusive. This method
     * will remove custom query if present.
     * </p>
     *
     * @return this.
     */
    public URIBuilder addParameters(final List <NameValuePair> nvps) {
        if (this.queryParams == null) {
            this.queryParams = new ArrayList<>();
        }
        this.queryParams.addAll(nvps);
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        this.query = null;
        return this;
    }

    /**
     * Sets URI query parameters. The parameter name / values are expected to be unescaped
     * and may contain non ASCII characters.
     * <p>
     * Please note query parameters and custom query component are mutually exclusive. This method
     * will remove custom query if present.
     * </p>
     *
     * @return this.
     */
    public URIBuilder setParameters(final NameValuePair... nvps) {
        if (this.queryParams == null) {
            this.queryParams = new ArrayList<>();
        } else {
            this.queryParams.clear();
        }
        for (final NameValuePair nvp: nvps) {
            this.queryParams.add(nvp);
        }
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        this.query = null;
        return this;
    }

    /**
     * Adds parameter to URI query. The parameter name and value are expected to be unescaped
     * and may contain non ASCII characters.
     * <p>
     * Please note query parameters and custom query component are mutually exclusive. This method
     * will remove custom query if present.
     * </p>
     *
     * @return this.
     */
    public URIBuilder addParameter(final String param, final String value) {
        if (this.queryParams == null) {
            this.queryParams = new ArrayList<>();
        }
        this.queryParams.add(new BasicNameValuePair(param, value));
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        this.query = null;
        return this;
    }

    /**
     * Sets parameter of URI query overriding existing value if set. The parameter name and value
     * are expected to be unescaped and may contain non ASCII characters.
     * <p>
     * Please note query parameters and custom query component are mutually exclusive. This method
     * will remove custom query if present.
     * </p>
     *
     * @return this.
     */
    public URIBuilder setParameter(final String param, final String value) {
        if (this.queryParams == null) {
            this.queryParams = new ArrayList<>();
        }
        if (!this.queryParams.isEmpty()) {
            for (final Iterator<NameValuePair> it = this.queryParams.iterator(); it.hasNext(); ) {
                final NameValuePair nvp = it.next();
                if (nvp.getName().equals(param)) {
                    it.remove();
                }
            }
        }
        this.queryParams.add(new BasicNameValuePair(param, value));
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        this.query = null;
        return this;
    }

    /**
     * Clears URI query parameters.
     *
     * @return this.
     */
    public URIBuilder clearParameters() {
        this.queryParams = null;
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        return this;
    }

    /**
     * Sets custom URI query. The value is expected to be unescaped and may contain non ASCII
     * characters.
     * <p>
     * Please note query parameters and custom query component are mutually exclusive. This method
     * will remove query parameters if present.
     * </p>
     *
     * @return this.
     */
    public URIBuilder setCustomQuery(final String query) {
        this.query = !TextUtils.isBlank(query) ? query : null;
        this.encodedQuery = null;
        this.encodedSchemeSpecificPart = null;
        this.queryParams = null;
        return this;
    }

    /**
     * Sets URI fragment. The value is expected to be unescaped and may contain non ASCII
     * characters.
     *
     * @return this.
     */
    public URIBuilder setFragment(final String fragment) {
        this.fragment = !TextUtils.isBlank(fragment) ? fragment : null;
        this.encodedFragment = null;
        return this;
    }

    public boolean isAbsolute() {
        return this.scheme != null;
    }

    public boolean isOpaque() {
        return this.pathSegments == null && this.encodedPath == null;
    }

    public String getScheme() {
        return this.scheme;
    }

    /**
     * Gets the scheme specific part
     *
     * @return String
     * @since 5.1
     */
    public String getSchemeSpecificPart() {
        return this.encodedSchemeSpecificPart;
    }

    public String getUserInfo() {
        return this.userInfo;
    }

    public String getHost() {
        return this.host;
    }

    public int getPort() {
        return this.port;
    }

    public boolean isPathEmpty() {
        return (this.pathSegments == null || this.pathSegments.isEmpty()) &&
                (this.encodedPath == null || this.encodedPath.isEmpty());
    }

    public List<String> getPathSegments() {
        return this.pathSegments != null ? new ArrayList<>(this.pathSegments) : Collections.<String>emptyList();
    }

    public String getPath() {
        if (this.pathSegments == null) {
            return null;
        }
        final StringBuilder result = new StringBuilder();
        for (final String segment : this.pathSegments) {
            result.append('/').append(segment);
        }
        return result.toString();
    }

    public boolean isQueryEmpty() {
        return (this.queryParams == null || this.queryParams.isEmpty()) && this.encodedQuery == null;
    }

    public List<NameValuePair> getQueryParams() {
        return this.queryParams != null ? new ArrayList<>(this.queryParams) : Collections.<NameValuePair>emptyList();
    }

    public String getFragment() {
        return this.fragment;
    }

    @Override
    public String toString() {
        return buildString();
    }

}
