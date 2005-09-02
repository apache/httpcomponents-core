package org.apache.http.mockup;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import org.apache.http.io.InputStreamHttpDataReceiver;

/**
 * {@link HttpDataInputStream} mockup implementation.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class InputStreamHttpDataReceiverMockup extends InputStreamHttpDataReceiver {

    public static int BUFFER_SIZE = 16;
    
    public InputStreamHttpDataReceiverMockup(final InputStream instream, int buffersize) {
        super(instream);
        if (buffersize <= 0) {
            throw new IllegalArgumentException("Buffer size may not be negative or zero");
        }
        initBuffer(buffersize);
    }

    public InputStreamHttpDataReceiverMockup(final byte[] bytes) {
        this(bytes, BUFFER_SIZE);
    }

    public InputStreamHttpDataReceiverMockup(final byte[] bytes, int buffersize) {
        super(new ByteArrayInputStream(bytes));
        initBuffer(buffersize);
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
