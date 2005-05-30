package org.apache.http.mockup;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import org.apache.http.impl.io.NIOHttpDataReceiver;

/**
 * {@link HttpDataInputStream} mockup implementation.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class HttpDataReceiverMockup extends NIOHttpDataReceiver {

    public static int BUFFER_SIZE = 16;
    
    private final ReadableByteChannel channel;
    
    public HttpDataReceiverMockup(final ReadableByteChannel channel, int buffersize) {
        super();
        if (channel == null) {
            throw new IllegalArgumentException("Channel may not be null");
        }
        this.channel = channel;
        initBuffer(buffersize);
    }

    public HttpDataReceiverMockup(final byte[] bytes) {
        this(bytes, BUFFER_SIZE);
    }

    public HttpDataReceiverMockup(final byte[] bytes, int buffersize) {
        super();
        this.channel = Channels.newChannel(new ByteArrayInputStream(bytes));
        initBuffer(buffersize);
    }

    public HttpDataReceiverMockup(final String s, final String charset, int buffersize) 
        throws UnsupportedEncodingException {
        this(s.getBytes(charset), buffersize);
    }
    
    public HttpDataReceiverMockup(final String s, final String charset) 
        throws UnsupportedEncodingException {
        this(s.getBytes(charset));
    
    }
    
    protected int readFromChannel(final ByteBuffer dst) throws IOException {
        if (dst == null) {
            throw new IllegalArgumentException("Byte buffer may not be null");
        }
        return this.channel.read(dst);
    }
  
    public boolean isDataAvailable(int timeout) throws IOException {
        return this.channel.isOpen();
    }    
    
}
