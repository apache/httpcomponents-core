/*
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

package org.apache.hc.core5.http.examples;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.SocketConfig;
import org.apache.hc.core5.http.impl.bootstrap.HttpRequester;
import org.apache.hc.core5.http.impl.bootstrap.RequesterBootstrap;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.HttpEntityWithTrailers;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpCoreContext;

/**
 * Example of POST request with trailers execution using classic I/O.
 */
public class ClassicPostWithTrailersExecutionExample {
    public static void main(String[] args) throws Exception {
        HttpRequester httpRequester = RequesterBootstrap.bootstrap().create();

        HttpCoreContext coreContext = HttpCoreContext.create();
        HttpHost target = new HttpHost("httpbin.org");

        String requestUri = "/post";
        ClassicHttpRequest request = new BasicClassicHttpRequest("POST", target, requestUri);
        HttpEntity requestBody = new HttpEntityWithTrailers(
                new StringEntity("Chunked message with trailers", ContentType.TEXT_PLAIN),
                new BasicHeader("t1","Hello world"));
        request.setEntity(requestBody);

        SocketConfig socketConfig = SocketConfig.custom()
                .setConnectTimeout(5000)
                .setSoTimeout(5000)
                .build();

        System.out.println(">> Request URI: " + request.getUri());
        try (ClassicHttpResponse response = httpRequester.execute(target, request, socketConfig, coreContext)) {
            System.out.println(requestUri + "->" + response.getCode());
            System.out.println(EntityUtils.toString(response.getEntity()));
            System.out.println("==============");
        }
    }

}
