package org.apache.http.mockup;

import java.io.ByteArrayOutputStream;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

import org.apache.http.impl.NIOHttpDataTransmitter;

/**
 * {@link HttpDataTransmitter} mockup implementation.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class HttpDataTransmitterMockup extends NIOHttpDataTransmitter {

    public static int BUFFER_SIZE = 16;
    
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    
    public HttpDataTransmitterMockup(final WritableByteChannel channel, int buffersize) {
        super();
        init(channel, buffersize);
    }

    public HttpDataTransmitterMockup() {
        super();
        init(Channels.newChannel(this.buffer), BUFFER_SIZE);
    }

    public byte[] getData() {
        return this.buffer.toByteArray();
    }
    
}
