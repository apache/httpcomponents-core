package org.apache.http.nio.mockup;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

import org.apache.http.util.EncodingUtils;

public class ReadableByteChannelMockup implements ReadableByteChannel {

    private final String[] chunks;
    private final String charset;
    
    private int chunkCount = 0;
    
    private ByteBuffer currentChunk;
    private boolean closed = false;
    
    public ReadableByteChannelMockup(final String[] chunks, final String charset) {
        super();
        this.chunks = chunks;
        this.charset = charset;
    }

    private void prepareChunk() {
        if (this.currentChunk == null || !this.currentChunk.hasRemaining()) {
            if (this.chunkCount < this.chunks.length) {
                String s = this.chunks[this.chunkCount];
                this.chunkCount++;
                this.currentChunk = ByteBuffer.wrap(EncodingUtils.getBytes(s, this.charset));
            } else {
                this.closed = true;
            }
        }
    }
    
    public int read(final ByteBuffer dst) throws IOException {
        prepareChunk();
        if (this.closed) {
            return -1;
        }
        int i = 0;
        while (dst.hasRemaining() && this.currentChunk.hasRemaining()) {
            dst.put(this.currentChunk.get());
            i++;
        }
        return i;
    }

    public void close() throws IOException {
        this.closed = true;
    }

    public boolean isOpen() {
        return !this.closed;
    }
    
    
}
