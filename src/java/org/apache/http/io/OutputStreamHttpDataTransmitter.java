/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 2002-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.io;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

/**
 * <p>Old IO Compatibility wrapper</p>
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 */
public class OutputStreamHttpDataTransmitter implements HttpDataTransmitter {

    private static final int CR = 13;
    private static final int LF = 10;
    private static final byte[] CRLF = new byte[] {CR, LF};

    private final OutputStream outstream;
    
    private String charset = "US-ASCII";
    
    public OutputStreamHttpDataTransmitter(final OutputStream outstream) {
        super();
        if (outstream == null) {
            throw new IllegalArgumentException("Input stream may not be null");
        }
        this.outstream = outstream;
    }

    public OutputStream getOutputStream() {
        return this.outstream;
    }
    
    public void flush() throws IOException {
        this.outstream.flush();
    }
    
    public void write(final byte[] b, int off, int len) throws IOException {
        this.outstream.write(b, off, len);
    }
    
    public void write(final byte[] b) throws IOException {
        this.outstream.write(b);
    }
    
    public void write(int b) throws IOException {
        this.outstream.write(b);
    }
    
    public void writeLine(final String s) throws IOException {
        if (s == null) {
            return;
        }
        this.outstream.write(s.getBytes(this.charset));
        this.outstream.write(CRLF);
    }
    
    public void reset(final HttpParams params) {
        HttpProtocolParams protocolParams = new HttpProtocolParams(params);
        this.charset = protocolParams.getHttpElementCharset(); 
    }
    
}
