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

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class DefaultConnectionReuseStrategy implements ConnectionReuseStrategy {

    private static final String CONN_DIRECTIVE = "Connection";
    private static final String CONN_CLOSE = "Close";
    private static final String CONN_KEEP_ALIVE = "Keep-Alive";
    
    public DefaultConnectionReuseStrategy() {
        super();
    }
    
    public boolean keepAlive(final HttpResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("HTTP response may not be null");
        }
        HttpEntity entity = response.getEntity();
        HttpVersion ver = response.getStatusLine().getHttpVersion();
        if (entity != null) {
            if (entity.getContentLength() < 0 && !entity.isChunked()) {
                // if the content length is not known and is not chunk
                // encoded, the connection cannot be reused
                return false;
            }
        }
        // Check for 'Connection' directive
        Header connheader = response.getFirstHeader(CONN_DIRECTIVE);
        if (connheader != null) {
            String conndirective = connheader.getValue(); 
            if (CONN_CLOSE.equalsIgnoreCase(conndirective)) {
                return false;
            } else if (CONN_KEEP_ALIVE.equalsIgnoreCase(conndirective)) {
                return true;
            } else {
                // log unknown directive
            }
        }
        // Resorting to protocol version default close connection policy
        return ver.greaterEquals(HttpVersion.HTTP_1_1);
    }
            
}
