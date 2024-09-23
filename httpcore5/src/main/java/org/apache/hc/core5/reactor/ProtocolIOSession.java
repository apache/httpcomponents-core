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

package org.apache.hc.core5.reactor;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;

/**
 * TLS capable, protocol upgradable {@link IOSession}.
 *
 * @since 5.0
 */
public interface ProtocolIOSession extends IOSession, TransportSecurityLayer {

    /**
     * Switches this I/O session to the application protocol with the given ID.
     *
     * @param protocolId the application protocol ID
     * @param callback the result callback
     * @throws UnsupportedOperationException if application protocol switch
     * is not supported.
     */
    default void switchProtocol(String protocolId, FutureCallback<ProtocolIOSession> callback) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Protocol switch not supported");
    }

    /**
     * Registers protocol upgrade handler with the given application protocol ID.
     *
     * @param protocolId the application protocol ID
     * @param upgradeHandler the upgrade handler.
     * @since 5.2
     */
    default void registerProtocol(String protocolId, ProtocolUpgradeHandler upgradeHandler) {
    }

    NamedEndpoint getInitialEndpoint();

}
