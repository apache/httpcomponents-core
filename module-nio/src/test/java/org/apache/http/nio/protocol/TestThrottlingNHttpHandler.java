/*
 * $HeadURL:$
 * $Revision:$
 * $Date:$
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

package org.apache.http.nio.protocol;

import java.io.InterruptedIOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.mockup.SimpleEventListener;
import org.apache.http.mockup.SimpleHttpRequestHandlerResolver;
import org.apache.http.mockup.TestHttpClient;
import org.apache.http.mockup.TestHttpServer;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.NHttpServiceHandler;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExpectationVerifier;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;

/**
 * HttpCore NIO integration tests using throttling version of protocol
 * handlers.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * 
 * @version $Id:$
 */
public class TestThrottlingNHttpHandler extends TestCase {

    private static final int DEFAULT_SERVER_SO_TIMEOUT = 5000;
    
    // ------------------------------------------------------------ Constructor
    public TestThrottlingNHttpHandler(String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestThrottlingNHttpHandler.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestThrottlingNHttpHandler.class);
    }

    private TestHttpServer server;
    private TestHttpClient client;
    
    @Override
    protected void setUp() throws Exception {
        HttpParams serverParams = new BasicHttpParams();
        serverParams
            .setIntParameter(CoreConnectionPNames.SO_TIMEOUT, DEFAULT_SERVER_SO_TIMEOUT)
            .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
            .setParameter(CoreProtocolPNames.ORIGIN_SERVER, "TEST-SERVER/1.1");
        
        this.server = new TestHttpServer(serverParams);
        
        HttpParams clientParams = new BasicHttpParams();
        clientParams
            .setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 5000)
            .setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 2000)
            .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
            .setParameter(CoreProtocolPNames.USER_AGENT, "TEST-CLIENT/1.1");
        
        this.client = new TestHttpClient(clientParams);
    }

    @Override
    protected void tearDown() throws Exception {
        this.server.shutdown();
        this.client.shutdown();
    }
    
    private NHttpServiceHandler createHttpServiceHandler(
            final HttpRequestHandler requestHandler,
            final HttpExpectationVerifier expectationVerifier,
            final Executor executor) {
        
        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        httpproc.addInterceptor(new ResponseDate());
        httpproc.addInterceptor(new ResponseServer());
        httpproc.addInterceptor(new ResponseContent());
        httpproc.addInterceptor(new ResponseConnControl());

        NHttpServiceHandlerBase serviceHandler = new ThrottlingHttpServiceHandler(
                httpproc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                executor,
                this.server.getParams());

        serviceHandler.setHandlerResolver(
                new SimpleHttpRequestHandlerResolver(requestHandler));
        serviceHandler.setExpectationVerifier(expectationVerifier);
        serviceHandler.setEventListener(new SimpleEventListener());
        
        return serviceHandler;
    }
    
    private NHttpClientHandler createHttpClientHandler(
            final HttpRequestExecutionHandler requestExecutionHandler) {
        
        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        httpproc.addInterceptor(new RequestContent());
        httpproc.addInterceptor(new RequestTargetHost());
        httpproc.addInterceptor(new RequestConnControl());
        httpproc.addInterceptor(new RequestUserAgent());
        httpproc.addInterceptor(new RequestExpectContinue());

        BufferingHttpClientHandler clientHandler = new BufferingHttpClientHandler(
                httpproc,
                requestExecutionHandler,
                new DefaultConnectionReuseStrategy(),
                this.client.getParams());

        clientHandler.setEventListener(new SimpleEventListener());
        return clientHandler;
    }
    
    /**
     * This test ensures that an Executor instance 
     * (under the control of a ThrottlingHttpServiceHandler)
     * terminates when a connection timeout occurs.
     */
    public void testExecutorTermination() throws Exception {
        // swap original timeout with a short one.
        int originalTimeout = this.server.getParams().getIntParameter(
                CoreConnectionPNames.SO_TIMEOUT, DEFAULT_SERVER_SO_TIMEOUT);
        final int SHORT_TIMEOUT = 100;
        this.server.getParams().setIntParameter(
                CoreConnectionPNames.SO_TIMEOUT, SHORT_TIMEOUT);
        
        // main expectation: the executor spawned by the service must finish.
        final String COMMAND_FINISHED = "CommandFinished";
        final Map<String, Boolean> serverExpectations = Collections.synchronizedMap(
                new HashMap<String, Boolean>());
        serverExpectations.put(COMMAND_FINISHED, false);
        
        // secondary expectation: not strictly necessary, the test will wait for the 
        // client to finalize the request 
        final String CLIENT_FINALIZED = "ClientFinalized";
        final Map<String, Boolean> clientExpectations = Collections.synchronizedMap(
                new HashMap<String, Boolean>());
        clientExpectations.put(CLIENT_FINALIZED, false);
        
        // runs the command on a separate thread and updates the server expectation
        Executor executor = new Executor() {
            
            public void execute(final Runnable command) {
                new Thread() {
                    public void run() {
                        command.run();
                        synchronized (serverExpectations) {
                            serverExpectations.put(COMMAND_FINISHED, true);
                            serverExpectations.notify();
                        }
                    }
                }.start();
            }
            
        };
        
        // additional expectation: server-side processing ends with an exception.
        final Exception handlerException = new Exception();
        HttpRequestHandler requestHandler = new HttpRequestHandler() {
            public void handle(
                    HttpRequest request,
                    HttpResponse response,
                    HttpContext context) {
                try {
                    ((HttpEntityEnclosingRequest) request).getEntity().getContent().read();
                    response.setStatusCode(HttpStatus.SC_OK);
                } catch (Exception e){
                    handlerException.initCause(e);
                }
            }
        };
        
        // convoluted client-side entity content. The byte expected by the HttpRequest will not 
        // be written.
        final PipedOutputStream pipe = new PipedOutputStream();
        final PipedInputStream producer = new PipedInputStream(pipe);
        pipe.close();
        
        // A POST request enclosing an entity with (supposedly) a content length of 1 byte.
        // the connection will be closed at the end of the request.
        HttpRequestExecutionHandler requestExecutionHandler = new HttpRequestExecutionHandler() {
            public void initalizeContext(final HttpContext context, final Object attachment) {
            }

            public void finalizeContext(HttpContext context) {
                synchronized (clientExpectations) {
                    clientExpectations.put(CLIENT_FINALIZED, true);
                    clientExpectations.notifyAll();
                }
            }

            public HttpRequest submitRequest( HttpContext context ) {
                HttpEntityEnclosingRequest post = new BasicHttpEntityEnclosingRequest("POST", "/");
                post.setHeader( "Connection", "Close" );
                post.setEntity(new InputStreamEntity(producer, 1));
                return post;
            }

            public void handleResponse(final HttpResponse response, final HttpContext context) {
            }
            
        };
        
        // create a throttling service handler  
        NHttpServiceHandler serviceHandler = createHttpServiceHandler(
                requestHandler, 
                null, 
                executor);

        NHttpClientHandler clientHandler = createHttpClientHandler(
                requestExecutionHandler);

        this.server.start(serviceHandler);
        this.client.start(clientHandler);
        
        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();
        
        this.client.openConnection(
                new InetSocketAddress("localhost", serverAddress.getPort()), 
                null);
        
        // wait for the client to invoke finalizeContext().
        synchronized (clientExpectations) {
            if (!clientExpectations.get(CLIENT_FINALIZED)) {
                clientExpectations.wait(DEFAULT_SERVER_SO_TIMEOUT);
                assertTrue(clientExpectations.get(CLIENT_FINALIZED));
            }
        }
        
        // wait for server to finish the command within a reasonable amount of time.
        // the time constraint is not necessary, it only prevents the test from hanging.
        synchronized (serverExpectations) {
            if (!serverExpectations.get(COMMAND_FINISHED)) {
                serverExpectations.wait(SHORT_TIMEOUT);
                assertTrue(serverExpectations.get(COMMAND_FINISHED));
            }
        }
        
        // ensure server-side processing was aborted
        assertNotNull(handlerException.getCause());
        assertEquals(InterruptedIOException.class, handlerException.getCause().getClass());
        
        this.server.shutdown();
        this.client.shutdown();
        
        // restore original timeout
        this.server.getParams().setIntParameter(
                CoreConnectionPNames.SO_TIMEOUT, originalTimeout);
    }
}
