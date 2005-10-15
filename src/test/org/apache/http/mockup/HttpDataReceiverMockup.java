package org.apache.http.mockup;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import org.apache.http.impl.io.AbstractHttpDataReceiver;

/**
 * {@link HttpDataInputStream} mockup implementation.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class HttpDataReceiverMockup extends AbstractHttpDataReceiver {

    public static int BUFFER_SIZE = 16;
    
    public HttpDataReceiverMockup(final InputStream instream, int buffersize) {
        super();
        init(instream, buffersize);
    }

    public HttpDataReceiverMockup(final byte[] bytes) {
        this(bytes, BUFFER_SIZE);
    }

    public HttpDataReceiverMockup(final byte[] bytes, int buffersize) {
        this(new ByteArrayInputStream(bytes), buffersize);
    }

    public HttpDataReceiverMockup(final String s, final String charset, int buffersize) 
        throws UnsupportedEncodingException {
        this(s.getBytes(charset), buffersize);
    }
    
    public HttpDataReceiverMockup(final String s, final String charset) 
        throws UnsupportedEncodingException {
        this(s.getBytes(charset));
    
    }
    
    public boolean isDataAvailable(int timeout) throws IOException {
        return true;
    }
    
}
