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

package org.apache.hc.core5.ssl;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.junit.jupiter.api.Test;

public class SSLContextsTest {

    @Test
    void createDefault() {
        final SSLContext sslContext = SSLContexts.createDefault();
        assertAll(
                () -> assertNotNull(sslContext),
                () -> assertEquals(SSLContextBuilder.TLS, sslContext.getProtocol()),
                () -> assertNotNull(sslContext.getProvider())
        );
    }

    @Test
    void createSystemDefault() {
        final SSLContext sslContext = SSLContexts.createSystemDefault();
        assertAll(
                () -> assertNotNull(sslContext),
                () -> assertEquals("Default", sslContext.getProtocol()),
                () -> assertNotNull(sslContext.getProvider())
        );
    }

    @Test
    void custom() throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException, KeyManagementException {

        final SSLContext sslContext = SSLContexts.custom()
                .setKeyStoreType(KeyStore.getDefaultType())
                .setKeyManagerFactoryAlgorithm(KeyManagerFactory.getDefaultAlgorithm())
                .setTrustManagerFactoryAlgorithm(TrustManagerFactory.getDefaultAlgorithm())
                .setProvider("SunJSSE")
                .setProtocol("TLS")
                .setSecureRandom(null)
                .loadTrustMaterial((KeyStore) null, null)
                .loadKeyMaterial((KeyStore) null, null, null)
                .build();

        assertAll(
                () -> assertNotNull(sslContext),
                () -> assertEquals(SSLContextBuilder.TLS, sslContext.getProtocol()),
                () -> assertEquals("SunJSSE", sslContext.getProvider().getName())
        );
    }
}