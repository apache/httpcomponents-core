/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2004 The Apache Software Foundation
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

package org.apache.http.entity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.http.HttpEntity;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class InputStreamEntity implements HttpEntity {

    private final static String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private final static int BUFFER_SIZE = 2048;

    private final InputStream content;
    private final long length;
    private String contentType = DEFAULT_CONTENT_TYPE;
    private String contentEncoding = null;
    private boolean chunked = false;

    public InputStreamEntity(final InputStream instream, long length) {
        super();
        if (instream == null) {
            throw new IllegalArgumentException("Source input stream may not be null");
        }
        this.content = instream;
        this.length = length;
    }

    public boolean isRepeatable() {
        return false;
    }

    public boolean isChunked() {
        return this.chunked;
    }

    public void setChunked(boolean b) {
        this.chunked = b;
    }

    public long getContentLength() {
        return this.length;
    }
    
    public String getContentType() {
        return this.contentType;
    }

    public void setContentType(final String contentType) {
        this.contentType = contentType;
    }

    public String getContentEncoding() {
        return this.contentEncoding;
    }

    public void setContentEncoding(final String contentEncoding) {
        this.contentEncoding = contentEncoding;
    }

    public InputStream getContent() throws IOException {
        return this.content;
    }
        
    public boolean writeTo(final OutputStream outstream) throws IOException {
        if (outstream == null) {
            throw new IllegalArgumentException("Output stream may not be null");
        }
        InputStream instream = this.content;
        byte[] buffer = new byte[BUFFER_SIZE];
        long remaining = this.length;
    	while (remaining > 0) {
    	    int l = instream.read(buffer, 0, (int)Math.min(BUFFER_SIZE, remaining));
    	    if (l == -1) {
    	    	break;
    	    }
            outstream.write(buffer, 0, l);
    	    remaining -= l;
    	}
        return true;
    }
    
}
