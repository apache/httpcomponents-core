package org.apache.http.mockup;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import org.apache.http.impl.NIOHttpDataReceiver;

/**
 * {@link HttpDataInputStream} mockup implementation.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class HttpDataReceiverMockup extends NIOHttpDataReceiver {

    public static int BUFFER_SIZE = 16;
    
    public HttpDataReceiverMockup(final ReadableByteChannel channel, int buffersize) {
        super();
        init(channel, buffersize);
    }

    public HttpDataReceiverMockup(final byte[] bytes) {
        super();
        init(Channels.newChannel(
                new ByteArrayInputStream(bytes)),
                BUFFER_SIZE);
    }

    public HttpDataReceiverMockup(final String s, final String charset) 
        throws UnsupportedEncodingException {
        this(s.getBytes(charset));
    }
    
}
