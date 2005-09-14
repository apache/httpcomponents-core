package org.apache.http.mockup;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import org.apache.http.impl.io.InputStreamHttpDataReceiver;

/**
 * {@link HttpDataInputStream} mockup implementation.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class InputStreamHttpDataReceiverMockup extends InputStreamHttpDataReceiver {

    public static int BUFFER_SIZE = 16;
    
    public InputStreamHttpDataReceiverMockup(final InputStream instream, int buffersize) {
        super();
        init(instream, buffersize);
    }

    public InputStreamHttpDataReceiverMockup(final byte[] bytes) {
        this(bytes, BUFFER_SIZE);
    }

    public InputStreamHttpDataReceiverMockup(final byte[] bytes, int buffersize) {
        this(new ByteArrayInputStream(bytes), buffersize);
    }

    public InputStreamHttpDataReceiverMockup(final String s, final String charset, int buffersize) 
        throws UnsupportedEncodingException {
        this(s.getBytes(charset), buffersize);
    }
    
    public InputStreamHttpDataReceiverMockup(final String s, final String charset) 
        throws UnsupportedEncodingException {
        this(s.getBytes(charset));
    
    }
    
}
