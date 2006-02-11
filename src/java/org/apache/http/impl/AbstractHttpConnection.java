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

package org.apache.http.impl;

import java.io.IOException;
import java.net.Socket;

import org.apache.http.HttpConnection;
import org.apache.http.impl.io.SocketHttpDataReceiver;
import org.apache.http.impl.io.SocketHttpDataTransmitter;
import org.apache.http.io.HttpDataReceiver;
import org.apache.http.io.HttpDataReceiverFactory;
import org.apache.http.io.HttpDataTransmitter;
import org.apache.http.io.HttpDataTransmitterFactory;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

/**
 * Abstract base class for HTTP connections on the client and server side.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
abstract class AbstractHttpConnection implements HttpConnection {

	/* 
	 * I/O operations may not be performed if this flag is set to false 
	 * All methods must call #assertOpen() to ensure the connection 
	 * is open prior to performing any I/O
	 */ 
	protected volatile boolean open;
    protected Socket socket = null;
    protected HttpDataTransmitter datatransmitter = null;
    protected HttpDataReceiver datareceiver = null;
    
    /*
     * Dependent interfaces
     */
    private HttpDataTransmitterFactory trxfactory = null; 
    private HttpDataReceiverFactory rcvfactory = null; 
    
    protected AbstractHttpConnection() {
        super();
    }
    
    public void setReceiverFactory(final HttpDataReceiverFactory rcvfactory) {
        if (rcvfactory == null) {
            throw new IllegalArgumentException("Factory may not be null");
        }
        this.rcvfactory = rcvfactory;
    }
    
    public void setTransmitterFactory(final HttpDataTransmitterFactory trxfactory) {
        if (trxfactory == null) {
            throw new IllegalArgumentException("Factory may not be null");
        }
        this.trxfactory = trxfactory;
    }

    protected void assertNotOpen() {
        if (this.open) {
            throw new IllegalStateException("Connection is already open");
        }
    }
    
    protected void assertOpen() {
        if (!this.open) {
            throw new IllegalStateException("Connection is not open");
        }
    }
    
    protected void bind(final Socket socket, final HttpParams params) throws IOException {
        if (socket == null) {
            throw new IllegalArgumentException("Socket may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        socket.setTcpNoDelay(HttpConnectionParams.getTcpNoDelay(params));
        socket.setSoTimeout(HttpConnectionParams.getSoTimeout(params));
        
        int linger = HttpConnectionParams.getLinger(params);
        if (linger >= 0) {
            socket.setSoLinger(linger > 0, linger);
        }
        
        int buffersize = HttpConnectionParams.getSocketBufferSize(params);
        assertNotOpen();
        this.open = true;
        this.socket = socket;
        if (this.trxfactory != null) {
            this.datatransmitter = this.trxfactory.create(this.socket); 
        } else {
            this.datatransmitter = new SocketHttpDataTransmitter(this.socket, buffersize);
        }
        if (this.rcvfactory != null) {
            this.datareceiver = this.rcvfactory.create(this.socket); 
        } else {
            this.datareceiver = new SocketHttpDataReceiver(this.socket, buffersize);
        }
    }

    public boolean isOpen() {
        return this.open;
    }

    public void shutdown() throws IOException {
        this.open = false;
        Socket tmpsocket = this.socket;
        if (tmpsocket != null) {
            tmpsocket.close();
        }
    }
    
    public void close() throws IOException {
        if (!this.open) {
            return;
        }
        this.open = false;
        this.datatransmitter.flush();
        try {
            this.socket.shutdownOutput();
        } catch (IOException ignore) {
        }
        try {
            this.socket.shutdownInput();
        } catch (IOException ignore) {
        }
        this.socket.close();
    }
    
    public boolean isStale() {
        assertOpen();
        try {
            this.datareceiver.isDataAvailable(1);
            return false;
        } catch (IOException ex) {
            return true;
        }
    }
        
}
