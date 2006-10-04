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
package org.apache.http.nio.impl;

import java.io.IOException;

import org.apache.http.HttpHost;
import org.apache.http.impl.AbstractHttpClientConnection;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.entity.DefaultEntityDeserializer;
import org.apache.http.impl.entity.DefaultEntitySerializer;
import org.apache.http.nio.IOConsumer;
import org.apache.http.nio.IOProducer;
import org.apache.http.nio.IOSession;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

public class AsyncHttpClientConnection extends AbstractHttpClientConnection {

    private final HttpHost targetHost;
    private final IOSession session;
    private final IOProducer ioProducer;
    private final IOConsumer ioConsumer;
    
    public AsyncHttpClientConnection(
            final HttpHost targetHost, 
            final IOSession session, 
            final HttpParams params) {
        super();
        if (targetHost == null) {
            throw new IllegalArgumentException("Target host may not be null");
        }
        if (session == null) {
            throw new IllegalArgumentException("IO session may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.session = session;
        this.targetHost = targetHost;
        int buffersize = HttpConnectionParams.getSocketBufferSize(params);
        
        AsyncHttpDataReceiver datareceiver = new AsyncHttpDataReceiver(
                session, buffersize);
        datareceiver.reset(params);
        AsyncHttpDataTransmitter datatransmitter = new AsyncHttpDataTransmitter(
                session, buffersize);
        datatransmitter.reset(params);

        this.ioConsumer = datareceiver;
        this.ioProducer = datatransmitter;
        
        setHttpDataReceiver(datareceiver);
        setHttpDataTransmitter(datatransmitter);
        setResponseFactory(new DefaultHttpResponseFactory());
        setEntitySerializer(new DefaultEntitySerializer());
        setEntityDeserializer(new DefaultEntityDeserializer());
    }

    public HttpHost getTargetHost() {
        return this.targetHost;
    }
    
    public IOConsumer getIOConsumer() {
        return this.ioConsumer;
    }

    public IOProducer getIOProducer() {
        return this.ioProducer;
    }

    protected void assertOpen() throws IllegalStateException {
        if (this.session.isClosed()) {
            throw new IllegalStateException("Connection is closed");
        }
    }

    public void close() throws IOException {
        flush();
        shutdown();
    }

    public boolean isOpen() {
        return !this.session.isClosed();
    }

    public boolean isStale() {
        return this.session.isClosed();
    }

    public void shutdown() throws IOException {
        this.ioProducer.shutdown();
        this.ioConsumer.shutdown();
        this.session.close();
    }

}
