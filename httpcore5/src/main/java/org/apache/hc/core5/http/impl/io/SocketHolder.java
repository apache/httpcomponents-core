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

package org.apache.hc.core5.http.impl.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLSocket;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.util.Args;

/**
 * Utility class that holds a {@link Socket} along with copies of its {@link InputStream}
 * and {@link OutputStream}.
 *
 * @since 5.0
 */
public class SocketHolder {

    private final SSLSocket sslSocket;
    private final Socket baseSocket;
    private final AtomicReference<InputStream> inputStreamRef;
    private final AtomicReference<OutputStream> outputStreamRef;

    /**
     * @since 5.3
     */
    public SocketHolder(final SSLSocket sslSocket, final Socket baseSocket) {
        this.sslSocket = Args.notNull(sslSocket, "SSL Socket");
        this.baseSocket = Args.notNull(baseSocket, "Socket");
        this.inputStreamRef = new AtomicReference<>();
        this.outputStreamRef = new AtomicReference<>();
    }

    public SocketHolder(final Socket socket) {
        this.baseSocket = Args.notNull(socket, "Socket");
        this.sslSocket = null;
        this.inputStreamRef = new AtomicReference<>();
        this.outputStreamRef = new AtomicReference<>();
    }

    public final Socket getSocket() {
        return sslSocket != null ? sslSocket : baseSocket;
    }

    /**
     * @since 5.3
     */
    @Internal
    public Socket getBaseSocket() {
        return baseSocket;
    }

    /**
     * @since 5.3
     */
    @Internal
    public SSLSocket getSSLSocket() {
        return sslSocket;
    }

    public final InputStream getInputStream() throws IOException {
        InputStream local = inputStreamRef.get();
        if (local != null) {
            return local;
        }
        local = getInputStream(getSocket());
        if (inputStreamRef.compareAndSet(null, local)) {
            return local;
        }
        return inputStreamRef.get();
    }

    protected InputStream getInputStream(final Socket socket) throws IOException {
        return socket.getInputStream();
    }

    protected OutputStream getOutputStream(final Socket socket) throws IOException {
        return socket.getOutputStream();
    }

    public final OutputStream getOutputStream() throws IOException {
        OutputStream local = outputStreamRef.get();
        if (local != null) {
            return local;
        }
        local = getOutputStream(getSocket());
        if (outputStreamRef.compareAndSet(null, local)) {
            return local;
        }
        return outputStreamRef.get();
    }

    @Override
    public String toString() {
        return "SocketHolder{" +
                "sslSocket=" + sslSocket +
                ", baseSocket=" + baseSocket +
                '}';
    }

}
