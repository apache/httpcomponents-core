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

package org.apache.http;


/**
 * Interface for deciding whether a connection should be kept alive.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 *
 * <!-- empty lines above to avoid 'svn diff' context problems -->
 * @version $Revision$
 * 
 * @since 4.0
 */
public interface ConnectionReuseStrategy {

    /**
     * Decides whether a connection can be kept open after a request.
     * If this method returns <code>false</code>, the caller MUST
     * close the connection to correctly implement the HTTP protocol.
     * If it returns <code>true</code>, the caller SHOULD attempt to
     * keep the connection open for reuse with another request.
     * <br/>
     * If the connection is already closed, <code>false</code> is returned.
     * The stale connection check MUST NOT be triggered by a
     * connection reuse strategy.
     *
     * @param connection
     *          The connection for which to decide about reuse.
     * @param response
     *          The last response received over that connection.
     *
     * @return <code>true</code> if the connection is allowed to be reused, or
     *         <code>false</code> if it MUST NOT be reused
     */
    public boolean keepAlive(HttpConnection connection,
                             HttpResponse response);
            
}
