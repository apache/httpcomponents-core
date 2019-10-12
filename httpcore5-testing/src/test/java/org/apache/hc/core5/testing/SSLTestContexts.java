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

import java.net.URL;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.ssl.SSLContextBuilder;

public final class SSLTestContexts {

    public static SSLContext createServerSSLContext() throws Exception {
        final URL keyStoreURL = SSLTestContexts.class.getResource("/test.p12");
        final String storePassword = "nopassword";
        return SSLContextBuilder.create()
                .setKeyStoreType("pkcs12")
                .loadTrustMaterial(keyStoreURL, storePassword.toCharArray())
                .loadKeyMaterial(keyStoreURL, storePassword.toCharArray(), storePassword.toCharArray())
                .build();
    }

    public static SSLContext createClientSSLContext() throws Exception {
        final URL keyStoreURL = SSLTestContexts.class.getResource("/test.p12");
        final String storePassword = "nopassword";
        return SSLContextBuilder.create()
                .setKeyStoreType("pkcs12")
                .loadTrustMaterial(keyStoreURL, storePassword.toCharArray())
                .build();
    }

}
