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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link TlsCiphers}.
 */
class TestTlsCiphers {

    static String[] testExcludeH2Blacklisted() {
        final String[] mixCipherSuites = {
               "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
               "TLS_RSA_WITH_AES_256_CBC_SHA256",
               "AES_SHA_US",
               "TLS_RSA_WITH_AES_128_CBC_SHA",
               "NULL_SHA",
               "TLS_RSA_WITH_AES_256_GCM_SHA384"
        };
        return TlsCiphers.excludeH2Blacklisted(mixCipherSuites);
    }

    static String[] testExcludeWeak() {
        final String[] weakCiphersSuites = {
                "SSL_RSA_WITH_RC4_128_SHA",
                "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
                "TLS_DH_anon_WITH_AES_128_CBC_SHA",
                "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
                "SSL_RSA_WITH_NULL_SHA",
                "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
                "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
                "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
                "TLS_DH_anon_WITH_AES_256_GCM_SHA384",
                "TLS_ECDH_anon_WITH_AES_256_CBC_SHA",
                "TLS_RSA_WITH_NULL_SHA256",
                "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
                "SSL_DH_anon_EXPORT_WITH_RC4_40_MD5",
                "TLS_KRB5_EXPORT_WITH_RC4_40_SHA",
                "SSL_RSA_EXPORT_WITH_RC2_CBC_40_MD5",
                "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
                "TLS_RSA_WITH_AES_256_CBC_SHA256",
                "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256",
                "TLS_RSA_WITH_AES_128_CBC_SHA",
                "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
                "TLS_RSA_WITH_AES_256_GCM_SHA384"
        };
        return TlsCiphers.excludeWeak(weakCiphersSuites);
    }

    @Test
    void testCipherTLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384() {
        final String cipherSuite = "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384";
        Assertions.assertTrue(TlsCiphers.isH2Blacklisted(cipherSuite));
        Assertions.assertTrue(TlsCiphers.isWeak(cipherSuite));
    }

    @ParameterizedTest
    @MethodSource
    void testExcludeH2Blacklisted(final String strongCipherSuite) {
        Assertions.assertFalse(TlsCiphers.isH2Blacklisted(strongCipherSuite), strongCipherSuite);
        Assertions.assertFalse(TlsCiphers.isWeak(strongCipherSuite), strongCipherSuite);
   }

    @ParameterizedTest
    @MethodSource
    void testExcludeWeak(final String strongCipherSuite) {
        Assertions.assertTrue(TlsCiphers.isWeak(strongCipherSuite), strongCipherSuite);
    }

    @Test
    void testExcludeWeakNull() {
        Assertions.assertNull(TlsCiphers.excludeWeak((String[]) null));
    }

    /**
     * RFC9113 "prohibits" the use of certain cipher suites in HTTP/2.
     *
     * RFC10015 "deprecates" some cipher suites, and discourages the use of others.
     *
     * So we have 3 classifications and our own "weak" classification.
     *
     * For leave test disabled.
     */
    @ParameterizedTest
    @MethodSource("org.apache.hc.core5.http.ssl.TlsCiphers#getH2Blacklisted()")
    void testH2BlacklistedIsWeak(final String h2BlacklistedCipherSuite) {
        // Sanity assert
        Assertions.assertTrue(TlsCiphers.isH2Blacklisted(h2BlacklistedCipherSuite), h2BlacklistedCipherSuite);
        // Test
        Assertions.assertTrue(TlsCiphers.isWeak(h2BlacklistedCipherSuite), h2BlacklistedCipherSuite);
    }

    /**
     * Tests DH cipher suites deprecated by <a href="https://www.rfc-editor.org/rfc/rfc10015.html">RFC10015</a> Section 5.1.
     *
     * @param deprecatedCipherSuite DH cipher suites deprecated by RFC10015 Section 5.1.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "TLS_DH_DSS_EXPORT_WITH_DES40_CBC_SHA",     // RFC4346
            "TLS_DH_DSS_WITH_DES_CBC_SHA",              // RFC8996
            "TLS_DH_DSS_WITH_3DES_EDE_CBC_SHA",         // RFC5246
            "TLS_DH_RSA_EXPORT_WITH_DES40_CBC_SHA",     // RFC4346
            "TLS_DH_RSA_WITH_DES_CBC_SHA",              // RFC8996
            "TLS_DH_RSA_WITH_3DES_EDE_CBC_SHA",         // RFC5246
            "TLS_DH_anon_EXPORT_WITH_RC4_40_MD5",       // RFC4346, RFC6347
            "TLS_DH_anon_WITH_RC4_128_MD5",             // RFC5246, RFC6347
            "TLS_DH_anon_EXPORT_WITH_DES40_CBC_SHA",    // RFC4346
            "TLS_DH_anon_WITH_DES_CBC_SHA",             // RFC8996
            "TLS_DH_anon_WITH_3DES_EDE_CBC_SHA",        // RFC5246
            "TLS_DH_DSS_WITH_AES_128_CBC_SHA",          // RFC5246
            "TLS_DH_RSA_WITH_AES_128_CBC_SHA",          // RFC5246
            "TLS_DH_anon_WITH_AES_128_CBC_SHA",         // RFC5246
            "TLS_DH_DSS_WITH_AES_256_CBC_SHA",          // RFC5246
            "TLS_DH_RSA_WITH_AES_256_CBC_SHA",          // RFC5246
            "TLS_DH_anon_WITH_AES_256_CBC_SHA",         // RFC5246
            "TLS_DH_DSS_WITH_AES_128_CBC_SHA256",       // RFC5246
            "TLS_DH_RSA_WITH_AES_128_CBC_SHA256",       // RFC5246
            "TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA",     // RFC5932
            "TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA",     // RFC5932
            "TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA",    // RFC5932
            "TLS_DH_DSS_WITH_AES_256_CBC_SHA256",       // RFC5246
            "TLS_DH_RSA_WITH_AES_256_CBC_SHA256",       // RFC5246
            "TLS_DH_anon_WITH_AES_128_CBC_SHA256",      // RFC5246
            "TLS_DH_anon_WITH_AES_256_CBC_SHA256",      // RFC5246
            "TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA",     // RFC5932
            "TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA",     // RFC5932
            "TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA",    // RFC5932
            "TLS_DH_DSS_WITH_SEED_CBC_SHA",             // RFC4162
            "TLS_DH_RSA_WITH_SEED_CBC_SHA",             // RFC4162
            "TLS_DH_anon_WITH_SEED_CBC_SHA",            // RFC4162
            "TLS_DH_RSA_WITH_AES_128_GCM_SHA256",       // RFC5288
            "TLS_DH_RSA_WITH_AES_256_GCM_SHA384",       // RFC5288
            "TLS_DH_DSS_WITH_AES_128_GCM_SHA256",       // RFC5288
            "TLS_DH_DSS_WITH_AES_256_GCM_SHA384",       // RFC5288
            "TLS_DH_anon_WITH_AES_128_GCM_SHA256",      // RFC5288
            "TLS_DH_anon_WITH_AES_256_GCM_SHA384",      // RFC5288
            "TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA256",  // RFC5932
            "TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA256",  // RFC5932
            "TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA256", // RFC5932
            "TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA256",  // RFC5932
            "TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA256",  // RFC5932
            "TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA256", // RFC5932
            "TLS_DH_DSS_WITH_ARIA_128_CBC_SHA256",      // RFC6209
            "TLS_DH_DSS_WITH_ARIA_256_CBC_SHA384",      // RFC6209
            "TLS_DH_RSA_WITH_ARIA_128_CBC_SHA256",      // RFC6209
            "TLS_DH_RSA_WITH_ARIA_256_CBC_SHA384",      // RFC6209
            "TLS_DH_anon_WITH_ARIA_128_CBC_SHA256",     // RFC6209
            "TLS_DH_anon_WITH_ARIA_256_CBC_SHA384",     // RFC6209
            "TLS_DH_RSA_WITH_ARIA_128_GCM_SHA256",      // RFC6209
            "TLS_DH_RSA_WITH_ARIA_256_GCM_SHA384",      // RFC6209
            "TLS_DH_DSS_WITH_ARIA_128_GCM_SHA256",      // RFC6209
            "TLS_DH_DSS_WITH_ARIA_256_GCM_SHA384",      // RFC6209
            "TLS_DH_anon_WITH_ARIA_128_GCM_SHA256",     // RFC6209
            "TLS_DH_anon_WITH_ARIA_256_GCM_SHA384",     // RFC6209
            "TLS_DH_RSA_WITH_CAMELLIA_128_GCM_SHA256",  // RFC6367
            "TLS_DH_RSA_WITH_CAMELLIA_256_GCM_SHA384",  // RFC6367
            "TLS_DH_DSS_WITH_CAMELLIA_128_GCM_SHA256",  // RFC6367
            "TLS_DH_DSS_WITH_CAMELLIA_256_GCM_SHA384",  // RFC6367
            "TLS_DH_anon_WITH_CAMELLIA_128_GCM_SHA256", // RFC6367
            "TLS_DH_anon_WITH_CAMELLIA_256_GCM_SHA384", // RFC6367
    })
    void testRfc10015Section5_1_DhDepreacted(final String deprecatedCipherSuite) {
        Assertions.assertTrue(TlsCiphers.isWeak(deprecatedCipherSuite), deprecatedCipherSuite);
    }

    /**
     * Tests ECDH cipher suites whose use is discouraged by <a href="https://www.rfc-editor.org/rfc/rfc10015.html">RFC10015</a> Section 5.2.
     *
     * @param discouragedCipherSuite ECDH cipher suites whose use is discouraged by RFC10015 Section 5.2
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "TLS_ECDH_ECDSA_WITH_NULL_SHA",                // RFC8422
            "TLS_ECDH_ECDSA_WITH_RC4_128_SHA",             // RFC8422, RFC6347
            "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",        // RFC8422
            "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA",         // RFC8422
            "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA",         // RFC8422
            "TLS_ECDH_RSA_WITH_NULL_SHA",                  // RFC8422
            "TLS_ECDH_RSA_WITH_RC4_128_SHA",               // RFC8422, RFC6347
            "TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",          // RFC8422
            "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",           // RFC8422
            "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA",           // RFC8422
            "TLS_ECDH_anon_WITH_NULL_SHA",                 // RFC8422
            "TLS_ECDH_anon_WITH_RC4_128_SHA",              // RFC8422, RFC6347
            "TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA",         // RFC8422
            "TLS_ECDH_anon_WITH_AES_128_CBC_SHA",          // RFC8422
            "TLS_ECDH_anon_WITH_AES_256_CBC_SHA",          // RFC8422
            "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256",      // RFC5289
            "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384",      // RFC5289
            "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256",        // RFC5289
            "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384",        // RFC5289
            "TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256",      // RFC5289
            "TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384",      // RFC5289
            "TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256",        // RFC5289
            "TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384",        // RFC5289
            "TLS_ECDH_ECDSA_WITH_ARIA_128_CBC_SHA256",     // RFC6209
            "TLS_ECDH_ECDSA_WITH_ARIA_256_CBC_SHA384",     // RFC6209
            "TLS_ECDH_RSA_WITH_ARIA_128_CBC_SHA256",       // RFC6209
            "TLS_ECDH_RSA_WITH_ARIA_256_CBC_SHA384",       // RFC6209
            "TLS_ECDH_ECDSA_WITH_ARIA_128_GCM_SHA256",     // RFC6209
            "TLS_ECDH_ECDSA_WITH_ARIA_256_GCM_SHA384",     // RFC6209
            "TLS_ECDH_RSA_WITH_ARIA_128_GCM_SHA256",       // RFC6209
            "TLS_ECDH_RSA_WITH_ARIA_256_GCM_SHA384",       // RFC6209
            "TLS_ECDH_ECDSA_WITH_CAMELLIA_128_CBC_SHA256", // RFC6367
            "TLS_ECDH_ECDSA_WITH_CAMELLIA_256_CBC_SHA384", // RFC6367
            "TLS_ECDH_RSA_WITH_CAMELLIA_128_CBC_SHA256",   // RFC6367
            "TLS_ECDH_RSA_WITH_CAMELLIA_256_CBC_SHA384",   // RFC6367
            "TLS_ECDH_ECDSA_WITH_CAMELLIA_128_GCM_SHA256", // RFC6367
            "TLS_ECDH_ECDSA_WITH_CAMELLIA_256_GCM_SHA384", // RFC6367
            "TLS_ECDH_RSA_WITH_CAMELLIA_128_GCM_SHA256",   // RFC6367
            "TLS_ECDH_RSA_WITH_CAMELLIA_256_GCM_SHA384",   // RFC6367
    })
    void testRfc10015Section5_2_EcdhDiscouraged(final String discouragedCipherSuite) {
        Assertions.assertTrue(TlsCiphers.isWeak(discouragedCipherSuite), discouragedCipherSuite);
    }

    /**
     * Tests DHE cipher suites deprecated by <a href="https://www.rfc-editor.org/rfc/rfc10015.html">RFC10015</a> Section 5.3.
     *
     * @param deprecatedCipherSuite DHE cipher suites deprecated by RFC10015 Section 5.3.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "TLS_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA",     // RFC4346
            "TLS_DHE_DSS_WITH_DES_CBC_SHA",              // RFC8996
            "TLS_DHE_DSS_WITH_3DES_EDE_CBC_SHA",         // RFC5246
            "TLS_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",     // RFC4346
            "TLS_DHE_RSA_WITH_DES_CBC_SHA",              // RFC8996
            "TLS_DHE_RSA_WITH_3DES_EDE_CBC_SHA",         // RFC5246
            "TLS_DHE_PSK_WITH_NULL_SHA",                 // RFC4785
            "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",          // RFC5246
            "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",          // RFC5246
            "TLS_DHE_DSS_WITH_AES_256_CBC_SHA",          // RFC5246
            "TLS_DHE_RSA_WITH_AES_256_CBC_SHA",          // RFC5246
            "TLS_DHE_DSS_WITH_AES_128_CBC_SHA256",       // RFC5246
            "TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA",     // RFC5932
            "TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA",     // RFC5932
            "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",       // RFC5246
            "TLS_DHE_DSS_WITH_AES_256_CBC_SHA256",       // RFC5246
            "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256",       // RFC5246
            "TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA",     // RFC5932
            "TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA",     // RFC5932
            "TLS_DHE_PSK_WITH_RC4_128_SHA",              // RFC4279, RFC6347
            "TLS_DHE_PSK_WITH_3DES_EDE_CBC_SHA",         // RFC4279
            "TLS_DHE_PSK_WITH_AES_128_CBC_SHA",          // RFC4279
            "TLS_DHE_PSK_WITH_AES_256_CBC_SHA",          // RFC4279
            "TLS_DHE_DSS_WITH_SEED_CBC_SHA",             // RFC4162
            "TLS_DHE_RSA_WITH_SEED_CBC_SHA",             // RFC4162
            "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",       // RFC5288
            "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",       // RFC5288
            "TLS_DHE_DSS_WITH_AES_128_GCM_SHA256",       // RFC5288
            "TLS_DHE_DSS_WITH_AES_256_GCM_SHA384",       // RFC5288
            "TLS_DHE_PSK_WITH_AES_128_GCM_SHA256",       // RFC5487
            "TLS_DHE_PSK_WITH_AES_256_GCM_SHA384",       // RFC5487
            "TLS_DHE_PSK_WITH_AES_128_CBC_SHA256",       // RFC5487
            "TLS_DHE_PSK_WITH_AES_256_CBC_SHA384",       // RFC5487
            "TLS_DHE_PSK_WITH_NULL_SHA256",              // RFC5487
            "TLS_DHE_PSK_WITH_NULL_SHA384",              // RFC5487
            "TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA256",  // RFC5932
            "TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA256",  // RFC5932
            "TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA256",  // RFC5932
            "TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA256",  // RFC5932
            "TLS_DHE_DSS_WITH_ARIA_128_CBC_SHA256",      // RFC6209
            "TLS_DHE_DSS_WITH_ARIA_256_CBC_SHA384",      // RFC6209
            "TLS_DHE_RSA_WITH_ARIA_128_CBC_SHA256",      // RFC6209
            "TLS_DHE_RSA_WITH_ARIA_256_CBC_SHA384",      // RFC6209
            "TLS_DHE_RSA_WITH_ARIA_128_GCM_SHA256",      // RFC6209
            "TLS_DHE_RSA_WITH_ARIA_256_GCM_SHA384",      // RFC6209
            "TLS_DHE_DSS_WITH_ARIA_128_GCM_SHA256",      // RFC6209
            "TLS_DHE_DSS_WITH_ARIA_256_GCM_SHA384",      // RFC6209
            "TLS_DHE_PSK_WITH_ARIA_128_CBC_SHA256",      // RFC6209
            "TLS_DHE_PSK_WITH_ARIA_256_CBC_SHA384",      // RFC6209
            "TLS_DHE_PSK_WITH_ARIA_128_GCM_SHA256",      // RFC6209
            "TLS_DHE_PSK_WITH_ARIA_256_GCM_SHA384",      // RFC6209
            "TLS_DHE_RSA_WITH_CAMELLIA_128_GCM_SHA256",  // RFC6367
            "TLS_DHE_RSA_WITH_CAMELLIA_256_GCM_SHA384",  // RFC6367
            "TLS_DHE_DSS_WITH_CAMELLIA_128_GCM_SHA256",  // RFC6367
            "TLS_DHE_DSS_WITH_CAMELLIA_256_GCM_SHA384",  // RFC6367
            "TLS_DHE_PSK_WITH_CAMELLIA_128_GCM_SHA256",  // RFC6367
            "TLS_DHE_PSK_WITH_CAMELLIA_256_GCM_SHA384",  // RFC6367
            "TLS_DHE_PSK_WITH_CAMELLIA_128_CBC_SHA256",  // RFC6367
            "TLS_DHE_PSK_WITH_CAMELLIA_256_CBC_SHA384",  // RFC6367
            "TLS_DHE_RSA_WITH_AES_128_CCM",              // RFC6655
            "TLS_DHE_RSA_WITH_AES_256_CCM",              // RFC6655
            "TLS_DHE_RSA_WITH_AES_128_CCM_8",            // RFC6655
            "TLS_DHE_RSA_WITH_AES_256_CCM_8",            // RFC6655
            "TLS_DHE_PSK_WITH_AES_128_CCM",              // RFC6655
            "TLS_DHE_PSK_WITH_AES_256_CCM",              // RFC6655
            "TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256", // RFC7905
            "TLS_DHE_PSK_WITH_CHACHA20_POLY1305_SHA256", // RFC7905
            "TLS_PSK_DHE_WITH_AES_128_CCM_8",            // RFC6655
            "TLS_PSK_DHE_WITH_AES_256_CCM_8",            // RFC6655
    })
    void testRfc10015Section5_3_DheDeprecated(final String deprecatedCipherSuite) {
        Assertions.assertTrue(TlsCiphers.isWeak(deprecatedCipherSuite), deprecatedCipherSuite);
    }

    /**
     * Tests RSA cipher suites deprecated by <a href="https://www.rfc-editor.org/rfc/rfc10015.html">RFC10015</a> Section 5.4.
     *
     * @param deprecatedCipherSuite RSA cipher suites deprecated by RFC10015 Section 5.4.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "TLS_RSA_WITH_NULL_MD5",                     // RFC5246
            "TLS_RSA_WITH_NULL_SHA",                     // RFC5246
            "TLS_RSA_EXPORT_WITH_RC4_40_MD5",            // RFC4346 RFC6347
            "TLS_RSA_WITH_RC4_128_MD5",                  // RFC5246 RFC6347
            "TLS_RSA_WITH_RC4_128_SHA",                  // RFC5246 RFC6347
            "TLS_RSA_EXPORT_WITH_RC2_CBC_40_MD5",        // RFC4346
            "TLS_RSA_WITH_IDEA_CBC_SHA",                 // RFC8996
            "TLS_RSA_EXPORT_WITH_DES40_CBC_SHA",         // RFC4346
            "TLS_RSA_WITH_DES_CBC_SHA",                  // RFC8996
            "TLS_RSA_WITH_3DES_EDE_CBC_SHA",             // RFC5246
            "TLS_RSA_PSK_WITH_NULL_SHA",                 // RFC4785
            "TLS_RSA_WITH_AES_128_CBC_SHA",              // RFC5246
            "TLS_RSA_WITH_AES_256_CBC_SHA",              // RFC5246
            "TLS_RSA_WITH_NULL_SHA256",                  // RFC5246
            "TLS_RSA_WITH_AES_128_CBC_SHA256",           // RFC5246
            "TLS_RSA_WITH_AES_256_CBC_SHA256",           // RFC5246
            "TLS_RSA_WITH_CAMELLIA_128_CBC_SHA",         // RFC5932
            "TLS_RSA_WITH_CAMELLIA_256_CBC_SHA",         // RFC5932
            "TLS_RSA_PSK_WITH_RC4_128_SHA",              // RFC4279 RFC6347
            "TLS_RSA_PSK_WITH_3DES_EDE_CBC_SHA",         // RFC4279
            "TLS_RSA_PSK_WITH_AES_128_CBC_SHA",          // RFC4279
            "TLS_RSA_PSK_WITH_AES_256_CBC_SHA",          // RFC4279
            "TLS_RSA_WITH_SEED_CBC_SHA",                 // RFC4162
            "TLS_RSA_WITH_AES_128_GCM_SHA256",           // RFC5288
            "TLS_RSA_WITH_AES_256_GCM_SHA384",           // RFC5288
            "TLS_RSA_PSK_WITH_AES_128_GCM_SHA256",       // RFC5487
            "TLS_RSA_PSK_WITH_AES_256_GCM_SHA384",       // RFC5487
            "TLS_RSA_PSK_WITH_AES_128_CBC_SHA256",       // RFC5487
            "TLS_RSA_PSK_WITH_AES_256_CBC_SHA384",       // RFC5487
            "TLS_RSA_PSK_WITH_NULL_SHA256",              // RFC5487
            "TLS_RSA_PSK_WITH_NULL_SHA384",              // RFC5487
            "TLS_RSA_WITH_CAMELLIA_128_CBC_SHA256",      // RFC5932
            "TLS_RSA_WITH_CAMELLIA_256_CBC_SHA256",      // RFC5932
            "TLS_RSA_WITH_ARIA_128_CBC_SHA256",          // RFC6209
            "TLS_RSA_WITH_ARIA_256_CBC_SHA384",          // RFC6209
            "TLS_RSA_WITH_ARIA_128_GCM_SHA256",          // RFC6209
            "TLS_RSA_WITH_ARIA_256_GCM_SHA384",          // RFC6209
            "TLS_RSA_PSK_WITH_ARIA_128_CBC_SHA256",      // RFC6209
            "TLS_RSA_PSK_WITH_ARIA_256_CBC_SHA384",      // RFC6209
            "TLS_RSA_PSK_WITH_ARIA_128_GCM_SHA256",      // RFC6209
            "TLS_RSA_PSK_WITH_ARIA_256_GCM_SHA384",      // RFC6209
            "TLS_RSA_WITH_CAMELLIA_128_GCM_SHA256",      // RFC6367
            "TLS_RSA_WITH_CAMELLIA_256_GCM_SHA384",      // RFC6367
            "TLS_RSA_PSK_WITH_CAMELLIA_128_GCM_SHA256",  // RFC6367
            "TLS_RSA_PSK_WITH_CAMELLIA_256_GCM_SHA384",  // RFC6367
            "TLS_RSA_PSK_WITH_CAMELLIA_128_CBC_SHA256",  // RFC6367
            "TLS_RSA_PSK_WITH_CAMELLIA_256_CBC_SHA384",  // RFC6367
            "TLS_RSA_WITH_AES_128_CCM",                  // RFC6655
            "TLS_RSA_WITH_AES_256_CCM",                  // RFC6655
            "TLS_RSA_WITH_AES_128_CCM_8",                // RFC6655
            "TLS_RSA_WITH_AES_256_CCM_8",                // RFC6655
            "TLS_RSA_PSK_WITH_CHACHA20_POLY1305_SHA256", // RFC7905
    })
    void testRfc10015Section5_4_RsaDeprecated(final String deprecatedCipherSuite) {
        Assertions.assertTrue(TlsCiphers.isWeak(deprecatedCipherSuite), deprecatedCipherSuite);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // TLS 1.2 from https://www.ibm.com/docs/en/wm-integration-ipaas?topic=securing-understanding-cipher-suites
            "TLS_AES_128_GCM_SHA256", // RFC8446
            "TLS_AES_256_GCM_SHA384", // RFC8446
            "TLS_CHACHA20_POLY1305_SHA256", // RFC8446
            "TLS_AES_128_CCM_SHA256", // RFC8446
            "TLS_AES_128_CCM_8_SHA256", // RFC8446
            // TLS 1.3 from https://www.ibm.com/docs/en/wm-integration-ipaas?topic=securing-understanding-cipher-suites
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256", // RFC5289
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384", // RFC5289
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256", // RFC5289
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384", // RFC5289
            "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256", // RFC7905
            "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256", // RFC7905
            "TLS_ECDHE_PSK_WITH_CHACHA20_POLY1305_SHA256",   // RFC7905
            "TLS_ECDHE_PSK_WITH_AES_128_GCM_SHA256", // RFC8442
            "TLS_ECDHE_PSK_WITH_AES_256_GCM_SHA384", // RFC8442
            "TLS_ECDHE_PSK_WITH_AES_128_CCM_SHA256", // RFC8442
    })
    void testStrongCipherSuites(final String strongCipherSuite) {
        Assertions.assertFalse(TlsCiphers.isWeak(strongCipherSuite), strongCipherSuite);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SSL_RSA_WITH_RC4_128_SHA",
            "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_DH_anon_WITH_AES_128_CBC_SHA",
            "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
            "SSL_RSA_WITH_NULL_SHA",
            "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
            "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_DH_anon_WITH_AES_256_GCM_SHA384",
            "TLS_ECDH_anon_WITH_AES_256_CBC_SHA",
            "TLS_RSA_WITH_NULL_SHA256",
            "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
            "SSL_DH_anon_EXPORT_WITH_RC4_40_MD5",
            "TLS_KRB5_EXPORT_WITH_RC4_40_SHA",
            "SSL_RSA_EXPORT_WITH_RC2_CBC_40_MD5"
    })
    void testWeakCiphersDisabledByDefault(final String weakCiphersSuite) {
        Assertions.assertTrue(TlsCiphers.isWeak(weakCiphersSuite), weakCiphersSuite);
    }

}
