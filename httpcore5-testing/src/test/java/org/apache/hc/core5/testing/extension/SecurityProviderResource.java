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
package org.apache.hc.core5.testing.extension;

import java.security.Provider;
import java.security.Security;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class SecurityProviderResource implements BeforeEachCallback, AfterEachCallback {

    private final String securityProviderName;

    private Provider securityProvider;

    public SecurityProviderResource(final String securityProviderName) {
        super();
        this.securityProviderName = securityProviderName;
    }

    @Override
    public void beforeEach(final ExtensionContext context) throws Exception {
        if ("Conscrypt".equalsIgnoreCase(securityProviderName)) {
            final Set<String> supportedArchitectures = new HashSet<>(Arrays.asList("x86", "x86_64",
                    "x86-64", "amd64", "aarch64", "armeabi-v7a", "arm64-v8a"));
            Assumptions.assumeTrue(supportedArchitectures.contains(System.getProperty("os.arch")));
            try {
                securityProvider = Conscrypt.newProviderBuilder().provideTrustManager(true).build();
            } catch (final UnsatisfiedLinkError e) {
                Assertions.fail("Conscrypt provider failed to be loaded: " + e.getMessage());
            }
        } else if ("Oracle".equalsIgnoreCase(securityProviderName)) {
            securityProvider = null;
        } else if ("SUN".equalsIgnoreCase(securityProviderName)) {
            securityProvider = null;
        } else {
            throw new AssertionError("Unsupported security provider: " + securityProviderName);
        }
        if (securityProvider != null) {
            Security.insertProviderAt(securityProvider, 1);
        }
    }

    @Override
    public void afterEach(final ExtensionContext context) throws Exception {
        if (securityProvider != null) {
            Security.removeProvider(securityProvider.getName());
            securityProvider = null;
        }
    }

    public Provider securityProvider() {
        return securityProvider != null ? securityProvider : Security.getProvider(securityProviderName);
    }

}
