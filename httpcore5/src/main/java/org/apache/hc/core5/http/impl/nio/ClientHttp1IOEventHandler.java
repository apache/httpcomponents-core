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

package org.apache.hc.core5.http.impl.nio;

import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.net.InetAddressUtils;

/**
 * {@link org.apache.hc.core5.reactor.IOEventHandler} that implements
 *  client side HTTP/1.1 messaging protocol with full support for
 *  duplexed message transmission and message pipelining.
 *
 * @since 5.0
 */
public class ClientHttp1IOEventHandler extends AbstractHttp1IOEventHandler {

    public ClientHttp1IOEventHandler(final ClientHttp1StreamDuplexer streamDuplexer) {
        super(streamDuplexer);
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        InetAddressUtils.formatAddress(buf, getLocalAddress());
        buf.append("->");
        InetAddressUtils.formatAddress(buf, getRemoteAddress());
        buf.append(" [");
        streamDuplexer.appendState(buf);
        buf.append("]");
        return buf.toString();
    }

    @Override
    public ProtocolVersion getProtocolVersion() {
        final ProtocolVersion protocolVersion = super.getProtocolVersion();
        return protocolVersion != null ? protocolVersion : HttpVersion.HTTP_1_1;
    }

}

