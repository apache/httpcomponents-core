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
public class BasicHttpEntity implements HttpEntity {
    
    private String contentType = null;
    private String contentEncoding = null;
    private InputStream content = null;
    private long length = -1;
    private boolean chunked = false;
    
    public BasicHttpEntity() {
        super();
    }

    public long getContentLength() {
        return this.length;
    }

    public String getContentType() {
        return this.contentType;
    }
    
    public String getContentEncoding() {
        return this.contentEncoding;
    }
    
    public InputStream getContent() {
        return this.content;
    }
    
    public boolean isChunked() {
        return this.chunked;
    }
    
    public boolean isRepeatable() {
        return false;
    }
    
    public void setChunked(boolean b) {
        this.chunked = b;
    }
    
    public void setContentLength(long len) {
        this.length = len;
    }
    
    public void setContentType(final String contentType) {
        this.contentType = contentType;
    }
    
    public void setContentEncoding(final String contentEncoding) {
        this.contentEncoding = contentEncoding;
    }
    
    public void setContent(final InputStream instream) {
        this.content = instream; 
    }
    
    public boolean writeTo(final OutputStream outstream) throws IOException {
        if (outstream == null) {
            throw new IllegalArgumentException("Output stream may not be null");
        }
        if (this.content == null) {
            return true;
        }
        InputStream instream = this.content;
        int l;
        byte[] tmp = new byte[2048];
        while ((l = instream.read(tmp)) != -1) {
            outstream.write(tmp, 0, l);
        }
        return true;
    }
    
}
