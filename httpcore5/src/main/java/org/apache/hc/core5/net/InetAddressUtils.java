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
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

import org.apache.hc.core5.util.Args;

/**
 * A collection of utilities relating to InetAddresses.
 *
 * @since 4.0
 */
public class InetAddressUtils {

    /**
     * Represents the ipv4
     *
     * @since 5.1
     */
    public static final byte IPV4 = 1;
    /**
     * Represents the ipv6.
     *
     * @since 5.1
     */
    public static final byte IPV6 = 4;

    private InetAddressUtils() {
    }

    private static final String IPV4_BASIC_PATTERN_STRING =
            "(([1-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){1}" + // initial first field, 1-255
            "(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){2}" + // following 2 fields, 0-255 followed by .
             "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])"; // final field, 0-255

    private static final Pattern IPV4_PATTERN =
        Pattern.compile("^" + IPV4_BASIC_PATTERN_STRING + "$");

    private static final Pattern IPV4_MAPPED_IPV6_PATTERN = // TODO does not allow for redundant leading zeros
            Pattern.compile("^::[fF]{4}:" + IPV4_BASIC_PATTERN_STRING + "$");

    private static final Pattern IPV6_STD_PATTERN =
        Pattern.compile(
                "^[0-9a-fA-F]{1,4}(:[0-9a-fA-F]{1,4}){7}$");

    private static final Pattern IPV6_HEX_COMPRESSED_PATTERN =
        Pattern.compile(
                "^(([0-9A-Fa-f]{1,4}(:[0-9A-Fa-f]{1,4}){0,5})?)" + // 0-6 hex fields
                 "::" +
                 "(([0-9A-Fa-f]{1,4}(:[0-9A-Fa-f]{1,4}){0,5})?)$"); // 0-6 hex fields

    /**
     * Regular expression pattern to match the scope ID in an IPv6 scoped address.
     * The scope ID should be a non-empty string consisting of only alphanumeric characters or "-".
     */
    private static final Pattern SCOPE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-]+$");

    /**
     * Delimiter used to separate an IPv6 address from its scope ID.
     */
    private static final char SCOPE_ID_DELIMITER = '%';



    /*
     *  The above pattern is not totally rigorous as it allows for more than 7 hex fields in total
     */
    private static final char COLON_CHAR = ':';

    // Must not have more than 7 colons (i.e. 8 fields)
    private static final int MAX_COLON_COUNT = 7;

    /**
     * Tests whether the parameter is a valid IPv4 address
     *
     * @param input the address character sequence to check for validity
     * @return true if the input parameter is a valid IPv4 address
     * @deprecated Use {@link #isIPv4(CharSequence)}
     */
    @Deprecated
    public static boolean isIPv4Address(final String input) {
        return isIPv4(input);
    }

    /**
     * Tests whether the parameter is a valid IPv4 address.
     *
     * @param input the address character sequence to check for validity
     * @return true if the input parameter is a valid IPv4 address
     * @since 5.3
     */
    public static boolean isIPv4(final CharSequence input) {
        return IPV4_PATTERN.matcher(input).matches();
    }

    /**
     * Tests if an IPv6 address is an IPv4-mapped IPv6 address.
     *
     * @param  input the IPv6 address to be checked
     * @return true if the IPv6 address is an IPv4-mapped IPv6 address, false otherwise.
     * @deprecated Use {@link #isIPv4MappedIPv6(CharSequence)}
     */
    @Deprecated
    public static boolean isIPv4MappedIPv64Address(final String input) {
        return isIPv4MappedIPv6(input);
    }

    /**
     * Tests if an IPv6 address is an IPv4-mapped IPv6 address.
     *
     * @param  input the IPv6 address to be checked
     * @return true if the IPv6 address is an IPv4-mapped IPv6 address, false otherwise.
     * @since 5.3
     */
    public static boolean isIPv4MappedIPv6(final CharSequence input) {
        return IPV4_MAPPED_IPV6_PATTERN.matcher(input).matches();
    }

    static boolean hasValidIPv6ColonCount(final CharSequence input) {
        int colonCount = 0;
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == COLON_CHAR) {
                colonCount++;
            }
        }
        // IPv6 address must have at least 2 colons and not more than 7 (i.e. 8 fields)
        return colonCount >= 2 && colonCount <= MAX_COLON_COUNT;
    }

    /**
     * Tests whether the parameter is a valid standard (non-compressed) IPv6 address
     *
     * @param input the address character sequence to check for validity
     * @return true if the input parameter is a valid standard (non-compressed) IPv6 address
     * @deprecated Use {@link #isIPv6Std(CharSequence)}
     */
    @Deprecated
    public static boolean isIPv6StdAddress(final String input) {
        return isIPv6Std(input);
    }

    /**
     * Tests whether the parameter is a valid standard (non-compressed) IPv6 address
     *
     * @param input the address character sequence to check for validity
     * @return true if the input parameter is a valid standard (non-compressed) IPv6 address
     * @since 5.3
     */
    public static boolean isIPv6Std(final CharSequence input) {
        return hasValidIPv6ColonCount(input) && IPV6_STD_PATTERN.matcher(input).matches();
    }

    /**
     * Tests whether the parameter is a valid compressed IPv6 address
     *
     * @param input the address character sequence to check for validity
     * @return true if the input parameter is a valid compressed IPv6 address
     * @deprecated Use {@link #isIPv6HexCompressed(CharSequence)}
     */
    @Deprecated
    public static boolean isIPv6HexCompressedAddress(final String input) {
        return isIPv6HexCompressed(input);
    }

    /**
     * Tests whether the parameter is a valid compressed IPv6 address
     *
     * @param input the address character sequence to check for validity
     * @return true if the input parameter is a valid compressed IPv6 address
     * @since 5.3
     */
    public static boolean isIPv6HexCompressed(final CharSequence input) {
        return hasValidIPv6ColonCount(input) && IPV6_HEX_COMPRESSED_PATTERN.matcher(input).matches();
    }

    /**
     * Tests whether the parameter is a valid IPv6 address (including compressed).
     *
     * @param input the address character sequence to check for validity
     * @return true if the input parameter is a valid standard or compressed IPv6 address
     * @deprecated Use {@link #isIPv6(CharSequence)}
     */
    @Deprecated
    public static boolean isIPv6Address(final String input) {
        return isIPv6(input);
    }

    /**
     * Tests whether the parameter is a valid IPv6 address (including compressed).
     *
     * @param input the address character sequence to check for validity
     * @return true if the input parameter is a valid standard or compressed IPv6 address
     * @since 5.3
     */
    public static boolean isIPv6(final CharSequence input) {
        int index = -1;
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == SCOPE_ID_DELIMITER) {
                index = i;
                break;
            }
        }
        if (index == -1) {
            return isIPv6Std(input) || isIPv6HexCompressed(input);
        }
        final CharSequence address = input.subSequence(0, index);
        if (isIPv6Std(address) || isIPv6HexCompressed(address)) {
            // Check if the scope ID is valid
            final CharSequence scopeId = input.subSequence(index + 1, input.length());
            // Scope ID should be a non-empty character sequence consisting of only alphanumeric characters or "-"
            return scopeId.length() != 0 && SCOPE_ID_PATTERN.matcher(scopeId).matches();
        }
        return false;
    }

    /**
     * Tests whether the parameter is a valid URL formatted bracketed IPv6 address (including compressed).
     * This matches only bracketed values e.g. {@code [::1]}.
     *
     * @param input the address character sequence to check for validity
     * @return true if the input parameter is a valid URL-formatted bracketed IPv6 address
     * @deprecated Use {@link #isIPv6URLBracketed(CharSequence)}
     */
    @Deprecated
    public static boolean isIPv6URLBracketedAddress(final String input) {
        return isIPv6URLBracketed(input);
    }

    /**
     * Tests whether the parameter is a valid URL formatted bracketed IPv6 address (including compressed).
     * This matches only bracketed values e.g. {@code [::1]}.
     *
     * @param input the address character sequence to check for validity
     * @return true if the input parameter is a valid URL-formatted bracketed IPv6 address
     * @since 5.3
     */
    public static boolean isIPv6URLBracketed(final CharSequence input) {
        if (input.length() == 0) {
            return false;
        }
        return input.charAt(0) == '['
                && input.charAt(input.length() - 1) == ']'
                && isIPv6(input.subSequence(1, input.length() - 1));
    }

    /**
     * Formats {@link SocketAddress} as text.
     *
     * @param buffer The target buffer to append.
     * @param socketAddress The SocketAddress to append to {@code buffer}.
     * @since 5.0
     */
    public static void formatAddress(
            final StringBuilder buffer,
            final SocketAddress socketAddress) {
        Args.notNull(buffer, "buffer");
        if (socketAddress instanceof InetSocketAddress) {
            final InetSocketAddress socketaddr = (InetSocketAddress) socketAddress;
            final InetAddress inetaddr = socketaddr.getAddress();
            if (inetaddr != null) {
                buffer.append(inetaddr.getHostAddress()).append(':').append(socketaddr.getPort());
            } else {
                buffer.append(socketAddress);
            }
        } else {
            buffer.append(socketAddress);
        }
    }

    /**
     * Gets the canonical name of the local host, a fully qualified domain name.
     * <p>
     * This can be {@code "localhost"} or a fully qualified domain name like {@code "host.docker.internal"}.
     * </p>
     *
     * @return the canonical name of the local host.
     * @since 5.0
     */
    public static String getCanonicalLocalHostName() {
        try {
            return InetAddress.getLocalHost().getCanonicalHostName();
        } catch (final UnknownHostException ex) {
            return "localhost";
        }
    }

}
