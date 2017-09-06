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

package org.apache.hc.core5.testing.nio;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.reactor.ExceptionEvent;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.testing.SSLTestContexts;
import org.apache.hc.core5.util.TimeValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InternalConnectChannelTimeoutTest extends InternalHttp1ServerTestBase {

    private final Logger log = LogManager.getLogger(getClass());

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> protocols() {
        return Arrays.asList(new Object[][]{
                { URIScheme.HTTP },
                { URIScheme.HTTPS }
        });
    }

    public InternalConnectChannelTimeoutTest(final URIScheme scheme) {
        super(scheme);
    }

    private static final TimeValue TIMEOUT = TimeValue.ofMillis(1);

    private Http1TestClient client;

    @Before
    public void setup() throws Exception {
        log.debug("Starting up test client");
        client = new Http1TestClient(
                IOReactorConfig.DEFAULT,
                scheme == URIScheme.HTTPS ? SSLTestContexts.createClientSSLContext() : null);
    }

    @After
    public void cleanup() throws Exception {
        log.debug("Shutting down test client");
        if (client != null) {
            client.shutdown(TimeValue.ofSeconds(5));
            final List<ExceptionEvent> exceptionLog = client.getExceptionLog();
            if (!exceptionLog.isEmpty()) {
                for (final ExceptionEvent event: exceptionLog) {
                    final Throwable cause = event.getCause();
                    log.error("Unexpected " + cause.getClass() + " at " + event.getTimestamp(), cause);
                }
            }
        }
    }

    //I want to simulate:
    //1. connect to bing.com last more than 1 MILLISECOND
    //2. because the TIMEOUT set to be 1 MILLISECOND, the get method should throw an expecption,and close the connection to bing.com
    //though TIMEOUT set to be 1 MILLISECOND,connect to bing.com will still less than 1 MILLISECOND.
    //use debug mode with breakpoints is an easy way to simulate,however I don't find a way to simulate in unit test.

    /*@Test(expected = ExecutionException.class)
    public void testConnectTimeout() throws Exception {
        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "www.bing.com", 80, TIMEOUT);
        //connecttimeout exception will be thrown
        connectFuture.get();
    }*/

    //connect port is wrong , can not be connected
    @Test(expected = ExecutionException.class)
    public void testCannotConnectedTimeout() throws Exception {
        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "www.bing.com", 8080, TIMEOUT);
        //connecttimeout exception will be thrown
        connectFuture.get();
    }

    // connect to wrong port with localhost
    @Test(expected = ExecutionException.class)
    public void testCannotConnectedTimeoutLocalHost() throws Exception {
        final InetSocketAddress serverEndpoint = server.start();
        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort() + 1, TIMEOUT);
        //connecttimeout exception will be thrown
        connectFuture.get();
    }


}
