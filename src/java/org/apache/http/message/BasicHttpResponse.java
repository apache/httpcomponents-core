/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2006 The Apache Software Foundation
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

package org.apache.http.message;

import org.apache.http.HttpEntity;
import org.apache.http.HttpMutableResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.params.HttpProtocolParams;

/**
 * Basic implementation of an HTTP response that can be modified.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class BasicHttpResponse extends BasicHttpMessage implements HttpMutableResponse {
    
    private StatusLine statusline = null;
    private HttpEntity entity = null;
    
    public BasicHttpResponse() {
        super();
        setStatusCode(HttpStatus.SC_OK);
    }

    public BasicHttpResponse(final StatusLine statusline) {
        super();
        setStatusLine(statusline);
    }

    public StatusLine getStatusLine() {
        return this.statusline; 
    }

    public HttpEntity getEntity() {
        return this.entity;
    }

    public void setStatusLine(final StatusLine statusline) {
        if (statusline == null) {
            throw new IllegalArgumentException("Status line may not be null");
        }
        this.statusline = statusline;
    }
    
    public void setStatusCode(int code) {
        if (code < 0) {
            throw new IllegalArgumentException("Status line may not be null");
        }
        this.statusline = new StatusLine(
                HttpProtocolParams.getVersion(getParams()), 
                code, HttpStatus.getStatusText(code));
    }
    
    public void setEntity(final HttpEntity entity) {
        this.entity = entity;
    }
    
}
