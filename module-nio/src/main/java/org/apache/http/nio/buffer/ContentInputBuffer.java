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
package org.apache.http.nio.buffer;

import java.io.IOException;

import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentIOControl;

public class ContentInputBuffer extends ExpandableBuffer {

    private final ContentIOControl ioctrl;
    private final Object mutex;
    
    private volatile boolean shutdown = false;
    private volatile boolean endOfStream = false;
    private volatile IOException exception = null;
    
    public ContentInputBuffer(int buffersize, final ContentIOControl ioctrl) {
        super(buffersize);
        if (ioctrl == null) {
            throw new IllegalArgumentException("I/O content control may not be null");
        }
        this.ioctrl = ioctrl;
        this.mutex = new Object();
    }
    
    public void reset() {
        if (this.shutdown) {
            return;
        }
        synchronized (this.mutex) {
            clear();
            this.endOfStream = false;
        }
    }
    
    public void consumeContent(final ContentDecoder decoder) throws IOException {
        if (this.shutdown) {
            return;
        }
        synchronized (this.mutex) {
            setInputMode();
            int total = 0;
            int bytesRead = 0;
            while ((bytesRead = decoder.read(this.buffer)) > 0) {
                total =+ bytesRead;
            }
            if (bytesRead == -1 || decoder.isCompleted()) {
                this.endOfStream = true;
            }
            if (total > 0) {
                this.ioctrl.suspendInput();
                this.mutex.notifyAll();            
            }
        }
    }
    
    protected void waitForData() throws IOException {
        synchronized (this.mutex) {
            try {
                while (!hasData() && !this.endOfStream && !this.shutdown) {
                    this.ioctrl.requestInput();
                    this.mutex.wait();
                }
                IOException ex = this.exception;
                if (ex != null) {
                    throw ex;
                }
            } catch (InterruptedException ex) {
                throw new IOException("Interrupted while waiting for more data");
            }
        }
    }

    public void shutdown() {
        if (this.shutdown) {
            return;
        }
        this.shutdown = true;
        synchronized (this.mutex) {
            this.mutex.notifyAll();
        }
    }

    public void shutdown(final IOException exception) {
        this.exception = exception;
        shutdown();
    }
    
    protected boolean isShutdown() {
        return this.shutdown;
    }

    protected boolean isEndOfStream() {
        return this.shutdown || (!hasData() && this.endOfStream);
    }

    public int read() throws IOException {
        if (this.shutdown) {
            return -1;
        }
        synchronized (this.mutex) {
            if (!hasData()) {
                waitForData();
            }
            if (isEndOfStream()) {
                return -1; 
            }
            return this.buffer.get() & 0xff;
        }
    }

    public int read(final byte[] b, int off, int len) throws IOException {
        if (this.shutdown) {
            return -1;
        }
        if (b == null) {
            return 0;
        }
        synchronized (this.mutex) {
            if (!hasData()) {
                waitForData();
            }
            if (isEndOfStream()) {
                return -1; 
            }
            setOutputMode();
            int chunk = len;
            if (chunk > this.buffer.remaining()) {
                chunk = this.buffer.remaining();
            }
            this.buffer.get(b, off, chunk);
            return chunk;
        }
    }

    public int read(final byte[] b) throws IOException {
        if (this.shutdown) {
            return -1;
        }
        if (b == null) {
            return 0;
        }
        return read(b, 0, b.length);
    }

}
