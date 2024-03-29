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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * @deprecated Use {@link IOReactorConfig}.
 *
 * As of version 5.0.1 {@link #createHandler(ProtocolIOSession, Object)} throws {@link UnsupportedOperationException}.
 */
@Deprecated
public class SocksProxyProtocolHandlerFactory implements IOEventHandlerFactory {

    private final InetSocketAddress targetAddress;
    private final String username;
    private final String password;
    private final IOEventHandlerFactory eventHandlerFactory;

    public SocksProxyProtocolHandlerFactory(final SocketAddress targetAddress, final String username, final String password, final IOEventHandlerFactory eventHandlerFactory) throws IOException {
        this.eventHandlerFactory = eventHandlerFactory;
        this.username = username;
        this.password = password;
        if (targetAddress instanceof InetSocketAddress) {
            this.targetAddress = (InetSocketAddress) targetAddress;
        } else {
            throw new IOException("Unsupported target address type for SOCKS proxy connection: " + targetAddress.getClass());
        }
    }

    @Override
    public IOEventHandler createHandler(final ProtocolIOSession ioSession, final Object attachment) {
        throw new UnsupportedOperationException();
    }

}
