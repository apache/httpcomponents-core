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

package org.apache.http.examples;

import java.net.Socket;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.Scheme;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpClientConnection;
import org.apache.http.impl.DefaultHttpParams;
import org.apache.http.impl.io.PlainSocketFactory;
import org.apache.http.io.SocketFactory;
import org.apache.http.message.HttpGet;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExecutionContext;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.util.EntityUtils;

/**
 * Elemental example for executing a GET request.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 *
 * <!-- empty lines above to avoid 'svn diff' context problems -->
 * @version $Revision$
 */
public class ElementalHttpGet {

    public static void main(String[] args) throws Exception {
        
        SocketFactory socketfactory = PlainSocketFactory.getSocketFactory();
        Scheme.registerScheme("http", new Scheme("http", socketfactory, 80));

        HttpParams params = new DefaultHttpParams(null);
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(params, "UTF-8");
        HttpProtocolParams.setUserAgent(params, "Jakarta-HttpComponents/1.1");
        HttpProtocolParams.setUseExpectContinue(params, true);

        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        // Required protocol interceptors
        httpproc.addInterceptor(new RequestContent());
        httpproc.addInterceptor(new RequestTargetHost());
        // Recommended protocol interceptors
        httpproc.addInterceptor(new RequestConnControl());
        httpproc.addInterceptor(new RequestUserAgent());
        httpproc.addInterceptor(new RequestExpectContinue());

        HttpRequestExecutor httpexecutor = new HttpRequestExecutor(httpproc);
        httpexecutor.setParams(params);
        
        HttpContext context = new HttpExecutionContext(null);
        
        HttpHost host = new HttpHost("localhost", 8080);
        DefaultHttpClientConnection conn = new DefaultHttpClientConnection();
        try {
            
            String[] targets = {
                    "/",
                    "/servlets-examples/servlet/RequestInfoExample", 
                    "/somewhere%20in%20pampa"};
            
            ConnectionReuseStrategy connStrategy = new DefaultConnectionReuseStrategy();
            
            for (int i = 0; i < targets.length; i++) {
                if (!conn.isOpen()) {
                    Socket socket = socketfactory.createSocket(
                            host.getHostName(), host.getPort(), null, 0, params);
                    conn.bind(socket, host, params);
                }
                HttpGet request = new HttpGet(targets[i]);
                System.out.println(">> Request URI: " + request.getRequestLine().getUri());
                HttpResponse response = httpexecutor.execute(request, conn, context);
                System.out.println("<< Response: " + response.getStatusLine());
                System.out.println(EntityUtils.toString(response.getEntity()));
                System.out.println("==============");
                if (!connStrategy.keepAlive(response, context)) {
                    conn.close();
                } else {
                    System.out.println("Connection kept alive...");
                }
            }
        } finally {
            conn.close();
        }
    }
    
}
