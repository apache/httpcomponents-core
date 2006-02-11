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

import java.io.IOException;

/**
 * A generic HTTP connection, useful on client and server side.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public interface HttpConnection {

    /**
     * This method will gracefully close the connection. It will attempt to 
     * flush the transmitter's internal buffer prior to closing the underlying 
     * socket. This method MAY NOT be called from a different thread to force 
     * shutdown the connection. Use #shutdown() instead.
     * 
     * @see #shutdown()
     */
    void close() throws IOException;
    
    boolean isOpen();
 
    boolean isStale();
    
    /**
     * This method will force close the connection. This is the only method, 
     * which may be called from a different thread to terminate the connection. 
     * This method will not attempt to flush the transmitter's internal buffer 
     * prior to closing the underlying socket.
     */
    void shutdown() throws IOException;
}
