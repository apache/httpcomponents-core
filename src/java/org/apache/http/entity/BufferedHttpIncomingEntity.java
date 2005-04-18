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

import org.apache.http.HttpIncomingEntity;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class BufferedHttpIncomingEntity implements HttpIncomingEntity {
    
    private final HttpIncomingEntity source;
    private final byte[] buffer;
    
    public BufferedHttpIncomingEntity(final HttpIncomingEntity entity) throws IOException {
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
    
    public InputStream getInputStream() {
        if (this.buffer != null) {
            return new ByteArrayInputStream(this.buffer);
        } else {
            return this.source.getInputStream();
        }
    }
    
    public boolean isChunked() {
        return false;
    }
    
    public boolean isRepeatable() {
        return true;
    }

}
