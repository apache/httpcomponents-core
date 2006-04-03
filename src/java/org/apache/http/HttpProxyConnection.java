/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2006 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http;

import java.io.IOException;

import org.apache.http.params.HttpParams;

/**
 * An HTTP connection through a proxy. The semantics of
 * @link org.apache.http.HttpClientConnection#setTargetHost(HttpHost) and
 * @link org.apache.http.HttpClientConnection#getTargetHost() methods of a proxy
 *       connections change their meaning to designate the proxy host.

 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * @version $Revision$
 * @since 4.0
 */
public interface HttpProxyConnection extends HttpClientConnection {

    /**
     * After this connection is opened to the proxy, this method may be called
     * to create a new connection over it. Subsequent data is sent over the
     * resulting connection.
     * 
     * @param targetHost The final target host
     * @param params The parameters effective for this connection
     * @throws IOException
     */
    void tunnelTo(HttpHost targetHost, HttpParams params) throws IOException;
    
    /**
     * Returns the target host as provided by
     * 
     * @link #tunnelTo(HttpHost, HttpParams).
     */
    HttpHost getTunnelTarget();

    /**
     * Checks if
     * 
     * @link #tunnelTo(HttpHost, HttpParams) has been called on this connection.
     * @return true if tunnelTo has been called, false if not
     */
    boolean isTunnelActive();

    /**
     * Checks if the tunnel uses a secure socket.
     * 
     * @return true if this is a secure tunnel.
     */
    boolean isSecure();
    
}
