package org.apache.http.mockup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

import org.apache.http.impl.io.NIOHttpDataTransmitter;

/**
 * {@link HttpDataTransmitter} mockup implementation.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class HttpDataTransmitterMockup extends NIOHttpDataTransmitter {

    public static int BUFFER_SIZE = 16;
    
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    
    private final WritableByteChannel channel;
    
    public HttpDataTransmitterMockup(final WritableByteChannel channel, int buffersize) {
        super();
        if (channel == null) {
            throw new IllegalArgumentException("Channel may not be null");
        }
        this.channel = channel;
        initBuffer(buffersize);
    }

    public HttpDataTransmitterMockup() {
        super();
        this.channel = Channels.newChannel(this.buffer);
        initBuffer(BUFFER_SIZE);
    }

    protected void writeToChannel(final ByteBuffer src) throws IOException {
        this.channel.write(src);
    }
    
    public byte[] getData() {
        return this.buffer.toByteArray();
    }
    
}
