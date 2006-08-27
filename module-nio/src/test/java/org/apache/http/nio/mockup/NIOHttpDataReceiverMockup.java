package org.apache.http.nio.mockup;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import org.apache.http.nio.impl.NIOHttpDataReceiver;

/**
 * {@link HttpDataInputStream} mockup implementation.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class NIOHttpDataReceiverMockup extends NIOHttpDataReceiver {

    public static int BUFFER_SIZE = 16;
    
    private final ReadableByteChannel channel;
    
    public NIOHttpDataReceiverMockup(final ReadableByteChannel channel, int buffersize) {
        super();
        if (channel == null) {
            throw new IllegalArgumentException("Channel may not be null");
        }
        this.channel = channel;
        initBuffer(buffersize);
    }

    public NIOHttpDataReceiverMockup(final byte[] bytes) {
        this(bytes, BUFFER_SIZE);
    }

    public NIOHttpDataReceiverMockup(final byte[] bytes, int buffersize) {
        super();
        this.channel = Channels.newChannel(new ByteArrayInputStream(bytes));
        initBuffer(buffersize);
    }

    public NIOHttpDataReceiverMockup(final String s, final String charset, int buffersize) 
        throws UnsupportedEncodingException {
        this(s.getBytes(charset), buffersize);
    }
    
    public NIOHttpDataReceiverMockup(final String s, final String charset) 
        throws UnsupportedEncodingException {
        this(s.getBytes(charset));
    
    }
    
    protected int fillBuffer() throws IOException {
        return this.channel.read(getBuffer());
    }
  
    public boolean isDataAvailable(int timeout) throws IOException {
        return this.channel.isOpen();
    }    
    
}
