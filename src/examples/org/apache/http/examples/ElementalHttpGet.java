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
import org.apache.http.Scheme;
import org.apache.http.impl.DefaultHttpClientConnection;
import org.apache.http.impl.DefaultHttpParams;
import org.apache.http.impl.io.PlainSocketFactory;
import org.apache.http.io.SocketFactory;
import org.apache.http.message.HttpGet;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 */
public class ElementalHttpGet {

    public static void main(String[] args) throws Exception {
        
        SocketFactory socketfactory = PlainSocketFactory.getSocketFactory();
        Scheme.registerScheme("http", new Scheme("http", socketfactory, 80));
        
        HttpParams connparams = new DefaultHttpParams(null);
        HttpHost host = new HttpHost("localhost", 8080);
        HttpClientConnection conn = new DefaultHttpClientConnection(host);
        try {
            
            String[] targets = {
                    "/",
                    "/servlets-examples/servlet/RequestInfoExample", 
                    "/somewhere%20in%20pampa"};
            
            for (int i = 0; i < targets.length; i++) {
                HttpGet request = new HttpGet(targets[i]);
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
                conn.sendRequest(request);
                HttpResponse response = conn.receiveResponse(request); 
                System.out.println("<< Response: " + response.getStatusLine());
                System.out.println(EntityUtils.toString(response.getEntity()));
                System.out.println("==============");
            }
        } finally {
            conn.close();
        }
        
    }
    
}
