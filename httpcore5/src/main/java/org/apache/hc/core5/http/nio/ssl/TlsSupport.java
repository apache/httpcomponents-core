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

package org.apache.hc.core5.http.nio.ssl;

import javax.net.ssl.SSLParameters;

import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.http.ssl.TlsCiphers;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;

/**
 * HTTP/1.1 TLS support methods
 *
 * @since 5.0
 */
public final class TlsSupport {

    public static SSLSessionInitializer enforceStrongSecurity(final SSLSessionInitializer initializer) {
        return (endpoint, sslEngine) -> {
            final SSLParameters sslParameters = sslEngine.getSSLParameters();
            sslParameters.setProtocols(TLS.excludeWeak(sslParameters.getProtocols()));
            sslParameters.setCipherSuites(TlsCiphers.excludeWeak(sslParameters.getCipherSuites()));
            sslEngine.setSSLParameters(sslParameters);
            if (initializer != null) {
                initializer.initialize(endpoint, sslEngine);
            }
        };
    }

}
