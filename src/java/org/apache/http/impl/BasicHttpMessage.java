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

import java.util.Iterator;

import org.apache.http.Header;
import org.apache.http.HeaderGroup;
import org.apache.http.HttpMutableMessage;
import org.apache.http.params.HttpParams;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
class BasicHttpMessage implements HttpMutableMessage {
    
    private final HeaderGroup headergroup;
    private final HttpParams params;
    
    protected BasicHttpMessage() {
        super();
        this.headergroup = new HeaderGroup();
        this.params = new DefaultHttpParams(null);
    }

    public boolean containsHeader(String name) {
        return this.headergroup.containsHeader(name);
    }
    
    public Header[] getHeaders(final String name) {
        return this.headergroup.getHeaders(name);
    }

    public Header getFirstHeader(final String name) {
        return this.headergroup.getFirstHeader(name);
    }

    public Header getLastHeader(final String name) {
        return this.headergroup.getLastHeader(name);
    }

    public Header[] getAllHeaders() {
        return this.headergroup.getAllHeaders();
    }
    
    public void addHeader(final Header header) {
        this.headergroup.addHeader(header);
    }

    public void setHeader(final Header header) {
        if (header == null) {
            return;
        }
        Header[] headers = this.headergroup.getHeaders(header.getName());
        for (int i = 0; i < headers.length; i++) {
            this.headergroup.removeHeader(headers[i]);
        }
        this.headergroup.addHeader(header);
    }

    public void removeHeader(final Header header) {
        this.headergroup.removeHeader(header);
    }
    
    public Iterator headerIterator() {
        return this.headergroup.iterator();
    }
    
    public HttpParams getParams() {
        return this.params;
    }
    
}
