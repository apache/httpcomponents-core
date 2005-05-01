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

import java.io.ByteArrayInputStream;

import org.apache.http.Header;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.EntityConsumer;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultHttpClientConnection;
import org.apache.http.impl.DefaultHttpParams;
import org.apache.http.impl.HttpPostRequest;
import org.apache.http.params.HttpParams;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 */
public class ElementalHttpPost {

    public static void main(String[] args) throws Exception {
        
        HttpParams connparams = new DefaultHttpParams(null);
        HttpHost host = new HttpHost("localhost", 8080);
        HttpClientConnection conn = new DefaultHttpClientConnection(host);
        try {
            
            HttpEntity[] requestBodies = {
                    new StringEntity(
                            "This is the first test request", "UTF-8"),
                    new ByteArrayEntity(
                            "This is the second test request".getBytes("UTF-8")),
                    new InputStreamEntity(
                            new ByteArrayInputStream(
                                    "This is the third test request (will be chunked)"
                                    .getBytes("UTF-8")), -1)
            };
            
            for (int i = 0; i < requestBodies.length; i++) {
                HttpPostRequest request = new HttpPostRequest("/httpclienttest/body");
                request.setHeader(new Header("Host", host.toHostString()));
                request.setHeader(new Header("Agent", "Elemental HTTP client"));
                request.setHeader(new Header("Connection", "Keep-Alive"));
                
                HttpEntity requestbody = requestBodies[i];
                // Must specify a transfer encoding or a content length 
                if (requestbody.isChunked() || requestbody.getContentLength() < 0) {
                    request.setHeader(new Header("Transfer-Encoding", "chunked"));
                } else {
                    request.setHeader(new Header("Content-Length", 
                            Long.toString(requestbody.getContentLength())));
                }
                // Specify a content type if known
                if (requestbody.getContentType() != null) {
                    request.setHeader(new Header("Content-Type", requestbody.getContentType())); 
                }
                request.setEntity(requestbody);
                
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
