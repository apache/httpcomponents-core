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

/**
 * Unit tests for {@link TlsCiphers}.
 */
class TestTlsCiphers {

    @Test
    void testStrongCipherSuites() {
        final String[] strongCipherSuites = {
                "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
                "TLS_RSA_WITH_AES_256_CBC_SHA256",
                "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256",
                "TLS_RSA_WITH_AES_128_CBC_SHA",
                "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
                "TLS_RSA_WITH_AES_256_GCM_SHA384"
        };
        for (final String cipherSuite : strongCipherSuites) {
            Assertions.assertFalse(TlsCiphers.isWeak(cipherSuite));
        }
    }

    @Test
    void testWeakCiphersDisabledByDefault() {
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
                "SSL_RSA_EXPORT_WITH_RC2_CBC_40_MD5"
        };
        for (final String cipherSuite : weakCiphersSuites) {
            Assertions.assertTrue(TlsCiphers.isWeak(cipherSuite));
        }
    }

   @Test
    void excludeH2Blacklisted (){
       final String[] mixCipherSuites = {
               "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
               "TLS_RSA_WITH_AES_256_CBC_SHA256",
               "AES_SHA_US",
               "TLS_RSA_WITH_AES_128_CBC_SHA",
               "NULL_SHA",
               "TLS_RSA_WITH_AES_256_GCM_SHA384"
       };

       final String[] strongCipherSuites = TlsCiphers.excludeH2Blacklisted(mixCipherSuites);
       for (final String cipherSuite : strongCipherSuites) {
           Assertions.assertFalse(TlsCiphers.isWeak(cipherSuite));
       }
   }

    @Test
    void excludeWeak (){
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

        final String[] strongCipherSuites = TlsCiphers.excludeWeak(weakCiphersSuites);
        for (final String cipherSuite : strongCipherSuites) {
            Assertions.assertFalse(TlsCiphers.isWeak(cipherSuite));
        }
    }

    @Test
    void excludeWeakNull(){
        Assertions.assertNull(TlsCiphers.excludeWeak((String[]) null));
    }

}
