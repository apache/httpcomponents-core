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
import java.net.Socket;

import org.apache.http.params.HttpParams;

/**
 * An HTTP connection for use on the server side.
 * Requests are received, responses are sent.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public interface HttpServerConnection extends HttpConnection {

    /**
     * Binds this connection to an underlying socket.
     * 
     * @param socket The underlying socket.
     * @param params the parameters in effect for this connection
     * @throws IOException
     */
    void bind(Socket socket, HttpParams params) 
        throws IOException;
    
    /**
     * Receives the request line and all headers available from this connection.
     * The caller should examine the returned request and decide if to receive a
     * request entity as well.
     * 
     * @param params the parameters in effect for this connection
     * @return a new HttpRequest object whose request line and headers are
     *         initialized.
     * @throws HttpException
     * @throws IOException
     */
    HttpRequest receiveRequestHeader(HttpParams params) 
        throws HttpException, IOException;

    /**
     * Receives the next request entity available from this connection and attaches it to 
     * an existing request. 
     * @param request the request to attach the entity to.
     * @throws HttpException
     * @throws IOException
     */
    void receiveRequestEntity(HttpEntityEnclosingRequest request) 
        throws HttpException, IOException;

    /**
     * Sends the response line and headers of a response over this connection.
     * @param response the response whose headers to send.
     * @throws HttpException
     * @throws IOException
     */
    void sendResponseHeader(HttpResponse response) 
        throws HttpException, IOException;
    
    /**
     * Sends the response entity of a response over this connection.
     * @param response the response whose entity to send.
     * @throws HttpException
     * @throws IOException
     */
    void sendResponseEntity(HttpResponse response) 
        throws HttpException, IOException;
    
    /**
     * Sends all pending buffered data over this connection.
     * @throws IOException
     */
    void flush()
        throws IOException;
    
}
