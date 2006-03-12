package org.apache.http.mockup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.http.entity.AbstractHttpEntity;

/**
 * {@link AbstractHttpEntity} mockup implementation.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class HttpEntityMockup extends AbstractHttpEntity {

    private boolean stream;
    
    public InputStream getContent() throws IOException, IllegalStateException {
        return null;
    }

    public long getContentLength() {
        return 0;
    }

    public boolean isRepeatable() {
        return false;
    }

    public void setStreaming(final boolean b) {
        this.stream = b;
    }

    public boolean isStreaming() {
        return this.stream;
    }

    public void writeTo(OutputStream outstream) throws IOException {
    }    
    
}
