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

import org.apache.http.impl.AbstractHttpClientConnection;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.entity.DefaultEntityDeserializer;
import org.apache.http.impl.entity.DefaultEntitySerializer;
import org.apache.http.impl.entity.LaxContentLengthStrategy;
import org.apache.http.impl.entity.StrictContentLengthStrategy;
import org.apache.http.nio.IOConsumer;
import org.apache.http.nio.IOProducer;
import org.apache.http.nio.IOSession;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

public class AsyncHttpClientConnection extends AbstractHttpClientConnection {

    private final IOSession session;
    private final IOProducer ioProducer;
    private final IOConsumer ioConsumer;
    
    public AsyncHttpClientConnection(
            final IOSession session, 
            final HttpParams params) {
        super();
        if (session == null) {
            throw new IllegalArgumentException("IO session may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.session = session;
        int buffersize = HttpConnectionParams.getSocketBufferSize(params);
        int linebuffersize = buffersize;
        if (linebuffersize > 512) {
            linebuffersize = 512;
        }
        
        SessionInputBuffer inbuf = new SessionInputBuffer(buffersize, linebuffersize); 
        AsyncHttpDataReceiver datareceiver = new AsyncHttpDataReceiver(session, inbuf);
        datareceiver.reset(params);
        
        SessionOutputBuffer outbuf = new SessionOutputBuffer(buffersize, linebuffersize); 
        AsyncHttpDataTransmitter datatransmitter = new AsyncHttpDataTransmitter(session, outbuf);
        datatransmitter.reset(params);

        this.ioConsumer = datareceiver;
        this.ioProducer = datatransmitter;
        
        setHttpDataReceiver(datareceiver);
        setHttpDataTransmitter(datatransmitter);
        setResponseFactory(new DefaultHttpResponseFactory());
        setEntitySerializer(new DefaultEntitySerializer(
                new StrictContentLengthStrategy()));
        setEntityDeserializer(new DefaultEntityDeserializer(
                new LaxContentLengthStrategy()));
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
