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
package org.apache.hc.core5.http.signature;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.MessageHeaders;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.structured.StructuredFieldBareItem;
import org.apache.hc.core5.http.structured.StructuredFieldDictionary;
import org.apache.hc.core5.http.structured.StructuredFieldHeaders;
import org.apache.hc.core5.http.structured.StructuredFieldInnerList;
import org.apache.hc.core5.http.structured.StructuredFieldItem;
import org.apache.hc.core5.http.structured.StructuredFieldList;
import org.apache.hc.core5.http.structured.StructuredFieldMember;
import org.apache.hc.core5.http.structured.StructuredFieldSerializer;
import org.apache.hc.core5.http.structured.StructuredFieldType;
import org.apache.hc.core5.http.structured.StructuredFieldValue;
import org.apache.hc.core5.net.URIAuthority;

/**
 * RFC 9421 resolver for the standard derived components and field component parameters.
 *
 * @since 5.5
 */
public final class DefaultMessageSignatureComponentResolver implements MessageSignatureComponentResolver {

    /**
     * Singleton instance.
     *
     * @since 5.5
     */
    public static final DefaultMessageSignatureComponentResolver INSTANCE =
            new DefaultMessageSignatureComponentResolver();

    private static final Set<String> FIELD_PARAMETERS = new HashSet<>();

    static {
        FIELD_PARAMETERS.add("sf");
        FIELD_PARAMETERS.add("key");
        FIELD_PARAMETERS.add("bs");
        FIELD_PARAMETERS.add("req");
        FIELD_PARAMETERS.add("tr");
    }

    private DefaultMessageSignatureComponentResolver() {
    }

    @Override
    public String resolve(
            final MessageSignatureComponent component, final MessageSignatureContext context)
            throws MessageSignatureException {
        validateKnownParameters(component);
        if (component.isDerived()) {
            return resolveDerived(component, context);
        }
        return resolveField(component, context);
    }

    private static void validateKnownParameters(final MessageSignatureComponent component)
            throws MessageSignatureException {
        final Set<String> allowed = new HashSet<>();
        if (component.isDerived()) {
            allowed.add("req");
            if ("@query-param".equals(component.getName())) {
                allowed.add("name");
            }
        } else {
            allowed.addAll(FIELD_PARAMETERS);
        }
        for (final Map.Entry<String, StructuredFieldBareItem> entry : component.getParameters()) {
            if (!allowed.contains(entry.getKey())) {
                throw new MessageSignatureException("Unknown or inapplicable component parameter: "
                        + entry.getKey() + " on " + component.getName());
            }
        }
    }

    private static String resolveField(
            final MessageSignatureComponent component, final MessageSignatureContext context)
            throws MessageSignatureException {
        final boolean req = booleanParameter(component, "req");
        final boolean trailer = booleanParameter(component, "tr");
        final boolean sf = booleanParameter(component, "sf");
        final boolean bs = booleanParameter(component, "bs");
        final StructuredFieldBareItem keyParameter = component.getParameters().get("key");
        final String key = stringParameter(keyParameter, "key", false);

        if (bs && (sf || key != null)) {
            throw new MessageSignatureException("bs is incompatible with sf and key");
        }

        final MessageHeaders headers = selectHeaders(context, req, trailer);
        final String fieldName = component.getName();
        if (!headers.containsHeader(fieldName)) {
            throw new MessageSignatureException("Covered HTTP field is missing: " + fieldName);
        }

        try {
            if (bs) {
                return binaryWrapped(headers, fieldName);
            }
            if (key != null) {
                final StructuredFieldDictionary dictionary = StructuredFieldHeaders.parseDictionary(headers, fieldName);
                final StructuredFieldMember member = dictionary.get(key);
                if (member == null) {
                    throw new MessageSignatureException("Structured Field dictionary key is missing: " + key);
                }
                return serializeMember(member);
            }
            if (sf) {
                final StructuredFieldValueType type = context.getStructuredFieldType(fieldName);
                if (type == null) {
                    throw new MessageSignatureException("Structured Field type is unknown for: " + fieldName);
                }
                final StructuredFieldValue value;
                switch (type) {
                    case ITEM:
                        value = StructuredFieldHeaders.parseItem(headers, fieldName);
                        break;
                    case LIST:
                        value = StructuredFieldHeaders.parseList(headers, fieldName);
                        break;
                    case DICTIONARY:
                        value = StructuredFieldHeaders.parseDictionary(headers, fieldName);
                        break;
                    default:
                        throw new MessageSignatureException("Unsupported Structured Field type: " + type);
                }
                final String serialized = StructuredFieldSerializer.serialize(value);
                return serialized != null ? serialized : "";
            }
            return combineFieldValues(headers, fieldName);
        } catch (final ParseException ex) {
            throw new MessageSignatureException("Invalid covered HTTP field: " + fieldName, ex);
        }
    }

    private static MessageHeaders selectHeaders(
            final MessageSignatureContext context, final boolean req, final boolean trailer)
            throws MessageSignatureException {
        if (req) {
            if (context.getTarget() instanceof HttpRequest) {
                throw new MessageSignatureException("req MUST NOT be used when the target message is a request");
            }
            final HttpRequest relatedRequest = context.getRelatedRequest();
            if (relatedRequest == null) {
                throw new MessageSignatureException("req requires the related request");
            }
            if (trailer) {
                final MessageHeaders trailers = context.getRelatedRequestTrailers();
                if (trailers == null) {
                    throw new MessageSignatureException("Requested related-request trailers are unavailable");
                }
                return trailers;
            }
            return relatedRequest;
        }
        if (trailer) {
            final MessageHeaders trailers = context.getTrailers();
            if (trailers == null) {
                throw new MessageSignatureException("Requested trailers are unavailable");
            }
            return trailers;
        }
        return context.getTarget();
    }

    private static String combineFieldValues(final MessageHeaders headers, final String name) {
        final StringBuilder buffer = new StringBuilder();
        final Iterator<Header> iterator = headers.headerIterator(name);
        while (iterator.hasNext()) {
            if (buffer.length() > 0) {
                buffer.append(", ");
            }
            buffer.append(trimOws(unfold(iterator.next().getValue())));
        }
        return buffer.toString();
    }

    private static String binaryWrapped(final MessageHeaders headers, final String name)
            throws MessageSignatureException {
        final List<StructuredFieldMember> members = new ArrayList<>();
        final Iterator<Header> iterator = headers.headerIterator(name);
        while (iterator.hasNext()) {
            final String value = trimOws(unfold(iterator.next().getValue()));
            members.add(StructuredFieldItem.ofByteSequence(toFieldOctets(value)));
        }
        return StructuredFieldSerializer.serializeList(StructuredFieldList.of(members));
    }

    private static byte[] toFieldOctets(final String value) throws MessageSignatureException {
        final byte[] bytes = new byte[value.length()];
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (ch > 0xff) {
                throw new MessageSignatureException(
                        "HTTP field value cannot be represented as raw octets for ;bs");
            }
            bytes[i] = (byte) ch;
        }
        return bytes;
    }

    private static String serializeMember(final StructuredFieldMember member) {
        if (member instanceof StructuredFieldItem) {
            return StructuredFieldSerializer.serializeItem((StructuredFieldItem) member);
        }
        return StructuredFieldSerializer.serializeInnerList((StructuredFieldInnerList) member);
    }

    private static String resolveDerived(
            final MessageSignatureComponent component, final MessageSignatureContext context)
            throws MessageSignatureException {
        final boolean req = booleanParameter(component, "req");
        final HttpMessage target = context.getTarget();
        if (req && target instanceof HttpRequest) {
            throw new MessageSignatureException("req MUST NOT be used when the target message is a request");
        }
        final HttpMessage selected = req ? context.getRelatedRequest() : target;
        if (selected == null) {
            throw new MessageSignatureException("req requires the related request");
        }

        final String name = component.getName();
        if ("@status".equals(name)) {
            if (!(selected instanceof HttpResponse)) {
                throw new MessageSignatureException("@status requires a response target");
            }
            final int code = ((HttpResponse) selected).getCode();
            if (code < 100 || code > 599) {
                throw new MessageSignatureException("Invalid HTTP status code: " + code);
            }
            return Integer.toString(code);
        }

        if (!(selected instanceof HttpRequest)) {
            throw new MessageSignatureException(name + " requires a request target or ;req");
        }
        final HttpRequest request = (HttpRequest) selected;
        switch (name) {
            case "@method":
                return request.getMethod();
            case "@target-uri":
                return targetUri(request);
            case "@authority":
                return authority(request);
            case "@scheme":
                return scheme(request);
            case "@request-target":
                return requestTarget(request);
            case "@path":
                return path(request);
            case "@query":
                return query(request);
            case "@query-param":
                return queryParameter(request, component);
            default:
                throw new MessageSignatureException("Unknown derived component: " + name);
        }
    }

    private static String scheme(final HttpRequest request) throws MessageSignatureException {
        final String scheme = request.getScheme();
        if (scheme == null || scheme.isEmpty()) {
            throw new MessageSignatureException("Request scheme is unavailable");
        }
        return scheme.toLowerCase(Locale.ROOT);
    }

    private static String authority(final HttpRequest request) throws MessageSignatureException {
        final URIAuthority authority = request.getAuthority();
        if (authority == null) {
            throw new MessageSignatureException("Request authority is unavailable");
        }
        final String host = authority.getHostName().toLowerCase(Locale.ROOT);
        final int port = authority.getPort();
        final boolean defaultPort = port == 80 && "http".equalsIgnoreCase(request.getScheme())
                || port == 443 && "https".equalsIgnoreCase(request.getScheme());
        final String formattedHost = host.indexOf(':') >= 0 && !host.startsWith("[") ? "[" + host + "]" : host;
        return port >= 0 && !defaultPort ? formattedHost + ':' + port : formattedHost;
    }

    private static String targetUri(final HttpRequest request) throws MessageSignatureException {
        final String scheme = scheme(request);
        final String authority = authority(request);
        try {
            final URI uri = request.getUri();
            final String rawPath = uri.getRawPath();
            final String path = rawPath == null || rawPath.isEmpty() ? "/" : rawPath;
            final String rawQuery = uri.getRawQuery();
            return scheme + "://" + authority + path + (rawQuery != null ? "?" + rawQuery : "");
        } catch (final URISyntaxException ex) {
            throw new MessageSignatureException("Invalid request target URI", ex);
        }
    }

    private static String requestTarget(final HttpRequest request) throws MessageSignatureException {
        final String requestUri = request.getRequestUri();
        if (requestUri == null || requestUri.isEmpty()) {
            throw new MessageSignatureException("Request target is unavailable");
        }
        return requestUri;
    }

    private static URI uri(final HttpRequest request) throws MessageSignatureException {
        try {
            return request.getUri();
        } catch (final URISyntaxException ex) {
            throw new MessageSignatureException("Invalid request URI", ex);
        }
    }

    private static String path(final HttpRequest request) throws MessageSignatureException {
        final String rawPath = uri(request).getRawPath();
        return rawPath == null || rawPath.isEmpty() ? "/" : rawPath;
    }

    private static String query(final HttpRequest request) throws MessageSignatureException {
        final String rawQuery = uri(request).getRawQuery();
        return rawQuery != null ? "?" + rawQuery : "?";
    }

    private static String queryParameter(
            final HttpRequest request, final MessageSignatureComponent component)
            throws MessageSignatureException {
        final String encodedName = stringParameter(component.getParameters().get("name"), "name", true);
        final String rawQuery = uri(request).getRawQuery();
        if (rawQuery == null) {
            throw new MessageSignatureException("Named query parameter is missing: " + encodedName);
        }
        String found = null;
        int matches = 0;
        final String[] pairs = rawQuery.split("&", -1);
        for (final String pair : pairs) {
            // WHATWG application/x-www-form-urlencoded parsing skips empty byte sequences.
            if (pair.isEmpty()) {
                continue;
            }
            final int equals = pair.indexOf('=');
            final String rawName = equals >= 0 ? pair.substring(0, equals) : pair;
            final String rawValue = equals >= 0 ? pair.substring(equals + 1) : "";
            final String canonicalName = formEncode(formDecode(rawName));
            if (encodedName.equals(canonicalName)) {
                matches++;
                found = formEncode(formDecode(rawValue));
            }
        }
        if (matches == 0) {
            throw new MessageSignatureException("Named query parameter is missing: " + encodedName);
        }
        if (matches > 1) {
            throw new MessageSignatureException("Named query parameter occurs more than once: " + encodedName);
        }
        return found;
    }

    private static boolean booleanParameter(
            final MessageSignatureComponent component, final String name) throws MessageSignatureException {
        final StructuredFieldBareItem item = component.getParameters().get(name);
        if (item == null) {
            return false;
        }
        if (item.getType() != StructuredFieldType.BOOLEAN) {
            throw new MessageSignatureException("Component parameter '" + name + "' must be Boolean");
        }
        return item.getBooleanValue();
    }

    private static String stringParameter(
            final StructuredFieldBareItem item, final String name, final boolean required)
            throws MessageSignatureException {
        if (item == null) {
            if (required) {
                throw new MessageSignatureException("Missing required component parameter: " + name);
            }
            return null;
        }
        if (item.getType() != StructuredFieldType.STRING) {
            throw new MessageSignatureException("Component parameter '" + name + "' must be String");
        }
        return item.getTextValue();
    }

    private static String unfold(final String value) {
        if (value == null || value.indexOf('\r') < 0) {
            return value != null ? value : "";
        }
        return value.replaceAll("\\r\\n[ \\t]+", " ");
    }

    private static String trimOws(final String value) {
        int begin = 0;
        int end = value.length();
        while (begin < end && (value.charAt(begin) == ' ' || value.charAt(begin) == '\t')) {
            begin++;
        }
        while (end > begin && (value.charAt(end - 1) == ' ' || value.charAt(end - 1) == '\t')) {
            end--;
        }
        return value.substring(begin, end);
    }

    private static String formDecode(final String input) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream(input.length());
        for (int i = 0; i < input.length(); ) {
            final char ch = input.charAt(i);
            if (ch == '+') {
                bytes.write(' ');
                i++;
            } else if (ch == '%' && i + 2 < input.length()
                    && isHex(input.charAt(i + 1)) && isHex(input.charAt(i + 2))) {
                bytes.write((hex(input.charAt(i + 1)) << 4) | hex(input.charAt(i + 2)));
                i += 3;
            } else {
                final int codePoint = input.codePointAt(i);
                final byte[] encoded = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
                bytes.write(encoded, 0, encoded.length);
                i += Character.charCount(codePoint);
            }
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String formEncode(final String value) {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        final StringBuilder buffer = new StringBuilder(bytes.length);
        for (final byte b : bytes) {
            final int octet = b & 0xff;
            if (octet >= 'a' && octet <= 'z'
                    || octet >= 'A' && octet <= 'Z'
                    || octet >= '0' && octet <= '9'
                    || octet == '*' || octet == '-' || octet == '.' || octet == '_') {
                buffer.append((char) octet);
            } else {
                buffer.append('%');
                final char high = Character.toUpperCase(Character.forDigit((octet >>> 4) & 0xf, 16));
                final char low = Character.toUpperCase(Character.forDigit(octet & 0xf, 16));
                buffer.append(high).append(low);
            }
        }
        return buffer.toString();
    }

    private static boolean isHex(final char ch) {
        return ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'f' || ch >= 'A' && ch <= 'F';
    }

    private static int hex(final char ch) {
        if (ch >= '0' && ch <= '9') {
            return ch - '0';
        }
        if (ch >= 'a' && ch <= 'f') {
            return ch - 'a' + 10;
        }
        return ch - 'A' + 10;
    }

}
