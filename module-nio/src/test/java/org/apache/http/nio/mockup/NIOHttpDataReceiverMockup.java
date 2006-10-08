package org.apache.http.nio.mockup;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import org.apache.http.nio.impl.AbstractSessionDataReceiver;
import org.apache.http.nio.impl.SessionInputBuffer;

/**
 * {@link HttpDataInputStream} mockup implementation.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class NIOHttpDataReceiverMockup extends AbstractSessionDataReceiver {

    public static int BUFFER_SIZE = 16;
    
    private final ReadableByteChannel channel;
    
    public NIOHttpDataReceiverMockup(
            final ReadableByteChannel channel, 
            int buffersize, 
            int linebuffersize) {
        super(new SessionInputBuffer(buffersize, linebuffersize));
        if (channel == null) {
            throw new IllegalArgumentException("Channel may not be null");
        }
        this.channel = channel;
    }

    public NIOHttpDataReceiverMockup(final byte[] bytes) {
        this(bytes, BUFFER_SIZE, BUFFER_SIZE);
    }

    public NIOHttpDataReceiverMockup(
            final byte[] bytes, 
            int buffersize, 
            int linebuffersize) {
        super(new SessionInputBuffer(buffersize, linebuffersize));
        this.channel = Channels.newChannel(new ByteArrayInputStream(bytes));
    }

    public NIOHttpDataReceiverMockup(final String s, final String charset, int buffersize) 
        throws UnsupportedEncodingException {
        this(s.getBytes(charset), buffersize, buffersize);
    }
    
    public NIOHttpDataReceiverMockup(final String s, final String charset) 
        throws UnsupportedEncodingException {
        this(s.getBytes(charset));
    
    }
    
    protected int waitForData() throws IOException {
        return getBuffer().fill(this.channel);
    }
  
    public boolean isDataAvailable(int timeout) throws IOException {
        return this.channel.isOpen();
    }    
    
}
