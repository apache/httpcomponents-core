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

package org.apache.http.impl.io;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.http.io.HttpDataTransmitter;

/**
 * A stream for writing to a {@link HttpDataTransmitter HttpDataTransmitter}.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class HttpDataOutputStream extends OutputStream {
    
    private final HttpDataTransmitter datatransmitter;
    
    private boolean closed = false;
    
    public HttpDataOutputStream(final HttpDataTransmitter datatransmitter) {
        super();
        if (datatransmitter == null) {
            throw new IllegalArgumentException("HTTP data transmitter may not be null");
        }
        this.datatransmitter = datatransmitter;
    }
    
    public void close() throws IOException {
        if (!this.closed) {
            this.closed = true;
            this.datatransmitter.flush();
        }
    }

    private void assertNotClosed() {
        if (this.closed) {
            throw new IllegalStateException("Stream closed"); 
        }
    }
    
    public void flush() throws IOException {
        assertNotClosed();
        this.datatransmitter.flush();
    }
    
    public void write(final byte[] b, int off, int len) throws IOException {
        assertNotClosed();
        this.datatransmitter.write(b, off, len);
    }
    
    public void write(int b) throws IOException {
        assertNotClosed();
        this.datatransmitter.write(b);
    }
}
