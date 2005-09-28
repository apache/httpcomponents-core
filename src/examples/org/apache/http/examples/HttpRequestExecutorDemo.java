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

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.Protocol;
import org.apache.http.entity.EntityConsumer;
import org.apache.http.executor.HttpRequestExecutor;
import org.apache.http.impl.DefaultHttpClientConnection;
import org.apache.http.impl.DefaultHttpParams;
import org.apache.http.impl.io.PlainSocketFactory;
import org.apache.http.io.SocketFactory;
import org.apache.http.message.HttpGetRequest;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 */
public class HttpRequestExecutorDemo {

    public static void main(String[] args) throws Exception {
        
        SocketFactory socketfactory = PlainSocketFactory.getSocketFactory();
        Protocol.registerProtocol("http", new Protocol("http", socketfactory, 80));
        
        HttpParams params = new DefaultHttpParams(null);
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(params, "UTF-8");
        HttpProtocolParams.setUserAgent(params, "Jakarta HTTP Demo");
        
        HttpRequestExecutor httpexecutor = new HttpRequestExecutor();
        httpexecutor.setParams(params);
        // Required request interceptors
        httpexecutor.addRequestInterceptor(new RequestContent());
        httpexecutor.addRequestInterceptor(new RequestTargetHost());
        // Recommended request interceptors
        httpexecutor.addRequestInterceptor(new RequestConnControl());
        httpexecutor.addRequestInterceptor(new RequestUserAgent());
        httpexecutor.addRequestInterceptor(new RequestExpectContinue());
        
        HttpHost host = new HttpHost("www.yahoo.com");
        HttpClientConnection conn = new DefaultHttpClientConnection(host);
        try {
            HttpGetRequest request1 = new HttpGetRequest("/");
            HttpResponse response1 = httpexecutor.execute(request1, conn);
            System.out.println("<< Response: " + response1.getStatusLine());
            System.out.println(EntityConsumer.toString(response1.getEntity()));
            System.out.println("==============");
            if (conn.isOpen()) {
                System.out.println("Connection kept alive...");
            }
            HttpGetRequest request2 = new HttpGetRequest("/stuff");
            HttpResponse response2 = httpexecutor.execute(request2, conn);
            System.out.println("<< Response: " + response2.getStatusLine());
            System.out.println(EntityConsumer.toString(response2.getEntity()));
            System.out.println("==============");
        } finally {
            conn.close();
        }
    }
    
}
