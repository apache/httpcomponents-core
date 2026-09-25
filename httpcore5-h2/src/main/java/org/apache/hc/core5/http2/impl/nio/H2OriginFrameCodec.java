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
package org.apache.hc.core5.http2.impl.nio;

import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TextUtils;
import org.apache.hc.core5.util.Tokenizer;

/** ORIGIN frame wire codec and ASCII origin normalization. */
final class H2OriginFrameCodec {

    private static final Tokenizer.Delimiter SCHEME_DELIMITER = Tokenizer.delimiters(':');
    private static final Tokenizer.Delimiter AUTHORITY_DELIMITER = Tokenizer.delimiters('/', '?', '#');

    private H2OriginFrameCodec() {
    }

    static HttpHost parse(final CharSequence text) throws URISyntaxException {
        Args.notNull(text, "Origin");
        if (text.length() == 0) {
            throw new URISyntaxException(text.toString(), "Origin is empty");
        }
        for (int i = 0; i < text.length(); i++) {
            final char ch = text.charAt(i);
            if (ch <= 0x20 || ch >= 0x7f) {
                throw new URISyntaxException(text.toString(), "Origin is not visible US-ASCII", i);
            }
        }

        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, text.length());
        final String scheme = Tokenizer.INSTANCE.parseContent(text, cursor, SCHEME_DELIMITER);
        if (!isSchemeValid(scheme) || cursor.atEnd() || text.charAt(cursor.getPos()) != ':') {
            throw new URISyntaxException(text.toString(), "Invalid scheme", cursor.getPos());
        }
        final int authorityStart = cursor.getPos() + 3;
        if (authorityStart > text.length()
                || text.charAt(cursor.getPos() + 1) != '/'
                || text.charAt(cursor.getPos() + 2) != '/') {
            throw new URISyntaxException(text.toString(), "Expected hierarchical origin", cursor.getPos());
        }
        cursor.updatePos(authorityStart);
        final String authorityText = Tokenizer.INSTANCE.parseContent(text, cursor, AUTHORITY_DELIMITER);
        if (!cursor.atEnd()) {
            throw new URISyntaxException(text.toString(), "Path, query, and fragment are not allowed", cursor.getPos());
        }
        if (authorityText.isEmpty()) {
            throw new URISyntaxException(text.toString(), "Authority is empty", authorityStart);
        }
        validatePortDigits(authorityText, text.toString(), authorityStart);

        final URIAuthority authority = URIAuthority.create(authorityText);
        if (authority == null
                || TextUtils.isBlank(authority.getHostName())
                || authority.getHostName().indexOf('*') >= 0
                || authority.getHostName().indexOf('%') >= 0
                || authority.getUserInfo() != null) {
            throw new URISyntaxException(text.toString(), "Invalid origin authority", authorityStart);
        }
        final int port = resolvePort(scheme, authority.getPort());
        if (port < 0) {
            throw new URISyntaxException(text.toString(), "An explicit port is required for this scheme", authorityStart);
        }
        return new HttpHost(scheme, authority.getHostName().toLowerCase(Locale.ROOT), port);
    }

    static HttpHost normalize(final HttpHost origin) {
        Args.notNull(origin, "Origin");
        final String scheme = origin.getSchemeName();
        Args.check(isSchemeValid(scheme), "Invalid origin scheme: %s", scheme);
        Args.notBlank(origin.getHostName(), "Origin host");
        Args.check(origin.getHostName().indexOf('*') < 0, "Wildcard origins are not supported");
        Args.check(origin.getHostName().indexOf('%') < 0, "Scoped IP literals are not supported in origins");
        final int port = resolvePort(scheme, origin.getPort());
        Args.check(port >= 0, "An explicit port is required for scheme '%s'", scheme);
        return new HttpHost(scheme, origin.getHostName().toLowerCase(Locale.ROOT), port);
    }

    static List<HttpHost> normalize(final Collection<HttpHost> origins) {
        Args.notNull(origins, "Origins");
        final List<HttpHost> result = new ArrayList<>(origins.size());
        for (final HttpHost origin : origins) {
            result.add(normalize(origin));
        }
        return Collections.unmodifiableList(result);
    }

    static HttpHost fromRequest(final HttpRequest request) throws ProtocolException {
        final String scheme = request.getScheme();
        final URIAuthority authority = request.getAuthority();
        if (TextUtils.isBlank(scheme) || authority == null) {
            return null;
        }
        if (authority.getUserInfo() != null) {
            throw new ProtocolException("Request authority contains user info");
        }
        final int port = resolvePort(scheme, authority.getPort());
        if (port < 0) {
            throw new ProtocolException("Request origin has no explicit or default port");
        }
        return new HttpHost(scheme, authority.getHostName(), port);
    }

    static Set<HttpHost> decode(final ByteBuffer payload) {
        if (payload == null) {
            return Collections.emptySet();
        }
        final ByteBuffer src = payload.duplicate();
        final Set<HttpHost> origins = new LinkedHashSet<>();
        while (src.remaining() >= 2) {
            final int length = src.getShort() & 0xffff;
            if (length > src.remaining()) {
                break;
            }
            final byte[] bytes = new byte[length];
            src.get(bytes);
            boolean ascii = true;
            for (final byte b : bytes) {
                if ((b & 0x80) != 0) {
                    ascii = false;
                    break;
                }
            }
            if (ascii) {
                try {
                    origins.add(parse(new String(bytes, StandardCharsets.US_ASCII)));
                } catch (final URISyntaxException | IllegalArgumentException ignore) {
                    // Invalid ASCII-Origin entries are ignored.
                }
            }
        }
        return origins;
    }

    static List<ByteBuffer> encode(final Collection<HttpHost> origins, final int maxFrameSize) {
        Args.notNull(origins, "Origins");
        Args.positive(maxFrameSize, "Maximum frame size");
        final LinkedHashSet<String> values = new LinkedHashSet<>();
        for (final HttpHost origin : origins) {
            values.add(format(origin));
        }
        if (values.isEmpty()) {
            return Collections.singletonList(ByteBuffer.allocate(0));
        }

        final List<ByteBuffer> payloads = new ArrayList<>();
        ByteBuffer payload = ByteBuffer.allocate(maxFrameSize);
        for (final String value : values) {
            final byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
            final int entryLength = 2 + bytes.length;
            Args.check(bytes.length <= 0xffff, "Origin is too long");
            Args.check(entryLength <= maxFrameSize, "Origin does not fit into an HTTP/2 frame");
            if (payload.remaining() < entryLength) {
                payload.flip();
                payloads.add(payload);
                payload = ByteBuffer.allocate(maxFrameSize);
            }
            payload.putShort((short) bytes.length);
            payload.put(bytes);
        }
        payload.flip();
        payloads.add(payload);
        return payloads;
    }

    static String format(final HttpHost origin) {
        final HttpHost normalized = normalize(origin);
        final int port = normalized.getPort();
        final int defaultPort = defaultPort(normalized.getSchemeName());
        final HttpHost serialized = new HttpHost(
                normalized.getSchemeName(),
                normalized.getHostName(),
                port == defaultPort ? -1 : port);
        final String value = serialized.toURI();
        try {
            parse(value);
        } catch (final URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid origin: " + value, ex);
        }
        return value;
    }

    private static boolean isSchemeValid(final String scheme) {
        if (TextUtils.isBlank(scheme)
                || scheme.charAt(0) >= 0x80
                || !Character.isLetter(scheme.charAt(0))) {
            return false;
        }
        for (int i = 1; i < scheme.length(); i++) {
            final char ch = scheme.charAt(i);
            if (ch >= 0x80
                    || !Character.isLetter(ch) && !Character.isDigit(ch) && ch != '+' && ch != '-' && ch != '.') {
                return false;
            }
        }
        return true;
    }

    private static void validatePortDigits(
            final String authority, final String input, final int authorityStart) throws URISyntaxException {
        final int colon;
        if (authority.charAt(0) == '[') {
            final int bracket = authority.indexOf(']');
            if (bracket < 0) {
                throw new URISyntaxException(input, "Invalid IPv6 authority", authorityStart);
            }
            colon = bracket + 1 < authority.length() ? bracket + 1 : -1;
            if (colon >= 0 && authority.charAt(colon) != ':') {
                throw new URISyntaxException(input, "Invalid IPv6 authority", authorityStart + colon);
            }
        } else {
            colon = authority.lastIndexOf(':');
        }
        if (colon >= 0) {
            if (colon + 1 >= authority.length()) {
                throw new URISyntaxException(input, "Port is empty", authorityStart + colon + 1);
            }
            for (int i = colon + 1; i < authority.length(); i++) {
                if (!Character.isDigit(authority.charAt(i))) {
                    throw new URISyntaxException(input, "Port is invalid", authorityStart + i);
                }
            }
        }
    }

    private static int resolvePort(final String scheme, final int port) {
        return port >= 0 ? port : defaultPort(scheme);
    }

    private static int defaultPort(final String scheme) {
        final String normalized = scheme.toLowerCase(Locale.ROOT);
        if ("http".equals(normalized)) {
            return 80;
        }
        if ("https".equals(normalized)) {
            return 443;
        }
        return -1;
    }
}
