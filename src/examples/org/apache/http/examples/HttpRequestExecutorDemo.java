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
import org.apache.http.entity.EntityConsumer;
import org.apache.http.executor.HttpRequestExecutor;
import org.apache.http.impl.DefaultHttpClientConnection;
import org.apache.http.impl.DefaultHttpParams;
import org.apache.http.impl.HttpGetRequest;
import org.apache.http.interceptor.RequestConnControl;
import org.apache.http.interceptor.RequestContent;
import org.apache.http.interceptor.RequestExpectContinue;
import org.apache.http.interceptor.RequestTargetHost;
import org.apache.http.interceptor.RequestUserAgent;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 */
public class HttpRequestExecutorDemo {

    public static void main(String[] args) throws Exception {
        
        HttpParams params = new DefaultHttpParams(null);
        
        new HttpProtocolParams(params)
            .setVersion(HttpVersion.HTTP_1_1)
            .setContentCharset("UTF-8")
            .setUserAgent("Jakarta HTTP Demo");
        
        HttpRequestExecutor httpexecutor = new HttpRequestExecutor(params);
        // Required request interceptors
        httpexecutor.setRequestInterceptor(new RequestContent());
        httpexecutor.setRequestInterceptor(new RequestTargetHost());
        // Recommended request interceptors
        httpexecutor.setRequestInterceptor(new RequestConnControl());
        httpexecutor.setRequestInterceptor(new RequestUserAgent());
        httpexecutor.setRequestInterceptor(new RequestExpectContinue());
        
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
