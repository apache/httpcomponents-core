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
import java.io.InputStream;

import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.HttpLineParser;

/**
 * <p>Old IO Compatibility wrapper</p>
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 */
public class InputStreamHttpDataReceiver implements HttpDataReceiver {

    private final InputStream instream;
    
    private String charset = "US-ASCII";
    
    public InputStreamHttpDataReceiver(final InputStream instream) {
        super();
        if (instream == null) {
            throw new IllegalArgumentException("Input stream may not be null");
        }
        this.instream = instream;
    }
    
    public InputStream getInputStream() {
        return this.instream;
    }
    
    public boolean isDataAvailable(int timeout) throws IOException {
        return this.instream.available() > 0;
    }
    
    public int read() throws IOException {
        return this.instream.read();
    }
    
    public int read(final byte[] b, int off, int len) throws IOException {
        return this.instream.read(b, off, len);
    }
    
    public int read(final byte[] b) throws IOException {
        return this.instream.read(b);
    }
    
    public String readLine() throws IOException {
        return HttpLineParser.readLine(this.instream, this.charset);
    }
    
    public void reset(final HttpParams params) {
        HttpProtocolParams protocolParams = new HttpProtocolParams(params);
        this.charset = protocolParams.getHttpElementCharset(); 
    }
    
}
