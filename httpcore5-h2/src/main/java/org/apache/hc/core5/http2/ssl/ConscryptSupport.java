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

package org.apache.hc.core5.http2.ssl;

import javax.net.ssl.SSLParameters;

import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.http.ssl.TlsCiphers;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.conscrypt.Conscrypt;

/**
 * Conscrypt TLS support methods
 *
 * @since 5.0
 */
public final class ConscryptSupport {

    public static SSLSessionInitializer initialize(
            final Object attachment,
            final SSLSessionInitializer initializer) {
        return (endpoint, sslEngine) -> {
            final SSLParameters sslParameters = sslEngine.getSSLParameters();
            sslParameters.setProtocols(TLS.excludeWeak(sslParameters.getProtocols()));
            sslParameters.setCipherSuites(TlsCiphers.excludeH2Blacklisted(sslParameters.getCipherSuites()));
            H2TlsSupport.setEnableRetransmissions(sslParameters, false);
            final String[] appProtocols = H2TlsSupport.selectApplicationProtocols(attachment);
            if (Conscrypt.isConscrypt(sslEngine)) {
                sslEngine.setSSLParameters(sslParameters);
                Conscrypt.setApplicationProtocols(sslEngine, appProtocols);
            } else {
                sslParameters.setApplicationProtocols(appProtocols);
                sslEngine.setSSLParameters(sslParameters);
            }
            if (initializer != null) {
                initializer.initialize(endpoint, sslEngine);
            }
        };
    }

    public static SSLSessionVerifier verify(final SSLSessionVerifier verifier) {
        return (endpoint, sslEngine) -> {
            TlsDetails tlsDetails = verifier != null ? verifier.verify(endpoint, sslEngine) : null;
            if (tlsDetails == null && Conscrypt.isConscrypt(sslEngine)) {
                tlsDetails = new TlsDetails(sslEngine.getSession(), Conscrypt.getApplicationProtocol(sslEngine));
            }
            return tlsDetails;
        };
    }

}
