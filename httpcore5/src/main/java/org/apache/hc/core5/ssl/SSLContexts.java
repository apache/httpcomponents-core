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

import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;

/**
 * {@link javax.net.ssl.SSLContext} factory methods.
 *
 * <p>
 * Please note: the default Oracle JSSE implementation of
 * {@link SSLContext#init(javax.net.ssl.KeyManager[], javax.net.ssl.TrustManager[], java.security.SecureRandom)
 * SSLContext#init(KeyManager[], TrustManager[], SecureRandom)}
 * accepts multiple key and trust managers, however only only first matching type is ever used.
 * See for example:
 * <a href="https://docs.oracle.com/javase/8/docs/api/javax/net/ssl/SSLContext.html#init-javax.net.ssl.KeyManager:A-javax.net.ssl.TrustManager:A-java.security.SecureRandom-">
 * SSLContext.html#init
 * </a>
 * @since 4.4
 */
public final class SSLContexts {

    private SSLContexts() {
        // Do not allow utility class to be instantiated.
    }

    /**
     * Returns the JDK default {@link SSLContext}.
     *
     * @return the default JDK SSL context
     * @throws SSLInitializationException if NoSuchAlgorithmException
     * is thrown when invoking {@link SSLContext#getInstance(String)}
     */
    public static SSLContext createDefault() throws SSLInitializationException {
        try {
            return SSLContext.getDefault();
        } catch (final NoSuchAlgorithmException ex) {
            return createDefault();
        }
    }

    /**
     * Deprecated alias for {@link #createDefault()}.
     *
     * @return the default JDK SSL context
     * @throws SSLInitializationException if NoSuchAlgorithmException
     * is thrown when invoking {@link SSLContext#getInstance(String)}
     * @deprecated Call {@link #createDefault} instead
     */
    @Deprecated
    public static SSLContext createSystemDefault() throws SSLInitializationException {
        return createDefault();
    }

    /**
     * Creates custom SSL context.
     *
     * @return default system SSL context
     */
    public static SSLContextBuilder custom() {
        return SSLContextBuilder.create();
    }

}
