/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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

package org.apache.http.impl;

import java.io.IOException;
import java.net.Socket;

import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

/**
 * Default implementation of a client-side HTTP connection.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class DefaultHttpClientConnection extends SocketHttpClientConnection {

    public DefaultHttpClientConnection() {
        super();
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * {@link CoreConnectionPNames#TCP_NODELAY} parameter determines whether 
     * Nagle's algorithm is to be used. The Nagle's algorithm tries to conserve 
     * bandwidth by minimizing the number of segments that are sent. When 
     * applications wish to decrease network latency and increase performance, 
     * they can disable Nagle's algorithm (that is enable TCP_NODELAY). Data 
     * will be sent earlier, at the cost of an increase in bandwidth 
     * consumption.
     * <p>
     * {@link CoreConnectionPNames#SO_TIMEOUT} parameter defines the socket 
     * timeout in milliseconds, which is the timeout for waiting for data. 
     * A timeout value of zero is interpreted as an infinite timeout.
     * <p>
     * {@link CoreConnectionPNames#SO_LINGER} parameter defines linger time 
     * in seconds. The maximum timeout value is platform specific. Value 
     * <code>0</code> implies that the option is disabled. Value <code>-1</code>
     * implies that the JRE default is to be used. The setting only affects 
     * socket close.
     */
    public void bind(
            final Socket socket, 
            final HttpParams params) throws IOException {
        if (socket == null) {
            throw new IllegalArgumentException("Socket may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        assertNotOpen();
        socket.setTcpNoDelay(HttpConnectionParams.getTcpNoDelay(params));
        socket.setSoTimeout(HttpConnectionParams.getSoTimeout(params));
        
        int linger = HttpConnectionParams.getLinger(params);
        if (linger >= 0) {
            socket.setSoLinger(linger > 0, linger);
        }
        super.bind(socket, params);
    }

    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("[");
        if (isOpen()) {
            buffer.append(getRemotePort());
        } else {
            buffer.append("closed");
        }
        buffer.append("]");
        return buffer.toString();
    }
    
}
