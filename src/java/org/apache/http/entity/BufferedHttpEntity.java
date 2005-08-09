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

import java.io.ByteArrayInputStream;
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
public class BufferedHttpEntity implements HttpEntity {
    
    private final HttpEntity source;
    private final byte[] buffer;
    
    public BufferedHttpEntity(final HttpEntity entity) throws IOException {
        super();
        if (entity == null) {
            throw new IllegalArgumentException("HTTP entity may not be null");
        }
        this.source = entity;
        if (entity.isChunked() || !entity.isRepeatable() ) {
            this.buffer = EntityConsumer.toByteArray(entity);
        } else {
            this.buffer = null;
        }
    }

    public long getContentLength() {
        if (this.buffer != null) {
            return this.buffer.length;
        } else {
            return this.source.getContentLength();
        }
    }

    public String getContentType() {
        return this.source.getContentType();
    }
    
    public InputStream getContent() throws IOException {
        if (this.buffer != null) {
            return new ByteArrayInputStream(this.buffer);
        } else {
            return this.source.getContent();
        }
    }
    
    public boolean isChunked() {
        return false;
    }
    
    public boolean isRepeatable() {
        return true;
    }
    
    public boolean writeTo(final OutputStream outstream) throws IOException {
        if (outstream == null) {
            throw new IllegalArgumentException("Output stream may not be null");
        }
        if (this.buffer != null) {
            outstream.write(this.buffer);
        } else {
            this.source.writeTo(outstream);
        }
        return true;
    }
    
}
