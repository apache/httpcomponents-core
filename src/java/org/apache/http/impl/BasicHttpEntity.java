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

package org.apache.http.impl;

import java.io.InputStream;

import org.apache.http.HttpMutableEntity;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class BasicHttpEntity implements HttpMutableEntity {
    
    private String contenttype = null;
    private InputStream instream = null;
    private long contentlen = -1;
    private boolean chunked = false;
    
    protected BasicHttpEntity() {
        super();
    }

    public long getContentLength() {
        return this.contentlen;
    }

    public String getContentType() {
        return this.contenttype;
    }
    
    public InputStream getInputStream() {
        return this.instream;
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
        this.contentlen = len;
    }
    
    public void setContentType(final String contentType) {
        this.contenttype = contentType;
    }
    
    public void setInputStream(final InputStream instream) {
        this.instream = instream; 
    }
    
}
