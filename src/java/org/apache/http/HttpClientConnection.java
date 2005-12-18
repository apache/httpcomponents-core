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

package org.apache.http;

import java.io.IOException;
import java.net.InetAddress;

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
public interface HttpClientConnection extends HttpConnection {

    HttpHost getTargetHost();
    
    void setTargetHost(HttpHost targethost);
    
    InetAddress getLocalAddress();
    
    void setLocalAddress(InetAddress localAddress);
    
    void open(HttpParams params) throws IOException;
    
    void sendRequest(HttpRequest request) throws HttpException, IOException;

    void sendRequestHeader(HttpEntityEnclosingRequest request) throws HttpException, IOException;

    void sendRequestEntity(HttpEntityEnclosingRequest request) throws HttpException, IOException;

    HttpResponse receiveResponse(HttpRequest request) throws HttpException, IOException;

    HttpResponse receiveResponse(HttpRequest request, int timeout) throws HttpException, IOException;
    
}
