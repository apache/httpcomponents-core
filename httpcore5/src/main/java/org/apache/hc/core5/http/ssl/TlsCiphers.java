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

package org.apache.hc.core5.http.ssl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * TLS cipher suite support methods
 *
 * @since 5.0
 */
public final class TlsCiphers {

    private final static Set<String> H2_BLACKLISTED =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    "TLS_NULL_WITH_NULL_NULL",
                    "TLS_RSA_WITH_NULL_MD5",
                    "TLS_RSA_WITH_NULL_SHA",
                    "TLS_RSA_EXPORT_WITH_RC4_40_MD5",
                    "TLS_RSA_WITH_RC4_128_MD5",
                    "TLS_RSA_WITH_RC4_128_SHA",
                    "TLS_RSA_EXPORT_WITH_RC2_CBC_40_MD5",
                    "TLS_RSA_WITH_IDEA_CBC_SHA",
                    "TLS_RSA_EXPORT_WITH_DES40_CBC_SHA",
                    "TLS_RSA_WITH_DES_CBC_SHA",
                    "TLS_RSA_WITH_3DES_EDE_CBC_SHA",
                    "TLS_DH_DSS_EXPORT_WITH_DES40_CBC_SHA",
                    "TLS_DH_DSS_WITH_DES_CBC_SHA",
                    "TLS_DH_DSS_WITH_3DES_EDE_CBC_SHA",
                    "TLS_DH_RSA_EXPORT_WITH_DES40_CBC_SHA",
                    "TLS_DH_RSA_WITH_DES_CBC_SHA",
                    "TLS_DH_RSA_WITH_3DES_EDE_CBC_SHA",
                    "TLS_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA",
                    "TLS_DHE_DSS_WITH_DES_CBC_SHA",
                    "TLS_DHE_DSS_WITH_3DES_EDE_CBC_SHA",
                    "TLS_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
                    "TLS_DHE_RSA_WITH_DES_CBC_SHA",
                    "TLS_DHE_RSA_WITH_3DES_EDE_CBC_SHA",
                    "TLS_DH_anon_EXPORT_WITH_RC4_40_MD5",
                    "TLS_DH_anon_WITH_RC4_128_MD5",
                    "TLS_DH_anon_EXPORT_WITH_DES40_CBC_SHA",
                    "TLS_DH_anon_WITH_DES_CBC_SHA",
                    "TLS_DH_anon_WITH_3DES_EDE_CBC_SHA",
                    "TLS_KRB5_WITH_DES_CBC_SHA",
                    "TLS_KRB5_WITH_3DES_EDE_CBC_SHA",
                    "TLS_KRB5_WITH_RC4_128_SHA",
                    "TLS_KRB5_WITH_IDEA_CBC_SHA",
                    "TLS_KRB5_WITH_DES_CBC_MD5",
                    "TLS_KRB5_WITH_3DES_EDE_CBC_MD5",
                    "TLS_KRB5_WITH_RC4_128_MD5",
                    "TLS_KRB5_WITH_IDEA_CBC_MD5",
                    "TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA",
                    "TLS_KRB5_EXPORT_WITH_RC2_CBC_40_SHA",
                    "TLS_KRB5_EXPORT_WITH_RC4_40_SHA",
                    "TLS_KRB5_EXPORT_WITH_DES_CBC_40_MD5",
                    "TLS_KRB5_EXPORT_WITH_RC2_CBC_40_MD5",
                    "TLS_KRB5_EXPORT_WITH_RC4_40_MD5",
                    "TLS_PSK_WITH_NULL_SHA",
                    "TLS_DHE_PSK_WITH_NULL_SHA",
                    "TLS_RSA_PSK_WITH_NULL_SHA",
                    "TLS_RSA_WITH_AES_128_CBC_SHA",
                    "TLS_DH_DSS_WITH_AES_128_CBC_SHA",
                    "TLS_DH_RSA_WITH_AES_128_CBC_SHA",
                    "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
                    "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
                    "TLS_DH_anon_WITH_AES_128_CBC_SHA",
                    "TLS_RSA_WITH_AES_256_CBC_SHA",
                    "TLS_DH_DSS_WITH_AES_256_CBC_SHA",
                    "TLS_DH_RSA_WITH_AES_256_CBC_SHA",
                    "TLS_DHE_DSS_WITH_AES_256_CBC_SHA",
                    "TLS_DHE_RSA_WITH_AES_256_CBC_SHA",
                    "TLS_DH_anon_WITH_AES_256_CBC_SHA",
                    "TLS_RSA_WITH_NULL_SHA256",
                    "TLS_RSA_WITH_AES_128_CBC_SHA256",
                    "TLS_RSA_WITH_AES_256_CBC_SHA256",
                    "TLS_DH_DSS_WITH_AES_128_CBC_SHA256",
                    "TLS_DH_RSA_WITH_AES_128_CBC_SHA256",
                    "TLS_DHE_DSS_WITH_AES_128_CBC_SHA256",
                    "TLS_RSA_WITH_CAMELLIA_128_CBC_SHA",
                    "TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA",
                    "TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA",
                    "TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA",
                    "TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA",
                    "TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA",
                    "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",
                    "TLS_DH_DSS_WITH_AES_256_CBC_SHA256",
                    "TLS_DH_RSA_WITH_AES_256_CBC_SHA256",
                    "TLS_DHE_DSS_WITH_AES_256_CBC_SHA256",
                    "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256",
                    "TLS_DH_anon_WITH_AES_128_CBC_SHA256",
                    "TLS_DH_anon_WITH_AES_256_CBC_SHA256",
                    "TLS_RSA_WITH_CAMELLIA_256_CBC_SHA",
                    "TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA",
                    "TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA",
                    "TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA",
                    "TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA",
                    "TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA",
                    "TLS_PSK_WITH_RC4_128_SHA",
                    "TLS_PSK_WITH_3DES_EDE_CBC_SHA",
                    "TLS_PSK_WITH_AES_128_CBC_SHA",
                    "TLS_PSK_WITH_AES_256_CBC_SHA",
                    "TLS_DHE_PSK_WITH_RC4_128_SHA",
                    "TLS_DHE_PSK_WITH_3DES_EDE_CBC_SHA",
                    "TLS_DHE_PSK_WITH_AES_128_CBC_SHA",
                    "TLS_DHE_PSK_WITH_AES_256_CBC_SHA",
                    "TLS_RSA_PSK_WITH_RC4_128_SHA",
                    "TLS_RSA_PSK_WITH_3DES_EDE_CBC_SHA",
                    "TLS_RSA_PSK_WITH_AES_128_CBC_SHA",
                    "TLS_RSA_PSK_WITH_AES_256_CBC_SHA",
                    "TLS_RSA_WITH_SEED_CBC_SHA",
                    "TLS_DH_DSS_WITH_SEED_CBC_SHA",
                    "TLS_DH_RSA_WITH_SEED_CBC_SHA",
                    "TLS_DHE_DSS_WITH_SEED_CBC_SHA",
                    "TLS_DHE_RSA_WITH_SEED_CBC_SHA",
                    "TLS_DH_anon_WITH_SEED_CBC_SHA",
                    "TLS_RSA_WITH_AES_128_GCM_SHA256",
                    "TLS_RSA_WITH_AES_256_GCM_SHA384",
                    "TLS_DH_RSA_WITH_AES_128_GCM_SHA256",
                    "TLS_DH_RSA_WITH_AES_256_GCM_SHA384",
                    "TLS_DH_DSS_WITH_AES_128_GCM_SHA256",
                    "TLS_DH_DSS_WITH_AES_256_GCM_SHA384",
                    "TLS_DH_anon_WITH_AES_128_GCM_SHA256",
                    "TLS_DH_anon_WITH_AES_256_GCM_SHA384",
                    "TLS_PSK_WITH_AES_128_GCM_SHA256",
                    "TLS_PSK_WITH_AES_256_GCM_SHA384",
                    "TLS_RSA_PSK_WITH_AES_128_GCM_SHA256",
                    "TLS_RSA_PSK_WITH_AES_256_GCM_SHA384",
                    "TLS_PSK_WITH_AES_128_CBC_SHA256",
                    "TLS_PSK_WITH_AES_256_CBC_SHA384",
                    "TLS_PSK_WITH_NULL_SHA256",
                    "TLS_PSK_WITH_NULL_SHA384",
                    "TLS_DHE_PSK_WITH_AES_128_CBC_SHA256",
                    "TLS_DHE_PSK_WITH_AES_256_CBC_SHA384",
                    "TLS_DHE_PSK_WITH_NULL_SHA256",
                    "TLS_DHE_PSK_WITH_NULL_SHA384",
                    "TLS_RSA_PSK_WITH_AES_128_CBC_SHA256",
                    "TLS_RSA_PSK_WITH_AES_256_CBC_SHA384",
                    "TLS_RSA_PSK_WITH_NULL_SHA256",
                    "TLS_RSA_PSK_WITH_NULL_SHA384",
                    "TLS_RSA_WITH_CAMELLIA_128_CBC_SHA256",
                    "TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA256",
                    "TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA256",
                    "TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA256",
                    "TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA256",
                    "TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA256",
                    "TLS_RSA_WITH_CAMELLIA_256_CBC_SHA256",
                    "TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA256",
                    "TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA256",
                    "TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA256",
                    "TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA256",
                    "TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA256",
                    "TLS_EMPTY_RENEGOTIATION_INFO_SCSV",
                    "TLS_ECDH_ECDSA_WITH_NULL_SHA",
                    "TLS_ECDH_ECDSA_WITH_RC4_128_SHA",
                    "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
                    "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA",
                    "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA",
                    "TLS_ECDHE_ECDSA_WITH_NULL_SHA",
                    "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
                    "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA",
                    "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
                    "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
                    "TLS_ECDH_RSA_WITH_NULL_SHA",
                    "TLS_ECDH_RSA_WITH_RC4_128_SHA",
                    "TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",
                    "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",
                    "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA",
                    "TLS_ECDHE_RSA_WITH_NULL_SHA",
                    "TLS_ECDHE_RSA_WITH_RC4_128_SHA",
                    "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",
                    "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
                    "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
                    "TLS_ECDH_anon_WITH_NULL_SHA",
                    "TLS_ECDH_anon_WITH_RC4_128_SHA",
                    "TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA",
                    "TLS_ECDH_anon_WITH_AES_128_CBC_SHA",
                    "TLS_ECDH_anon_WITH_AES_256_CBC_SHA",
                    "TLS_SRP_SHA_WITH_3DES_EDE_CBC_SHA",
                    "TLS_SRP_SHA_RSA_WITH_3DES_EDE_CBC_SHA",
                    "TLS_SRP_SHA_DSS_WITH_3DES_EDE_CBC_SHA",
                    "TLS_SRP_SHA_WITH_AES_128_CBC_SHA",
                    "TLS_SRP_SHA_RSA_WITH_AES_128_CBC_SHA",
                    "TLS_SRP_SHA_DSS_WITH_AES_128_CBC_SHA",
                    "TLS_SRP_SHA_WITH_AES_256_CBC_SHA",
                    "TLS_SRP_SHA_RSA_WITH_AES_256_CBC_SHA",
                    "TLS_SRP_SHA_DSS_WITH_AES_256_CBC_SHA",
                    "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
                    "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
                    "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256",
                    "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384",
                    "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
                    "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
                    "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256",
                    "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384",
                    "TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256",
                    "TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384",
                    "TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256",
                    "TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384",
                    "TLS_ECDHE_PSK_WITH_RC4_128_SHA",
                    "TLS_ECDHE_PSK_WITH_3DES_EDE_CBC_SHA",
                    "TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA",
                    "TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA",
                    "TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA256",
                    "TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA384",
                    "TLS_ECDHE_PSK_WITH_NULL_SHA",
                    "TLS_ECDHE_PSK_WITH_NULL_SHA256",
                    "TLS_ECDHE_PSK_WITH_NULL_SHA384",
                    "TLS_RSA_WITH_ARIA_128_CBC_SHA256",
                    "TLS_RSA_WITH_ARIA_256_CBC_SHA384",
                    "TLS_DH_DSS_WITH_ARIA_128_CBC_SHA256",
                    "TLS_DH_DSS_WITH_ARIA_256_CBC_SHA384",
                    "TLS_DH_RSA_WITH_ARIA_128_CBC_SHA256",
                    "TLS_DH_RSA_WITH_ARIA_256_CBC_SHA384",
                    "TLS_DHE_DSS_WITH_ARIA_128_CBC_SHA256",
                    "TLS_DHE_DSS_WITH_ARIA_256_CBC_SHA384",
                    "TLS_DHE_RSA_WITH_ARIA_128_CBC_SHA256",
                    "TLS_DHE_RSA_WITH_ARIA_256_CBC_SHA384",
                    "TLS_DH_anon_WITH_ARIA_128_CBC_SHA256",
                    "TLS_DH_anon_WITH_ARIA_256_CBC_SHA384",
                    "TLS_ECDHE_ECDSA_WITH_ARIA_128_CBC_SHA256",
                    "TLS_ECDHE_ECDSA_WITH_ARIA_256_CBC_SHA384",
                    "TLS_ECDH_ECDSA_WITH_ARIA_128_CBC_SHA256",
                    "TLS_ECDH_ECDSA_WITH_ARIA_256_CBC_SHA384",
                    "TLS_ECDHE_RSA_WITH_ARIA_128_CBC_SHA256",
                    "TLS_ECDHE_RSA_WITH_ARIA_256_CBC_SHA384",
                    "TLS_ECDH_RSA_WITH_ARIA_128_CBC_SHA256",
                    "TLS_ECDH_RSA_WITH_ARIA_256_CBC_SHA384",
                    "TLS_RSA_WITH_ARIA_128_GCM_SHA256",
                    "TLS_RSA_WITH_ARIA_256_GCM_SHA384",
                    "TLS_DH_RSA_WITH_ARIA_128_GCM_SHA256",
                    "TLS_DH_RSA_WITH_ARIA_256_GCM_SHA384",
                    "TLS_DH_DSS_WITH_ARIA_128_GCM_SHA256",
                    "TLS_DH_DSS_WITH_ARIA_256_GCM_SHA384",
                    "TLS_DH_anon_WITH_ARIA_128_GCM_SHA256",
                    "TLS_DH_anon_WITH_ARIA_256_GCM_SHA384",
                    "TLS_ECDH_ECDSA_WITH_ARIA_128_GCM_SHA256",
                    "TLS_ECDH_ECDSA_WITH_ARIA_256_GCM_SHA384",
                    "TLS_ECDH_RSA_WITH_ARIA_128_GCM_SHA256",
                    "TLS_ECDH_RSA_WITH_ARIA_256_GCM_SHA384",
                    "TLS_PSK_WITH_ARIA_128_CBC_SHA256",
                    "TLS_PSK_WITH_ARIA_256_CBC_SHA384",
                    "TLS_DHE_PSK_WITH_ARIA_128_CBC_SHA256",
                    "TLS_DHE_PSK_WITH_ARIA_256_CBC_SHA384",
                    "TLS_RSA_PSK_WITH_ARIA_128_CBC_SHA256",
                    "TLS_RSA_PSK_WITH_ARIA_256_CBC_SHA384",
                    "TLS_PSK_WITH_ARIA_128_GCM_SHA256",
                    "TLS_PSK_WITH_ARIA_256_GCM_SHA384",
                    "TLS_RSA_PSK_WITH_ARIA_128_GCM_SHA256",
                    "TLS_RSA_PSK_WITH_ARIA_256_GCM_SHA384",
                    "TLS_ECDHE_PSK_WITH_ARIA_128_CBC_SHA256",
                    "TLS_ECDHE_PSK_WITH_ARIA_256_CBC_SHA384",
                    "TLS_ECDHE_ECDSA_WITH_CAMELLIA_128_CBC_SHA256",
                    "TLS_ECDHE_ECDSA_WITH_CAMELLIA_256_CBC_SHA384",
                    "TLS_ECDH_ECDSA_WITH_CAMELLIA_128_CBC_SHA256",
                    "TLS_ECDH_ECDSA_WITH_CAMELLIA_256_CBC_SHA384",
                    "TLS_ECDHE_RSA_WITH_CAMELLIA_128_CBC_SHA256",
                    "TLS_ECDHE_RSA_WITH_CAMELLIA_256_CBC_SHA384",
                    "TLS_ECDH_RSA_WITH_CAMELLIA_128_CBC_SHA256",
                    "TLS_ECDH_RSA_WITH_CAMELLIA_256_CBC_SHA384",
                    "TLS_RSA_WITH_CAMELLIA_128_GCM_SHA256",
                    "TLS_RSA_WITH_CAMELLIA_256_GCM_SHA384",
                    "TLS_DH_RSA_WITH_CAMELLIA_128_GCM_SHA256",
                    "TLS_DH_RSA_WITH_CAMELLIA_256_GCM_SHA384",
                    "TLS_DH_DSS_WITH_CAMELLIA_128_GCM_SHA256",
                    "TLS_DH_DSS_WITH_CAMELLIA_256_GCM_SHA384",
                    "TLS_DH_anon_WITH_CAMELLIA_128_GCM_SHA256",
                    "TLS_DH_anon_WITH_CAMELLIA_256_GCM_SHA384",
                    "TLS_ECDH_ECDSA_WITH_CAMELLIA_128_GCM_SHA256",
                    "TLS_ECDH_ECDSA_WITH_CAMELLIA_256_GCM_SHA384",
                    "TLS_ECDH_RSA_WITH_CAMELLIA_128_GCM_SHA256",
                    "TLS_ECDH_RSA_WITH_CAMELLIA_256_GCM_SHA384",
                    "TLS_PSK_WITH_CAMELLIA_128_GCM_SHA256",
                    "TLS_PSK_WITH_CAMELLIA_256_GCM_SHA384",
                    "TLS_RSA_PSK_WITH_CAMELLIA_128_GCM_SHA256",
                    "TLS_RSA_PSK_WITH_CAMELLIA_256_GCM_SHA384",
                    "TLS_PSK_WITH_CAMELLIA_128_CBC_SHA256",
                    "TLS_PSK_WITH_CAMELLIA_256_CBC_SHA384",
                    "TLS_DHE_PSK_WITH_CAMELLIA_128_CBC_SHA256",
                    "TLS_DHE_PSK_WITH_CAMELLIA_256_CBC_SHA384",
                    "TLS_RSA_PSK_WITH_CAMELLIA_128_CBC_SHA256",
                    "TLS_RSA_PSK_WITH_CAMELLIA_256_CBC_SHA384",
                    "TLS_ECDHE_PSK_WITH_CAMELLIA_128_CBC_SHA256",
                    "TLS_ECDHE_PSK_WITH_CAMELLIA_256_CBC_SHA384",
                    "TLS_RSA_WITH_AES_128_CCM",
                    "TLS_RSA_WITH_AES_256_CCM",
                    "TLS_RSA_WITH_AES_128_CCM_8",
                    "TLS_RSA_WITH_AES_256_CCM_8",
                    "TLS_PSK_WITH_AES_128_CCM",
                    "TLS_PSK_WITH_AES_256_CCM",
                    "TLS_PSK_WITH_AES_128_CCM_8",
                    "TLS_PSK_WITH_AES_256_CCM_8"
            )));

    public static boolean isH2Blacklisted(final String cipherSuite) {
        return H2_BLACKLISTED.contains(cipherSuite);
    }

    private static final String WEAK_KEY_EXCHANGES
            = "^(TLS|SSL)_(NULL|ECDH_anon|DH_anon|DH_anon_EXPORT|DHE_RSA_EXPORT|DHE_DSS_EXPORT|"
            + "DSS_EXPORT|DH_DSS_EXPORT|DH_RSA_EXPORT|RSA_EXPORT|KRB5_EXPORT)_(.*)";
    private static final String WEAK_CIPHERS
            = "^(TLS|SSL)_(.*)_WITH_(NULL|DES_CBC|DES40_CBC|DES_CBC_40|3DES_EDE_CBC|RC4_128|RC4_40|RC2_CBC_40)_(.*)";

    private static final List<Pattern> WEAK_CIPHER_SUITE_PATTERNS = Collections.unmodifiableList(Arrays.asList(
            Pattern.compile(WEAK_KEY_EXCHANGES, Pattern.CASE_INSENSITIVE),
            Pattern.compile(WEAK_CIPHERS, Pattern.CASE_INSENSITIVE)));

    public static boolean isWeak(final String cipherSuite) {
        for (final Pattern pattern : WEAK_CIPHER_SUITE_PATTERNS) {
            if (pattern.matcher(cipherSuite).matches()) {
                return true;
            }
        }
        return false;
    }

    public static String[] excludeH2Blacklisted(final String... ciphers) {
        if (ciphers == null) {
            return null;
        }
        final List<String> enabledCiphers = new ArrayList<>();
        for (final String cipher: ciphers) {
            if (!TlsCiphers.isH2Blacklisted(cipher)) {
                enabledCiphers.add(cipher);
            }
        }
        return !enabledCiphers.isEmpty() ? enabledCiphers.toArray(new String[0]) : ciphers;
    }

    public static String[] excludeWeak(final String... ciphers) {
        if (ciphers == null) {
            return null;
        }
        final List<String> enabledCiphers = new ArrayList<>();
        for (final String cipher: ciphers) {
            if (!TlsCiphers.isWeak(cipher)) {
                enabledCiphers.add(cipher);
            }
        }
        return !enabledCiphers.isEmpty() ? enabledCiphers.toArray(new String[0]) : ciphers;
    }

}
