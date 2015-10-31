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

package org.apache.http.examples;

import java.io.ByteArrayInputStream;
import java.net.Socket;

import org.apache.http.Consts;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.DefaultBHttpClientConnection;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpProcessorBuilder;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.util.EntityUtils;

/**
 * Elemental example for executing POST request with trailing headers
 *
 * <pre>{@code
 * protected void doPost(HttpServletRequest req, HttpServletResponse resp)
 *            throws ServletException, IOException {
 *       // read all body first
 *       IOUtils.copy(req.getInputStream(), resp.getOutputStream());
 *       logger.info("t1 trailer header is [{}]", req.getHeader("t1"));
 *   }
 * }</pre>
 */
public class ElementalHttpPost {
    public static void main(String[] args) throws Exception {
        HttpProcessor httpproc = HttpProcessorBuilder.create()
                .add(new RequestContent())
                .add(new RequestTargetHost())
                .add(new RequestConnControl())
                .add(new RequestUserAgent("Test/1.1"))
                .build();
        HttpRequestExecutor httpexecutor = new HttpRequestExecutor();
        HttpCoreContext coreContext = HttpCoreContext.create();
        HttpHost host = new HttpHost("localhost", 8080);
        coreContext.setTargetHost(host);
        DefaultBHttpClientConnection conn = new DefaultBHttpClientConnection(8 * 1024);
        InputStreamEntity requestBody =
                new InputStreamEntity(
                        new ByteArrayInputStream(
                                "This is the third test request (will be chunked)"
                                        .getBytes(Consts.UTF_8)),
                        ContentType.APPLICATION_OCTET_STREAM);
        Map<String, TrailerValueSupplier> m = new HashMap<>();
        m.put("t1", new ConstTrailerValueSupplier("Hello world"));
        requestBody.setTrailers(m);
        requestBody.setChunked(true);
        Socket socket = new Socket(host.getHostName(), host.getPort());
        conn.bind(socket);
        BasicHttpRequest request = new BasicHttpRequest("POST", "/");
        request.setEntity(requestBody);
        httpexecutor.preProcess(request, httpproc, coreContext);
        HttpResponse response = httpexecutor.execute(request, conn, coreContext);
        httpexecutor.postProcess(response, httpproc, coreContext);

        System.out.println("<< Response: " + response.getStatusLine());
        System.out.println(EntityUtils.toString(response.getEntity()));
        System.out.println("==============");
        conn.close();
    }
}
