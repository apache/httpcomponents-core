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

import java.io.IOException;
import java.net.Socket;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.io.DefaultBHttpClientConnection;
import org.apache.hc.core5.http.impl.io.bootstrap.HttpRequester;
import org.apache.hc.core5.http.impl.io.bootstrap.RequesterBootstrap;
import org.apache.hc.core5.http.io.ResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;

/**
 * Example of GET requests execution using classic I/O.
 */
public class ClassicGetExecutionExample {

    public static void main(String[] args) throws Exception {
        HttpRequester httpRequester = RequesterBootstrap.bootstrap().create();
        HttpHost host = new HttpHost("localhost", 8080);

        try (DefaultBHttpClientConnection conn = new DefaultBHttpClientConnection(8 * 1024)) {

            HttpCoreContext coreContext = HttpCoreContext.create();
            String[] targets = {
                    "/",
                    "/examples/servlets/servlet/RequestInfoExample",
                    "/somewhere%20in%20pampa"};

            for (int i = 0; i < targets.length; i++) {
                if (!conn.isOpen()) {
                    Socket socket = new Socket(host.getHostName(), host.getPort());
                    conn.bind(socket);
                }
                ClassicHttpRequest request = new BasicClassicHttpRequest("GET", host, targets[i]);
                System.out.println(">> Request URI: " + request.getUri());
                httpRequester.execute(conn, request, coreContext, new ResponseHandler<Void>() {

                    @Override
                    public Void handleResponse(final ClassicHttpResponse response) throws HttpException, IOException {
                        System.out.println("<< Response: " + response.getCode());
                        System.out.println(EntityUtils.toString(response.getEntity()));
                        System.out.println("==============");
                        return null;
                    }

                });
            }
        }
    }

}
