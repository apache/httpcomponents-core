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
import org.apache.http.io.ContentLengthInputStream;

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

    private final InputStream content;
    private final long length;
    private String contentType = DEFAULT_CONTENT_TYPE;
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

    public void setContentType(final String s) {
        this.contentType = s;
    }

    public InputStream getContent() throws IOException {
        return this.content;
    }
        
    public void writeTo(final OutputStream outstream) throws IOException {
        if (outstream == null) {
            throw new IllegalArgumentException("Output stream may not be null");
        }
        InputStream instream = this.content;
        if (this.length >= 0) {
            instream = new ContentLengthInputStream(instream, this.length);
        }
        int l;
        byte[] tmp = new byte[1024];
        while ((l = instream.read(tmp)) != -1) {
            outstream.write(tmp, 0, l);
        }
    }
    
}
