package org.apache.http.nio.mockup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

import org.apache.http.nio.impl.AbstractSessionDataTransmitter;
import org.apache.http.nio.impl.SessionOutputBuffer;

/**
 * {@link HttpDataTransmitter} mockup implementation.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class NIOHttpDataTransmitterMockup extends AbstractSessionDataTransmitter {

    public static int BUFFER_SIZE = 16;
    
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    
    private final WritableByteChannel channel;
    
    public NIOHttpDataTransmitterMockup(
            final WritableByteChannel channel, 
            int buffersize,
            int linebuffersize) {
        super(new SessionOutputBuffer(buffersize, linebuffersize));
        if (channel == null) {
            throw new IllegalArgumentException("Channel may not be null");
        }
        this.channel = channel;
    }

    public NIOHttpDataTransmitterMockup(
            int buffersize,
            int linebuffersize) {
        super(new SessionOutputBuffer(buffersize, linebuffersize));
        this.channel = Channels.newChannel(this.buffer);
    }

    public NIOHttpDataTransmitterMockup() {
        super(new SessionOutputBuffer(BUFFER_SIZE, BUFFER_SIZE));
        this.channel = Channels.newChannel(this.buffer);
    }

    protected void flushBuffer() throws IOException {
        getBuffer().flush(this.channel);
    }
    
    public byte[] getData() {
        return this.buffer.toByteArray();
    }
    
}
