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
 * An HTTP connection through a proxy.
 * The semantics of the
 * {@link HttpClientConnection#setTargetHost setTargetHost} and
 * {@link HttpClientConnection#getTargetHost getTargetHost}
 * methods of a the base interface change their meaning to designate
 * the proxy host instead of the target host.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 *
 * <!-- empty lines above to avoid 'svn diff' context problems -->
 * @version $Revision$
 * @since 4.0
 */
public interface HttpProxyConnection extends HttpClientConnection {

    /**
     * Creates a tunnelled connection through the proxy.
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
     * Obtains the target host of a tunnel through a proxy.
     *
     * @return  the target host provided to {@link #tunnelTo tunnelTo}
     */
    HttpHost getTunnelTarget();

    /**
     * Checks if this connection is tunnelled.
     *
     * @return <code>true</code> if {@link #tunnelTo tunnelTo} has been called,
     *         <code>false</code> otherwise
     * 
     */
    boolean isTunnelActive();

    /**
     * Checks if the tunnel uses a secure connection.
     * 
     * @return <code>true</code> if this is a secure tunnel
     */
    boolean isSecure();
    
}
