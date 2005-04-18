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
import java.io.OutputStream;

import org.apache.http.HttpOutgoingEntity;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class StringEntity implements HttpOutgoingEntity {

    private final static String DEFAULT_CONTENT_TYPE = "text/plain; charset=";

    private final String source;
    private String contentType = null;
    private boolean chunked = false;

    public StringEntity(final String s, final String charset) {
        super();
        if (s == null) {
            throw new IllegalArgumentException("Source string may not be null");
        }
        this.source = s;
        if (charset != null) {
            this.contentType = DEFAULT_CONTENT_TYPE + charset;
        }
    }

    public StringEntity(final String s) {
        this(s, null);
    }

    public boolean isRepeatable() {
        return true;
    }

    public boolean isChunked() {
        return this.chunked;
    }

    public void setChunked(boolean b) {
        this.chunked = b;
    }

    public long getContentLength() {
        return this.source.length();
    }
    
    public String getContentType() {
        return this.contentType;
    }

    public void setContentType(final String s) {
        this.contentType = s;
    }

    public void writeTo(final OutputStream outstream) throws IOException {
        if (outstream == null) {
            throw new IllegalArgumentException("Output stream may not be null");
        }
        String charset = EntityConsumer.getContentCharSet(this);
        byte[] content = this.source.getBytes(charset);
        outstream.write(content);
        outstream.flush();
    }

}
