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

import org.apache.http.io.CharArrayBuffer;
import org.apache.http.nio.EventMask;
import org.apache.http.nio.IOConsumer;
import org.apache.http.nio.IOSession;
import org.apache.http.params.HttpParams;

public class AsyncHttpDataReceiver 
    extends AbstractSessionDataReceiver implements IOConsumer {

    private final IOSession session;
    private final Object mutex;
    
    private volatile boolean closed = false;
    private volatile IOException exception = null;
    
    public AsyncHttpDataReceiver(
            final IOSession session, 
            final SessionInputBuffer buffer) {
        super(buffer);
        if (session == null) {
            throw new IllegalArgumentException("I/O session may not be null");
        }
        this.session = session;
        this.mutex = new Object();
    }
    
    public void consumeInput() throws IOException {
        synchronized (this.mutex) {
            int noRead = getBuffer().fill(this.session.channel());
            if (noRead == -1) {
                this.closed = true;
            }
            if (noRead != 0) {
                this.session.clearEvent(EventMask.READ);
                this.mutex.notifyAll();            
            }
        }
    }
    
    protected int waitForData() throws IOException {
        if (this.closed) {
            return -1;
        }
        synchronized (this.mutex) {
            try {
                while (!getBuffer().hasData() && !this.closed) {
                    this.session.setEvent(EventMask.READ);
                    this.mutex.wait();
                }
                
                IOException ex = this.exception;
                if (ex != null) {
                    throw ex;
                }
                
                if (this.closed) {
                    return -1;
                } else {
                    return getBuffer().length();
                }
                
            } catch (InterruptedException ex) {
                throw new IOException("Interrupted while waiting for more data");
            }
        }
    }

    public boolean isDataAvailable(int timeout) throws IOException {
        if (this.closed) {
            return false;
        }
        synchronized (this.mutex) {
            if (getBuffer().hasData()) {
                return true;
            }
            try {
                this.mutex.wait(timeout);
            } catch (InterruptedException ex) {
                throw new IOException("Interrupted while waiting for more data");
            }
            if (this.closed) {
                return false;
            }
            return getBuffer().hasData();
        }
    }
    
    public void shutdown() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        synchronized (this.mutex) {
            this.mutex.notifyAll();
        }
    }

    public void shutdown(final IOException exception) {
        this.exception = exception;
        shutdown();
    }

    public void reset(final HttpParams params) {
        synchronized (this.mutex) {
            super.reset(params);
        }
    }
    
    public int read() throws IOException {
        synchronized (this.mutex) {
            return super.read();
        }
    }

    public int read(final byte[] b, int off, int len) throws IOException {
        synchronized (this.mutex) {
            return super.read(b, off, len);
        }
    }

    public int read(final byte[] b) throws IOException {
        synchronized (this.mutex) {
            return super.read(b);
        }
    }

    public String readLine() throws IOException {
        synchronized (this.mutex) {
            return super.readLine();
        }
    }

    public int readLine(final CharArrayBuffer charbuffer) throws IOException {
        synchronized (this.mutex) {
            return super.readLine(charbuffer);
        }
    }

}
