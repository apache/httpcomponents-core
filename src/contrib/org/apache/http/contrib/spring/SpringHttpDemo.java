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
package org.apache.http.contrib.spring;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpHost;
import org.apache.http.HttpMutableRequest;
import org.apache.http.HttpRequestFactory;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.Scheme;
import org.apache.http.entity.EntityConsumer;
import org.apache.http.executor.HttpRequestExecutor;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;

public class SpringHttpDemo {

    public static void main(String[] args) throws Exception {

        ClassPathResource res = new ClassPathResource("org/apache/http/contrib/spring/http-beans.xml");
        XmlBeanFactory beanfactory = new XmlBeanFactory(res);
        
        // Set global params if desired
        HttpParams globalparams = (HttpParams) beanfactory.getBean("global-params");
        
        HttpParams params = (HttpParams) beanfactory.getBean("params");
        params.setDefaults(globalparams);
        
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(params, "UTF-8");
        HttpProtocolParams.setUseExpectContinue(params, true);
        HttpProtocolParams.setUserAgent(params, "Jakarta HTTP Demo");
        
        HttpRequestExecutor httpexec = (HttpRequestExecutor)beanfactory.getBean("http-executor");
        httpexec.setParams(params);
        
        Scheme http = (Scheme) beanfactory.getBean("http-protocol");
        HttpHost host = new HttpHost("www.yahoo.com", 80, http);

        HttpRequestFactory requestfactory = (HttpRequestFactory) beanfactory.getBean("http-request-factory");
        HttpClientConnection conn = (HttpClientConnection) beanfactory.getBean("http-connection");
        conn.setTargetHost(host);
        try {
            HttpMutableRequest request1 = requestfactory.newHttpRequest("GET", "/");
            HttpResponse response1 = httpexec.execute(request1, conn);
            System.out.println("<< Response: " + response1.getStatusLine());
            System.out.println(EntityConsumer.toString(response1.getEntity()));
            System.out.println("==============");
            if (conn.isOpen()) {
                System.out.println("Connection kept alive...");
            }
            HttpMutableRequest request2 = requestfactory.newHttpRequest("GET", "/stuff");
            HttpResponse response2 = httpexec.execute(request2, conn);
            System.out.println("<< Response: " + response2.getStatusLine());
            System.out.println(EntityConsumer.toString(response2.getEntity()));
            System.out.println("==============");
        } finally {
            conn.close();
        }
    }
    
}
