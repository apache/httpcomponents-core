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

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.net.Ports;
import org.apache.hc.core5.util.Args;

/**
 * Endpoint initialization parameters
 *
 * @since 5.1
 */
@Internal
public final class EndpointParameters implements NamedEndpoint {

    private final String scheme;
    private final String hostName;
    private final int port;
    private final Object attachment;

    public EndpointParameters(final String scheme, final String hostName, final int port, final Object attachment) {
        this.scheme = Args.notBlank(scheme, "Protocol scheme");
        this.hostName = Args.notBlank(hostName, "Endpoint name");
        this.port = Ports.checkWithDefault(port);
        this.attachment = attachment;
    }

    public EndpointParameters(final HttpHost host, final Object attachment) {
        Args.notNull(host, "HTTP host");
        this.scheme = host.getSchemeName();
        this.hostName = host.getHostName();
        this.port = host.getPort();
        this.attachment = attachment;
    }

    public String getScheme() {
        return scheme;
    }

    @Override
    public String getHostName() {
        return hostName;
    }

    @Override
    public int getPort() {
        return port;
    }

    public Object getAttachment() {
        return attachment;
    }

    @Override
    public String toString() {
        return "EndpointParameters{" +
                "scheme='" + scheme + '\'' +
                ", name='" + hostName + '\'' +
                ", port=" + port +
                ", attachment=" + attachment +
                '}';
    }

}
