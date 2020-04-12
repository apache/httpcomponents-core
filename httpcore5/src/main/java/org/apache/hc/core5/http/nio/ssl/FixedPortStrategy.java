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

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.apache.hc.core5.util.Args;

/**
 * Basic implementation of {@link SecurePortStrategy} with a fixed list of secure ports.
 *
 * @since 5.0
 *
 * @deprecated Use configuration parameters provided by connection listeners.
 */
@Deprecated
public final class FixedPortStrategy implements SecurePortStrategy {

    private final int[] securePorts;

    public FixedPortStrategy(final int... securePorts) {
        this.securePorts = Args.notNull(securePorts, "Secure ports");
    }

    @Override
    public boolean isSecure(final SocketAddress localAddress) {
        final int port = ((InetSocketAddress) localAddress).getPort();
        for (final int securePort: securePorts) {
            if (port == securePort) {
                return true;
            }
        }
        return false;
    }

}
