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

package org.apache.hc.core5.testing;

import java.io.IOException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.ssl.SSLContextBuilder;

public final class SSLTestContexts {

    public static SSLContext createServerSSLContext(final Provider provider, final String protocol) {
        final URL keyStoreURL = SSLTestContexts.class.getResource("/test.p12");
        final String storePassword = "nopassword";
        try {
            return SSLContextBuilder.create()
                    .setProvider(provider)
                    .setKeyStoreType("pkcs12")
                    .loadTrustMaterial(keyStoreURL, storePassword.toCharArray())
                    .loadKeyMaterial(keyStoreURL, storePassword.toCharArray(), storePassword.toCharArray())
                    .setProtocol(protocol)
                    .build();
        } catch (final NoSuchAlgorithmException | KeyManagementException | KeyStoreException | CertificateException |
                       UnrecoverableKeyException | IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static SSLContext createServerSSLContext(final String protocol) {
        return createServerSSLContext(null, protocol);
    }

    public static SSLContext createServerSSLContext() {
        return createServerSSLContext(null, null);
    }

    public static SSLContext createClientSSLContext(final Provider provider, final String protocol) {
        final URL keyStoreURL = SSLTestContexts.class.getResource("/test.p12");
        final String storePassword = "nopassword";
        try {
            return SSLContextBuilder.create()
                    .setKeyStoreType("pkcs12")
                    .loadTrustMaterial(keyStoreURL, storePassword.toCharArray())
                    .setProtocol(protocol)
                    .build();
        } catch (final NoSuchAlgorithmException | KeyManagementException | KeyStoreException | CertificateException |
                       IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static SSLContext createClientSSLContext(final String protocol) {
        return createClientSSLContext(null, protocol);
    }

    public static SSLContext createClientSSLContext() {
        return createClientSSLContext(null, null);
    }

}
