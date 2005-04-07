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

package org.apache.http.impl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;

import org.apache.http.ConnectTimeoutException;
import org.apache.http.ProtocolSocketFactory;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

/**
 * The default class for creating protocol sockets.  This class just uses the
 * {@link java.net.Socket socket} constructors.
 * 
 * @author Michael Becke
 * 
 * @since 2.0
 */
public class NIOProtocolSocketFactory implements ProtocolSocketFactory {

    /**
     * The factory singleton.
     */
    private static final NIOProtocolSocketFactory factory = new NIOProtocolSocketFactory();
    
    /**
     * Gets an singleton instance of the DefaultProtocolSocketFactory.
     * @return a DefaultProtocolSocketFactory
     */
    public static NIOProtocolSocketFactory getSocketFactory() {
        return factory;
    }
    
    /**
     * Constructor for DefaultProtocolSocketFactory.
     */
    private NIOProtocolSocketFactory() {
        super();
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
        SocketChannel channel = SocketChannel.open(); 
        Socket socket = channel.socket();
        if (localAddress != null) {
            socket.bind(new InetSocketAddress(localAddress, localPort));
        }
        HttpConnectionParams connparams = new HttpConnectionParams(params); 
        int timeout = connparams.getConnectionTimeout();
        socket.connect(new InetSocketAddress(host, port), timeout);
        return socket;
    }

    /**
     * All instances of DefaultProtocolSocketFactory are the same.
     */
    public boolean equals(Object obj) {
        return ((obj != null) && obj.getClass().equals(NIOProtocolSocketFactory.class));
    }

    /**
     * All instances of DefaultProtocolSocketFactory have the same hash code.
     */
    public int hashCode() {
        return NIOProtocolSocketFactory.class.hashCode();
    }

}
