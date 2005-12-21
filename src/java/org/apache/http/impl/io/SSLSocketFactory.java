/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 2002-2004 The Apache Software Foundation
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

package org.apache.http.impl.io;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.http.ConnectTimeoutException;
import org.apache.http.io.SecureSocketFactory;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

/**
 * <p>A wrapper class for the standard JSSE SSLSocketFactory</p>
 *   
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */

public class SSLSocketFactory implements SecureSocketFactory {

    /**
     * The factory singleton.
     */
    private static final SSLSocketFactory DEFAULT_FACTORY = new SSLSocketFactory();
    
    /**
     * Gets an singleton instance of the SSLProtocolSocketFactory.
     * @return a SSLProtocolSocketFactory
     */
    public static SSLSocketFactory getSocketFactory() {
        return DEFAULT_FACTORY;
    }
    
    /**
     * Attempts to get a new socket connection to the given host within the given time limit.
     *  
     * @param host the host name/IP
     * @param port the port on the host
     * @param localAddress the local host name/IP to bind the socket to
     * @param localPort the port on the local machine
     * @param params {@link HttpConnectionParams Http connection parameters}
     * 
     * @return Socket a new socket
     * 
     * @throws IOException if an I/O error occurs while creating the socket
     * @throws UnknownHostException if the IP address of the host cannot be
     * determined
     * @throws ConnectTimeoutException if socket cannot be connected within the
     *  given time limit
     * 
     * @since 3.0
     */
    public Socket createSocket(
        final String host,
        final int port,
        final InetAddress localAddress,
        final int localPort,
        final HttpParams params
    ) throws IOException, UnknownHostException, ConnectTimeoutException {
        if (params == null) {
            throw new IllegalArgumentException("Parameters may not be null");
        }
        int timeout = HttpConnectionParams.getConnectionTimeout(params);
        if (timeout != 0) {
            throw new IllegalStateException("Connection timeout is not supported in old IO mode");
        }
        javax.net.ssl.SSLSocketFactory socketfactory = 
            (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
        if (localAddress != null) {
            return socketfactory.createSocket(host, port, localAddress, localPort);
        } else {
            return socketfactory.createSocket(host, port);
        }
    }

    /**
     * @see SecureProtocolSocketFactory#createSocket(java.net.Socket,java.lang.String,int,boolean)
     */
    public Socket createSocket(
        Socket socket,
        String host,
        int port,
        boolean autoClose)
        throws IOException, UnknownHostException
    {
        javax.net.ssl.SSLSocketFactory socketfactory = 
            (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
        return socketfactory.createSocket(socket, host, port, autoClose);
    }
    
}
