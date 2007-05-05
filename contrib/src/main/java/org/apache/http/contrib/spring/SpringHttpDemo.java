/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.http.contrib.spring;

import java.net.Socket;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestFactory;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.impl.DefaultHttpClientConnection;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExecutionContext;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;

public class SpringHttpDemo {

    public static void main(String[] args) throws Exception {

        ClassPathResource res = new ClassPathResource("org/apache/http/contrib/spring/http-beans.xml");
        XmlBeanFactory beanfactory = new XmlBeanFactory(res);
        
        // Set global params if desired
        HttpParams globalparams = (HttpParams) beanfactory.getBean("global-params");
        globalparams
            .setParameter(HttpProtocolParams.PROTOCOL_VERSION, HttpVersion.HTTP_1_1)
            .setParameter(HttpProtocolParams.HTTP_CONTENT_CHARSET, "UTF-8")
            .setBooleanParameter(HttpProtocolParams.USE_EXPECT_CONTINUE, true)
            .setParameter(HttpProtocolParams.USER_AGENT, "Jakarta-HttpComponents/1.1");
        
        HttpParams params = (HttpParams) beanfactory.getBean("params");
        
        HttpRequestExecutor httpexec = (HttpRequestExecutor)beanfactory.getBean("http-executor");
        
        HttpHost host = new HttpHost("www.yahoo.com", 80);

        HttpContext context = new HttpExecutionContext(null);
        DefaultHttpClientConnection conn = new DefaultHttpClientConnection();
        
        context.setAttribute(HttpExecutionContext.HTTP_TARGET_HOST, host);
        
        HttpRequestFactory requestfactory = (HttpRequestFactory) beanfactory.getBean("http-request-factory");
        ConnectionReuseStrategy connStrategy = (ConnectionReuseStrategy) beanfactory.getBean("conn-reuse-strategy");
        try {
            if (!conn.isOpen()) {
                Socket socket = new Socket(host.getHostName(), host.getPort());
                conn.bind(socket, params);
            }
            HttpRequest request1 = requestfactory.newHttpRequest("GET", "/");
            HttpResponse response1 = httpexec.execute(request1, conn, context);
            System.out.println("<< Response: " + response1.getStatusLine());
            System.out.println(EntityUtils.toString(response1.getEntity()));
            System.out.println("==============");
            if (connStrategy.keepAlive(response1, context)) {
                System.out.println("Connection kept alive...");
            } else {
                conn.close();
                System.out.println("Connection closed...");
            }
            if (!conn.isOpen()) {
                Socket socket = new Socket(host.getHostName(), host.getPort());
                conn.bind(socket, params);
            }
            HttpRequest request2 = requestfactory.newHttpRequest("GET", "/stuff");
            HttpResponse response2 = httpexec.execute(request2, conn, context);
            System.out.println("<< Response: " + response2.getStatusLine());
            System.out.println(EntityUtils.toString(response2.getEntity()));
            System.out.println("==============");
            if (connStrategy.keepAlive(response2, context)) {
                System.out.println("Connection kept alive...");
            } else {
                conn.close();
                System.out.println("Connection closed...");
            }
        } finally {
            conn.close();
        }
    }
    
}
