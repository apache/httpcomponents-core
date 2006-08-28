package org.apache.http.nio.impl;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.http.io.CharArrayBuffer;
import org.apache.http.nio.EventMask;
import org.apache.http.nio.IOProducer;
import org.apache.http.nio.IOSession;
import org.apache.http.params.HttpParams;

public class AsyncHttpDataTransmitter extends NIOHttpDataTransmitter implements IOProducer {
    
    private final IOSession session;
    private final Object mutex;
    
    private volatile boolean closed = false;
    private volatile IOException exception = null;
    
    public AsyncHttpDataTransmitter(final IOSession session, int buffersize) {
        super();
        if (session == null) {
            throw new IllegalArgumentException("I/O session may not be null");
        }
        this.session = session;
        this.mutex = new Object();
        initBuffer(buffersize);
    }

    public void produceOutput() throws IOException {
        synchronized (this.mutex) {
            ByteBuffer buffer = getBuffer();
            this.session.channel().write(buffer);
            if (!buffer.hasRemaining()) {
                this.session.clearEvent(EventMask.WRITE);
                this.mutex.notifyAll();            
            }
        }
    }
    
    protected void flushBuffer() throws IOException {
        if (this.closed) {
            return;
        }
        this.session.setEvent(EventMask.WRITE);
        synchronized (this.mutex) {
            ByteBuffer buffer = getBuffer();
            try {
                while (buffer.hasRemaining() && !this.closed) {
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

    public void flush() throws IOException {
        synchronized (this.mutex) {
            super.flush();
        }
    }

    public void reset(final HttpParams params) {
        synchronized (this.mutex) {
            super.reset(params);
        }
    }

    public void write(final byte[] b, int off, int len) throws IOException {
        synchronized (this.mutex) {
            super.write(b, off, len);
        }
    }

    public void write(final byte[] b) throws IOException {
        synchronized (this.mutex) {
            super.write(b);
        }
    }

    public void write(int b) throws IOException {
        synchronized (this.mutex) {
            super.write(b);
        }
    }

    public void writeLine(final CharArrayBuffer buffer) throws IOException {
        synchronized (this.mutex) {
            super.writeLine(buffer);
        }
    }

    public void writeLine(final String s) throws IOException {
        synchronized (this.mutex) {
            super.writeLine(s);
        }
    }

}
