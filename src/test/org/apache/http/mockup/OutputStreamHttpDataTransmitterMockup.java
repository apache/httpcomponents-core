package org.apache.http.mockup;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import org.apache.http.io.OutputStreamHttpDataTransmitter;

/**
 * {@link HttpDataTransmitter} mockup implementation.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class OutputStreamHttpDataTransmitterMockup extends OutputStreamHttpDataTransmitter {

    private ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    public static int BUFFER_SIZE = 16;
    
    public OutputStreamHttpDataTransmitterMockup(final OutputStream outstream, int buffersize) {
        super(outstream);
        if (buffersize <= 0) {
            throw new IllegalArgumentException("Buffer size may not be negative or zero");
        }
        initBuffer(buffersize);
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
