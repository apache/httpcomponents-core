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
import java.util.concurrent.Future;

import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.entity.EntityUtils;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.pool.io.BasicConnFactory;
import org.apache.hc.core5.http.pool.io.BasicConnPool;
import org.apache.hc.core5.http.pool.io.BasicPoolEntry;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.HttpProcessorBuilder;
import org.apache.hc.core5.http.protocol.RequestConnControl;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.http.protocol.RequestExpectContinue;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.apache.hc.core5.http.protocol.RequestUserAgent;

/**
 * Elemental example for executing multiple GET requests from different threads using a connection
 * pool.
 */
public class ElementalPoolingHttpGet {

    public static void main(String[] args) throws Exception {
        final HttpProcessor httpproc = HttpProcessorBuilder.create()
            .add(new RequestContent())
            .add(new RequestTargetHost())
            .add(new RequestConnControl())
            .add(new RequestUserAgent("Test/1.1"))
            .add(new RequestExpectContinue()).build();

        final HttpRequestExecutor httpexecutor = new HttpRequestExecutor();

        final BasicConnPool pool = new BasicConnPool(new BasicConnFactory());
        pool.setDefaultMaxPerRoute(2);
        pool.setMaxTotal(2);

        HttpHost[] targets = {
                new HttpHost("www.google.com", 80),
                new HttpHost("www.yahoo.com", 80),
                new HttpHost("www.apache.com", 80)
        };

        class WorkerThread extends Thread {

            private final HttpHost target;

            WorkerThread(final HttpHost target) {
                super();
                this.target = target;
            }

            @Override
            public void run() {
                ConnectionReuseStrategy connStrategy = DefaultConnectionReuseStrategy.INSTANCE;
                try {
                    Future<BasicPoolEntry> future = pool.lease(this.target, null);

                    boolean reusable = false;
                    BasicPoolEntry entry = future.get();
                    try {
                        HttpClientConnection conn = entry.getConnection();
                        HttpCoreContext coreContext = HttpCoreContext.create();
                        coreContext.setTargetHost(this.target);

                        BasicHttpRequest request = new BasicHttpRequest("GET", "/");
                        System.out.println(">> Request URI: " + request.getRequestLine().getUri());

                        httpexecutor.preProcess(request, httpproc, coreContext);
                        HttpResponse response = httpexecutor.execute(request, conn, coreContext);
                        httpexecutor.postProcess(response, httpproc, coreContext);

                        System.out.println("<< Response: " + response.getStatusLine());
                        System.out.println(EntityUtils.toString(response.getEntity()));

                        reusable = connStrategy.keepAlive(request, response, coreContext);
                    } catch (IOException | HttpException ex) {
                        throw ex;
                    } finally {
                        if (reusable) {
                            System.out.println("Connection kept alive...");
                        }
                        pool.release(entry, reusable);
                    }
                } catch (Exception ex) {
                    System.out.println("Request to " + this.target + " failed: " + ex.getMessage());
                }
            }

        };

        WorkerThread[] workers = new WorkerThread[targets.length];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new WorkerThread(targets[i]);
        }
        for (int i = 0; i < workers.length; i++) {
            workers[i].start();
        }
        for (int i = 0; i < workers.length; i++) {
            workers[i].join();
        }
    }

}
