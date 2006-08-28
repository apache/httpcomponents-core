package org.apache.http.nio.impl;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.http.io.CharArrayBuffer;
import org.apache.http.nio.EventMask;
import org.apache.http.nio.IOConsumer;
import org.apache.http.nio.IOSession;
import org.apache.http.params.HttpParams;

public class AsyncHttpDataReceiver extends NIOHttpDataReceiver implements IOConsumer {

    private final IOSession session;
    private final Object mutex;
    
    private volatile boolean closed = false;
    private volatile IOException exception = null;
    
    public AsyncHttpDataReceiver(final IOSession session, int buffersize) {
        super();
        if (session == null) {
            throw new IllegalArgumentException("I/O session may not be null");
        }
        this.session = session;
        this.mutex = new Object();
        int linebuffersize = buffersize;
        if (linebuffersize > 512) {
            linebuffersize = 512;
        }
        initBuffer(buffersize, linebuffersize);
    }
    
    public void consumeInput() throws IOException {
        synchronized (this.mutex) {
            int noRead = this.session.channel().read(getBuffer());
            if (noRead == -1) {
                this.closed = true;
            }
            if (noRead != 0) {
                this.session.clearEvent(EventMask.READ);
                this.mutex.notifyAll();            
            }
        }
    }
    
    protected int fillBuffer() throws IOException {
        if (this.closed) {
            return -1;
        }
        synchronized (this.mutex) {
            ByteBuffer buffer = getBuffer();
            try {
                while (buffer.position() == 0 && !this.closed) {
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
                    return buffer.position();
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
            ByteBuffer buffer = getBuffer();
            if (buffer.position() > 0) {
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
            return buffer.position() > 0;
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
