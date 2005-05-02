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

package org.apache.http.examples;

import org.apache.http.Header;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.entity.EntityConsumer;
import org.apache.http.impl.DefaultHttpClientConnection;
import org.apache.http.impl.DefaultHttpParams;
import org.apache.http.impl.HttpGetRequest;
import org.apache.http.params.HttpParams;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 */
public class ElementalHttpGet {

    public static void main(String[] args) throws Exception {
        
        HttpParams connparams = new DefaultHttpParams(null);
        HttpHost host = new HttpHost("www.yahoo.com");
        HttpClientConnection conn = new DefaultHttpClientConnection(host);
        try {
            
            String[] targets = {
                    "/",
                    "/news/", 
                    "/somewhere%20in%20pampa"};
            
            for (int i = 0; i < targets.length; i++) {
                HttpGetRequest request = new HttpGetRequest(targets[i]);
                request.setHeader(new Header("Host", host.toHostString()));
                request.setHeader(new Header("User-Agent", "Elemental HTTP client"));
                request.setHeader(new Header("Connection", "Keep-Alive"));
                if (!conn.isOpen()) {
                    System.out.println("Open new connection to: " + host);
                    conn.open(connparams);
                } else {
                    System.out.println("Connection kept alive. Reusing...");
                }
                System.out.println(">> Request URI: " + request.getRequestLine().getUri());
                HttpResponse response = conn.sendRequest(request);
                // Request may be terminated prematurely, when expect-continue 
                // protocol is used
                if (response == null) {
                    // No error response so far. 
                    response = conn.receiveResponse(request);
                }
                EntityConsumer body = new EntityConsumer(response);
                System.out.println("<< Response: " + response.getStatusLine());
                System.out.println(body.asString());
                System.out.println("==============");
            }
        } finally {
            conn.close();
        }
        
    }
    
}
