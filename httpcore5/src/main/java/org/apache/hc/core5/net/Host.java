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
package org.apache.hc.core5.net;

import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.Locale;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.LangUtils;
import org.apache.hc.core5.util.TextUtils;

/**
 * Component that holds all details needed to describe a network connection
 * to a host. This includes remote host name and port.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class Host implements NamedEndpoint, Serializable {

    private static final long serialVersionUID = 1L;
    private final String name;
    private final String lcName;
    private final int port;

    public Host(final String name, final int port) {
        super();
        this.name   = Args.containsNoBlanks(name, "Host name");
        this.port = Ports.check(port);
        this.lcName = this.name.toLowerCase(Locale.ROOT);
    }

    public static Host create(final String s) throws URISyntaxException {
        Args.notEmpty(s, "HTTP Host");
        final int portIdx = s.lastIndexOf(":");
        int port;
        if (portIdx > 0) {
            try {
                port = Integer.parseInt(s.substring(portIdx + 1));
            } catch (final NumberFormatException ex) {
                throw new URISyntaxException(s, "invalid port");
            }
            final String hostname = s.substring(0, portIdx);
            if (TextUtils.containsBlanks(hostname)) {
                throw new URISyntaxException(s, "hostname contains blanks");
            }
            return new Host(hostname, port);
        }
        throw new URISyntaxException(s, "port not found");
    }

    @Override
    public String getHostName() {
        return name;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof Host) {
            final Host that = (Host) o;
            return this.lcName.equals(that.lcName) && this.port == that.port;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.lcName);
        hash = LangUtils.hashCode(hash, this.port);
        return hash;
    }

    @Override
    public String toString() {
        return name + ":" + port;
    }

}
