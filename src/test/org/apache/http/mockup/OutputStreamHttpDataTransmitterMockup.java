package org.apache.http.mockup;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import org.apache.http.impl.io.AbstractHttpDataTransmitter;

/**
 * {@link HttpDataTransmitter} mockup implementation.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class OutputStreamHttpDataTransmitterMockup extends AbstractHttpDataTransmitter {

    private ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    public static int BUFFER_SIZE = 16;
    
    public OutputStreamHttpDataTransmitterMockup(final OutputStream outstream, int buffersize) {
        super();
        init(outstream, buffersize);
    }

    public OutputStreamHttpDataTransmitterMockup(final ByteArrayOutputStream buffer) {
        this(buffer, BUFFER_SIZE);
        this.buffer = buffer;
    }

    public OutputStreamHttpDataTransmitterMockup() {
        this(new ByteArrayOutputStream());
    }

    public byte[] getData() {
        if (this.buffer != null) {
            return this.buffer.toByteArray();
        } else {
            return new byte[] {};
        }
    }
    
}
