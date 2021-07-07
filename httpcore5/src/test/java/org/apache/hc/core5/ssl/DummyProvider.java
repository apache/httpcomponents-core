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

import java.security.Provider;
import java.security.Security;
import java.util.HashSet;
import java.util.Set;

public class DummyProvider extends Provider {

    private final Provider realJSSEProvider = Security.getProvider(TestSSLContextBuilder.PROVIDER_SUN_JSSE);
    private final Provider realJCEEProvider = Security.getProvider(TestSSLContextBuilder.PROVIDER_SUN_JCE);
    final static String NAME = "FAKE";

    private final Set<String> requestedTypes = new HashSet<>();

    public DummyProvider() {
        super(NAME, 1.1, "http core fake provider 1.1");
    }

    public boolean hasBeenRequested(final String what) {
        return requestedTypes.contains(what);
    }

    @Override
    public Service getService(final String type, final String algorithm) {
        requestedTypes.add(type);
        if ("KeyStore".equals(type)) {
            return realJCEEProvider.getService(type, algorithm);
        }
        return realJSSEProvider.getService(type, algorithm);
    }

    @Override
    public synchronized Set<Service> getServices() {
        return realJSSEProvider.getServices();
    }
}
