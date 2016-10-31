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

package org.apache.hc.core5.http.osgi;

import java.io.IOException;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.testing.classic.ClassicTestClient;
import org.apache.hc.core5.testing.classic.ClassicTestServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

/**
 * Use the test methods from TestSyncHttp.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class ActuallyConnectIT {

    @Configuration
    public static Option[] options() {
        return Common.config();
    }

    private ClassicTestServer server;
    private ClassicTestClient client;

    @Before
    public void initServer() throws Exception {
        this.server = new ClassicTestServer();
        this.server.setTimeout(5000);
    }

    @Before
    public void initClient() throws Exception {
        this.client = new ClassicTestClient();
        this.client.setTimeout(5000);
    }

    @After
    public void shutDownServer() throws Exception {
        if (this.server != null) {
            this.server.shutdown();
        }
    }

    @Test
    public void testSingleGet() throws Exception {

        // Initialize the server-side request handler
        this.server.registerHandler("*", new HttpRequestHandler() {

            @Override
            public void handle(
                    final ClassicHttpRequest request,
                    final ClassicHttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                response.setEntity(new StringEntity("Hi there"));
            }

        });

        this.server.start();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpHost host = new HttpHost("localhost", this.server.getPort());

        try {
            final BasicClassicHttpRequest get = new BasicClassicHttpRequest("GET", "/");
            final ClassicHttpResponse response = this.client.execute(host, get, context);
            Assert.assertEquals(200, response.getCode());
            Assert.assertEquals("Hi there", EntityUtils.toString(response.getEntity()));
            this.client.keepAlive(get, response, context);
        } finally {
            this.server.shutdown();
        }
    }

}
