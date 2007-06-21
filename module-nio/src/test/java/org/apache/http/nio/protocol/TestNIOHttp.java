/*
 * $HeadURL$
 * $Revision$
 * $Date$
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.NHttpServiceHandler;
import org.apache.http.nio.mockup.CountingEventListener;
import org.apache.http.nio.mockup.SimpleHttpRequestHandlerResolver;
import org.apache.http.nio.mockup.SimpleThreadPoolExecutor;
import org.apache.http.nio.mockup.TestHttpClient;
import org.apache.http.nio.mockup.TestHttpServer;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExecutionContext;
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
import org.apache.http.util.EncodingUtils;
import org.apache.http.util.EntityUtils;

/**
 * HttpCore NIO integration tests.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * 
 * @version $Id$
 */
public class TestNIOHttp extends TestCase {

    private static final int MODE_BUFFERING    = 0;
    private static final int MODE_THROTTLING   = 1;
    
    private int clientMode = MODE_BUFFERING;
    private int serverMode = MODE_BUFFERING;
    
    // ------------------------------------------------------------ Constructor
    public TestNIOHttp(String testName) {
        super(testName);
    }

    public int getClientMode() {
        return this.clientMode;
    }

    public void setClientMode(int clientMode) {
        this.clientMode = clientMode;
    }

    public int getServerMode() {
        return this.serverMode;
    }

    public void setServerMode(int serverMode) {
        this.serverMode = serverMode;
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestNIOHttp.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    static class ServerModeDecorator extends TestSetup {

        private int clientMode;
        private int serverMode;
        
        public ServerModeDecorator(final TestNIOHttp test, int clientMode, int serverMode) {
            super(test);
            this.clientMode = clientMode;
            this.serverMode = serverMode;
        }
                
        protected void setUp() throws Exception {
            TestNIOHttp testcase = (TestNIOHttp)getTest();
            testcase.setServerMode(this.serverMode);
            testcase.setClientMode(this.clientMode);
        }  
    }
    
    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        TestSuite source = new TestSuite(TestNIOHttp.class);
        
        TestSuite suite = new TestSuite();
        for (Enumeration en = source.tests(); en.hasMoreElements(); ) {
            TestNIOHttp test = (TestNIOHttp) en.nextElement();
            suite.addTest(new ServerModeDecorator(test, MODE_BUFFERING, MODE_BUFFERING));            
        }
        for (Enumeration en = source.tests(); en.hasMoreElements(); ) {
            TestNIOHttp test = (TestNIOHttp) en.nextElement();
            suite.addTest(new ServerModeDecorator(test, MODE_THROTTLING, MODE_BUFFERING));            
        }
        for (Enumeration en = source.tests(); en.hasMoreElements(); ) {
            TestNIOHttp test = (TestNIOHttp) en.nextElement();
            suite.addTest(new ServerModeDecorator(test, MODE_BUFFERING, MODE_THROTTLING));            
        }
        for (Enumeration en = source.tests(); en.hasMoreElements(); ) {
            TestNIOHttp test = (TestNIOHttp) en.nextElement();
            suite.addTest(new ServerModeDecorator(test, MODE_THROTTLING, MODE_THROTTLING));            
        }
        return suite;
    }

    private TestHttpServer server;
    private TestHttpClient client;
    private SimpleThreadPoolExecutor executor;
    
    protected void setUp() throws Exception {
        HttpParams serverParams = new BasicHttpParams();
        serverParams
            .setIntParameter(HttpConnectionParams.SO_TIMEOUT, 5000)
            .setIntParameter(HttpConnectionParams.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(HttpConnectionParams.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(HttpConnectionParams.TCP_NODELAY, true)
            .setParameter(HttpProtocolParams.ORIGIN_SERVER, "TEST-SERVER/1.1");
        
        this.server = new TestHttpServer(serverParams);
        
        HttpParams clientParams = new BasicHttpParams();
        clientParams
            .setIntParameter(HttpConnectionParams.SO_TIMEOUT, 5000)
            .setIntParameter(HttpConnectionParams.CONNECTION_TIMEOUT, 2000)
            .setIntParameter(HttpConnectionParams.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(HttpConnectionParams.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(HttpConnectionParams.TCP_NODELAY, true)
            .setParameter(HttpProtocolParams.USER_AGENT, "TEST-CLIENT/1.1");
        
        this.client = new TestHttpClient(clientParams);
        this.executor = new SimpleThreadPoolExecutor();
    }

    protected void tearDown() throws Exception {
        this.executor.shutdown();
        this.server.shutdown();
        this.client.shutdown();
    }
    
    private NHttpServiceHandler createHttpServiceHandler(
            final HttpRequestHandler requestHandler,
            final HttpExpectationVerifier expectationVerifier,
            final EventListener eventListener) {
        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        httpproc.addInterceptor(new ResponseDate());
        httpproc.addInterceptor(new ResponseServer());
        httpproc.addInterceptor(new ResponseContent());
        httpproc.addInterceptor(new ResponseConnControl());

        if (this.serverMode == MODE_BUFFERING) {
            BufferingHttpServiceHandler serviceHandler = new BufferingHttpServiceHandler(
                    httpproc,
                    new DefaultHttpResponseFactory(),
                    new DefaultConnectionReuseStrategy(),
                    this.server.getParams());

            serviceHandler.setHandlerResolver(
                    new SimpleHttpRequestHandlerResolver(requestHandler));
            serviceHandler.setExpectationVerifier(expectationVerifier);
            serviceHandler.setEventListener(eventListener);
            
            return serviceHandler;
        }
        if (this.serverMode == MODE_THROTTLING) {
            ThrottlingHttpServiceHandler serviceHandler = new ThrottlingHttpServiceHandler(
                    httpproc,
                    new DefaultHttpResponseFactory(),
                    new DefaultConnectionReuseStrategy(),
                    this.executor,
                    this.server.getParams());

            serviceHandler.setHandlerResolver(
                    new SimpleHttpRequestHandlerResolver(requestHandler));
            serviceHandler.setExpectationVerifier(expectationVerifier);
            serviceHandler.setEventListener(eventListener);
            
            return serviceHandler;
        }
        throw new IllegalStateException();
    }
    
    private NHttpClientHandler createHttpClientHandler(
            final HttpRequestExecutionHandler requestExecutionHandler,
            final EventListener eventListener) {
        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        httpproc.addInterceptor(new RequestContent());
        httpproc.addInterceptor(new RequestTargetHost());
        httpproc.addInterceptor(new RequestConnControl());
        httpproc.addInterceptor(new RequestUserAgent());
        httpproc.addInterceptor(new RequestExpectContinue());

        if (this.clientMode == MODE_BUFFERING) {
            BufferingHttpClientHandler clientHandler = new BufferingHttpClientHandler(
                    httpproc,
                    requestExecutionHandler,
                    new DefaultConnectionReuseStrategy(),
                    this.client.getParams());

            clientHandler.setEventListener(eventListener);
            return clientHandler;
        }
        if (this.clientMode == MODE_THROTTLING) {
            ThrottlingHttpClientHandler clientHandler = new ThrottlingHttpClientHandler(
                    httpproc,
                    requestExecutionHandler,
                    new DefaultConnectionReuseStrategy(),
                    this.executor,
                    this.client.getParams());

            clientHandler.setEventListener(eventListener);
            return clientHandler;
        }
        throw new IllegalStateException();
    }
    
    /**
     * This test case executes a series of simple (non-pipelined) GET requests 
     * over multiple connections. 
     */
    public void testSimpleHttpGets() throws Exception {
        
        final int connNo = 3;
        final int reqNo = 20;
        
        Random rnd = new Random();
        
        // Prepare some random data
        final List testData = new ArrayList(reqNo);
        for (int i = 0; i < reqNo; i++) {
            int size = rnd.nextInt(5000);
            byte[] data = new byte[size];
            rnd.nextBytes(data);
            testData.add(data);
        }
        
        List[] responseData = new List[connNo];
        for (int i = 0; i < responseData.length; i++) {
            responseData[i] = new ArrayList();
        }
        
        HttpRequestHandler requestHandler = new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request, 
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException, IOException {
                
                String s = request.getRequestLine().getUri();
                URI uri;
                try {
                    uri = new URI(s);
                } catch (URISyntaxException ex) {
                    throw new HttpException("Invalid request URI: " + s);
                }
                int index = Integer.parseInt(uri.getQuery());
                byte[] data = (byte []) testData.get(index);
                ByteArrayEntity entity = new ByteArrayEntity(data); 
                response.setEntity(entity);
            }
            
        };
        
        HttpRequestExecutionHandler requestExecutionHandler = new HttpRequestExecutionHandler() {

            public void initalizeContext(final HttpContext context, final Object attachment) {
                context.setAttribute("LIST", (List) attachment);
                context.setAttribute("REQ-COUNT", new Integer(0));
                context.setAttribute("RES-COUNT", new Integer(0));
            }

            public HttpRequest submitRequest(final HttpContext context) {
                int i = ((Integer) context.getAttribute("REQ-COUNT")).intValue();
                BasicHttpRequest get = null;
                if (i < reqNo) {
                    get = new BasicHttpRequest("GET", "/?" + i);
                    context.setAttribute("REQ-COUNT", new Integer(i + 1));
                }
                return get;
            }
            
            public void handleResponse(final HttpResponse response, final HttpContext context) {
                NHttpConnection conn = (NHttpConnection) context.getAttribute(
                        HttpExecutionContext.HTTP_CONNECTION);
                
                List list = (List) context.getAttribute("LIST");
                int i = ((Integer) context.getAttribute("RES-COUNT")).intValue();
                i++;
                context.setAttribute("RES-COUNT", new Integer(i));

                try {
                    HttpEntity entity = response.getEntity();
                    byte[] data = EntityUtils.toByteArray(entity);
                    list.add(data);
                } catch (IOException ex) {
                    fail(ex.getMessage());
                }

                if (i < reqNo) {
                    conn.requestInput();
                } else {
                    try {
                        conn.close();
                    } catch (IOException ex) {
                        fail(ex.getMessage());
                    }
                }
            }
            
        };
        
        CountingEventListener serverEventListener = new CountingEventListener();
        CountingEventListener clientEventListener = new CountingEventListener();
        
        NHttpServiceHandler serviceHandler = createHttpServiceHandler(
                requestHandler, 
                null,
                serverEventListener);

        NHttpClientHandler clientHandler = createHttpClientHandler(
                requestExecutionHandler, 
                clientEventListener);

        this.server.start(serviceHandler);
        this.client.start(clientHandler);
        
        InetSocketAddress serverAddress = (InetSocketAddress) this.server.getSocketAddress();
        
        for (int i = 0; i < responseData.length; i++) {
            this.client.openConnection(
                    new InetSocketAddress("localhost", serverAddress.getPort()), 
                    responseData[i]);
        }
     
        clientEventListener.await(connNo, 1000);
        assertEquals(connNo, clientEventListener.getConnCount());
        
        this.client.shutdown();
        this.server.shutdown();

        for (int c = 0; c < responseData.length; c++) {
            List receivedPackets = responseData[c];
            List expectedPackets = testData;
            assertEquals(expectedPackets.size(), receivedPackets.size());
            for (int p = 0; p < testData.size(); p++) {
                byte[] expected = (byte[]) testData.get(p);
                byte[] received = (byte[]) receivedPackets.get(p);
                
                assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    assertEquals(expected[i], received[i]);
                }
            }
        }
        
    }

    /**
     * This test case executes a series of simple (non-pipelined) POST requests 
     * with content length delimited content over multiple connections. 
     */
    public void testSimpleHttpPostsWithContentLength() throws Exception {
        
        final int connNo = 3;
        final int reqNo = 20;
        
        Random rnd = new Random();
        
        // Prepare some random data
        final List testData = new ArrayList(reqNo);
        for (int i = 0; i < reqNo; i++) {
            int size = rnd.nextInt(5000);
            byte[] data = new byte[size];
            rnd.nextBytes(data);
            testData.add(data);
        }
        
        List[] responseData = new List[connNo];
        for (int i = 0; i < responseData.length; i++) {
            responseData[i] = new ArrayList();
        }
        
        HttpRequestHandler requestHandler = new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request, 
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException, IOException {
                
                if (request instanceof HttpEntityEnclosingRequest) {
                    HttpEntity incoming = ((HttpEntityEnclosingRequest) request).getEntity();
                    byte[] data = EntityUtils.toByteArray(incoming);
                    
                    ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    outgoing.setChunked(false);
                    response.setEntity(outgoing);
                } else {
                    StringEntity outgoing = new StringEntity("No content"); 
                    response.setEntity(outgoing);
                }
            }
            
        };
        
        HttpRequestExecutionHandler requestExecutionHandler = new HttpRequestExecutionHandler() {

            public void initalizeContext(final HttpContext context, final Object attachment) {
                context.setAttribute("LIST", (List) attachment);
                context.setAttribute("REQ-COUNT", new Integer(0));
                context.setAttribute("RES-COUNT", new Integer(0));
            }

            public HttpRequest submitRequest(final HttpContext context) {
                int i = ((Integer) context.getAttribute("REQ-COUNT")).intValue();
                BasicHttpEntityEnclosingRequest post = null;
                if (i < reqNo) {
                    post = new BasicHttpEntityEnclosingRequest("POST", "/?" + i);
                    byte[] data = (byte[]) testData.get(i);
                    ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    post.setEntity(outgoing);
                    
                    context.setAttribute("REQ-COUNT", new Integer(i + 1));
                }
                return post;
            }
            
            public void handleResponse(final HttpResponse response, final HttpContext context) {
                NHttpConnection conn = (NHttpConnection) context.getAttribute(
                        HttpExecutionContext.HTTP_CONNECTION);
                
                List list = (List) context.getAttribute("LIST");
                int i = ((Integer) context.getAttribute("RES-COUNT")).intValue();
                i++;
                context.setAttribute("RES-COUNT", new Integer(i));

                try {
                    HttpEntity entity = response.getEntity();
                    byte[] data = EntityUtils.toByteArray(entity);
                    list.add(data);
                } catch (IOException ex) {
                    fail(ex.getMessage());
                }

                if (i < reqNo) {
                    conn.requestInput();
                } else {
                    try {
                        conn.close();
                    } catch (IOException ex) {
                        fail(ex.getMessage());
                    }
                }
            }
            
        };
        
        CountingEventListener serverEventListener = new CountingEventListener();
        CountingEventListener clientEventListener = new CountingEventListener();
        
        NHttpServiceHandler serviceHandler = createHttpServiceHandler(
                requestHandler, 
                null,
                serverEventListener);

        NHttpClientHandler clientHandler = createHttpClientHandler(
                requestExecutionHandler, 
                clientEventListener);

        this.server.start(serviceHandler);
        this.client.start(clientHandler);
        
        InetSocketAddress serverAddress = (InetSocketAddress) this.server.getSocketAddress();
        
        for (int i = 0; i < responseData.length; i++) {
            this.client.openConnection(
                    new InetSocketAddress("localhost", serverAddress.getPort()), 
                    responseData[i]);
        }
     
        clientEventListener.await(connNo, 1000);
        assertEquals(connNo, clientEventListener.getConnCount());
        
        this.client.shutdown();
        this.server.shutdown();

        for (int c = 0; c < responseData.length; c++) {
            List receivedPackets = responseData[c];
            List expectedPackets = testData;
            assertEquals(expectedPackets.size(), receivedPackets.size());
            for (int p = 0; p < testData.size(); p++) {
                byte[] expected = (byte[]) testData.get(p);
                byte[] received = (byte[]) receivedPackets.get(p);
                
                assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    assertEquals(expected[i], received[i]);
                }
            }
        }
        
    }

    /**
     * This test case executes a series of simple (non-pipelined) POST requests 
     * with chunk coded content content over multiple connections. 
     */
    public void testSimpleHttpPostsChunked() throws Exception {
        
        final int connNo = 3;
        final int reqNo = 20;
        
        Random rnd = new Random();
        
        // Prepare some random data
        final List testData = new ArrayList(reqNo);
        for (int i = 0; i < reqNo; i++) {
            int size = rnd.nextInt(20000);
            byte[] data = new byte[size];
            rnd.nextBytes(data);
            testData.add(data);
        }
        
        List[] responseData = new List[connNo];
        for (int i = 0; i < responseData.length; i++) {
            responseData[i] = new ArrayList();
        }
        
        HttpRequestHandler requestHandler = new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request, 
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException, IOException {
                
                if (request instanceof HttpEntityEnclosingRequest) {
                    HttpEntity incoming = ((HttpEntityEnclosingRequest) request).getEntity();
                    byte[] data = EntityUtils.toByteArray(incoming);
                    ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    outgoing.setChunked(true);
                    response.setEntity(outgoing);
                } else {
                    StringEntity outgoing = new StringEntity("No content"); 
                    response.setEntity(outgoing);
                }
            }
            
        };
        
        HttpRequestExecutionHandler requestExecutionHandler = new HttpRequestExecutionHandler() {

            public void initalizeContext(final HttpContext context, final Object attachment) {
                context.setAttribute("LIST", (List) attachment);
                context.setAttribute("REQ-COUNT", new Integer(0));
                context.setAttribute("RES-COUNT", new Integer(0));
            }

            public HttpRequest submitRequest(final HttpContext context) {
                int i = ((Integer) context.getAttribute("REQ-COUNT")).intValue();
                BasicHttpEntityEnclosingRequest post = null;
                if (i < reqNo) {
                    post = new BasicHttpEntityEnclosingRequest("POST", "/?" + i);
                    byte[] data = (byte[]) testData.get(i);
                    ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    outgoing.setChunked(true);
                    post.setEntity(outgoing);
                    
                    context.setAttribute("REQ-COUNT", new Integer(i + 1));
                }
                return post;
            }
            
            public void handleResponse(final HttpResponse response, final HttpContext context) {
                NHttpConnection conn = (NHttpConnection) context.getAttribute(
                        HttpExecutionContext.HTTP_CONNECTION);
                
                List list = (List) context.getAttribute("LIST");
                int i = ((Integer) context.getAttribute("RES-COUNT")).intValue();
                i++;
                context.setAttribute("RES-COUNT", new Integer(i));
                
                try {
                    HttpEntity entity = response.getEntity();
                    byte[] data = EntityUtils.toByteArray(entity);
                    list.add(data);
                } catch (IOException ex) {
                    fail(ex.getMessage());
                }

                if (i < reqNo) {
                    conn.requestInput();
                } else {
                    try {
                        conn.close();
                    } catch (IOException ex) {
                        fail(ex.getMessage());
                    }
                }
            }
            
        };
        
        CountingEventListener serverEventListener = new CountingEventListener();
        CountingEventListener clientEventListener = new CountingEventListener();
        
        NHttpServiceHandler serviceHandler = createHttpServiceHandler(
                requestHandler, 
                null,
                serverEventListener);

        NHttpClientHandler clientHandler = createHttpClientHandler(
                requestExecutionHandler, 
                clientEventListener);

        this.server.start(serviceHandler);
        this.client.start(clientHandler);
        
        InetSocketAddress serverAddress = (InetSocketAddress) this.server.getSocketAddress();
        
        for (int i = 0; i < responseData.length; i++) {
            this.client.openConnection(
                    new InetSocketAddress("localhost", serverAddress.getPort()), 
                    responseData[i]);
        }
     
        clientEventListener.await(connNo, 1000);
        assertEquals(connNo, clientEventListener.getConnCount());
        
        this.client.shutdown();
        this.server.shutdown();

        for (int c = 0; c < responseData.length; c++) {
            List receivedPackets = responseData[c];
            List expectedPackets = testData;
            assertEquals(expectedPackets.size(), receivedPackets.size());
            for (int p = 0; p < testData.size(); p++) {
                byte[] expected = (byte[]) testData.get(p);
                byte[] received = (byte[]) receivedPackets.get(p);
                
                assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    assertEquals(expected[i], received[i]);
                }
            }
        }
        
    }

    /**
     * This test case executes a series of simple (non-pipelined) HTTP/1.0 
     * POST requests over multiple persistent connections. 
     */
    public void testSimpleHttpPostsHTTP10() throws Exception {
        
        final int connNo = 3;
        final int reqNo = 20;
        
        Random rnd = new Random();
        
        // Prepare some random data
        final List testData = new ArrayList(reqNo);
        for (int i = 0; i < reqNo; i++) {
            int size = rnd.nextInt(5000);
            byte[] data = new byte[size];
            rnd.nextBytes(data);
            testData.add(data);
        }
        
        List[] responseData = new List[connNo];
        for (int i = 0; i < responseData.length; i++) {
            responseData[i] = new ArrayList();
        }
        
        HttpRequestHandler requestHandler = new HttpRequestHandler() {
            

            public void handle(
                    final HttpRequest request, 
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException, IOException {
                
                if (request instanceof HttpEntityEnclosingRequest) {
                    HttpEntity incoming = ((HttpEntityEnclosingRequest) request).getEntity();
                    byte[] data = EntityUtils.toByteArray(incoming);
                    
                    ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    outgoing.setChunked(false);
                    response.setEntity(outgoing);
                } else {
                    StringEntity outgoing = new StringEntity("No content"); 
                    response.setEntity(outgoing);
                }
            }
            
        };
        
        // Set protocol level to HTTP/1.0
        this.client.getParams().setParameter(
                HttpProtocolParams.PROTOCOL_VERSION, HttpVersion.HTTP_1_0);
        
        HttpRequestExecutionHandler requestExecutionHandler = new HttpRequestExecutionHandler() {

            public void initalizeContext(final HttpContext context, final Object attachment) {
                context.setAttribute("LIST", (List) attachment);
                context.setAttribute("REQ-COUNT", new Integer(0));
                context.setAttribute("RES-COUNT", new Integer(0));
            }

            public HttpRequest submitRequest(final HttpContext context) {
                int i = ((Integer) context.getAttribute("REQ-COUNT")).intValue();
                BasicHttpEntityEnclosingRequest post = null;
                if (i < reqNo) {
                    post = new BasicHttpEntityEnclosingRequest("POST", "/?" + i);
                    byte[] data = (byte[]) testData.get(i);
                    ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    post.setEntity(outgoing);
                    
                    context.setAttribute("REQ-COUNT", new Integer(i + 1));
                }
                return post;
            }
            
            public void handleResponse(final HttpResponse response, final HttpContext context) {
                NHttpConnection conn = (NHttpConnection) context.getAttribute(
                        HttpExecutionContext.HTTP_CONNECTION);

                List list = (List) context.getAttribute("LIST");
                int i = ((Integer) context.getAttribute("RES-COUNT")).intValue();
                i++;
                context.setAttribute("RES-COUNT", new Integer(i));

                try {
                    HttpEntity entity = response.getEntity();
                    byte[] data = EntityUtils.toByteArray(entity);
                    list.add(data);
                } catch (IOException ex) {
                    fail(ex.getMessage());
                }

                if (i < reqNo) {
                    conn.requestInput();
                } else {
                    try {
                        conn.close();
                    } catch (IOException ex) {
                        fail(ex.getMessage());
                    }
                }
            }
            
        };
        
        CountingEventListener serverEventListener = new CountingEventListener();
        CountingEventListener clientEventListener = new CountingEventListener();
        
        NHttpServiceHandler serviceHandler = createHttpServiceHandler(
                requestHandler, 
                null,
                serverEventListener);

        NHttpClientHandler clientHandler = createHttpClientHandler(
                requestExecutionHandler, 
                clientEventListener);

        this.server.start(serviceHandler);
        this.client.start(clientHandler);
        
        InetSocketAddress serverAddress = (InetSocketAddress) this.server.getSocketAddress();
        
        for (int i = 0; i < responseData.length; i++) {
            this.client.openConnection(
                    new InetSocketAddress("localhost", serverAddress.getPort()), 
                    responseData[i]);
        }
     
        clientEventListener.await(connNo, 1000);
        assertEquals(connNo, clientEventListener.getConnCount());
        
        this.client.shutdown();
        this.server.shutdown();

        for (int c = 0; c < responseData.length; c++) {
            List receivedPackets = responseData[c];
            List expectedPackets = testData;
            assertEquals(expectedPackets.size(), receivedPackets.size());
            for (int p = 0; p < testData.size(); p++) {
                byte[] expected = (byte[]) testData.get(p);
                byte[] received = (byte[]) receivedPackets.get(p);
                
                assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    assertEquals(expected[i], received[i]);
                }
            }
        }
        
    }

    /**
     * This test case executes a series of simple (non-pipelined) POST requests 
     * over multiple connections using the 'expect: continue' handshake. 
     */
    public void testHttpPostsWithExpectContinue() throws Exception {
        
        final int connNo = 3;
        final int reqNo = 20;
        
        Random rnd = new Random();
        
        // Prepare some random data
        final List testData = new ArrayList(reqNo);
        for (int i = 0; i < reqNo; i++) {
            int size = rnd.nextInt(20000);
            byte[] data = new byte[size];
            rnd.nextBytes(data);
            testData.add(data);
        }
        
        List[] responseData = new List[connNo];
        for (int i = 0; i < responseData.length; i++) {
            responseData[i] = new ArrayList();
        }
        
        HttpRequestHandler requestHandler = new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request, 
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException, IOException {
                
                if (request instanceof HttpEntityEnclosingRequest) {
                    HttpEntity incoming = ((HttpEntityEnclosingRequest) request).getEntity();
                    byte[] data = EntityUtils.toByteArray(incoming);
                    ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    outgoing.setChunked(true);
                    response.setEntity(outgoing);
                } else {
                    StringEntity outgoing = new StringEntity("No content"); 
                    response.setEntity(outgoing);
                }
            }
            
        };

        // Activate 'expect: continue' handshake
        this.client.getParams().setBooleanParameter(HttpProtocolParams.USE_EXPECT_CONTINUE, true);
        
        HttpRequestExecutionHandler requestExecutionHandler = new HttpRequestExecutionHandler() {

            public void initalizeContext(final HttpContext context, final Object attachment) {
                context.setAttribute("LIST", (List) attachment);
                context.setAttribute("REQ-COUNT", new Integer(0));
                context.setAttribute("RES-COUNT", new Integer(0));
            }

            public HttpRequest submitRequest(final HttpContext context) {
                int i = ((Integer) context.getAttribute("REQ-COUNT")).intValue();
                BasicHttpEntityEnclosingRequest post = null;
                if (i < reqNo) {
                    post = new BasicHttpEntityEnclosingRequest("POST", "/?" + i);
                    byte[] data = (byte[]) testData.get(i);
                    ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    outgoing.setChunked(true);
                    post.setEntity(outgoing);
                    
                    context.setAttribute("REQ-COUNT", new Integer(i + 1));
                }
                return post;
            }
            
            public void handleResponse(final HttpResponse response, final HttpContext context) {
                NHttpConnection conn = (NHttpConnection) context.getAttribute(
                        HttpExecutionContext.HTTP_CONNECTION);
                
                List list = (List) context.getAttribute("LIST");
                int i = ((Integer) context.getAttribute("RES-COUNT")).intValue();
                i++;
                context.setAttribute("RES-COUNT", new Integer(i));
                
                try {
                    HttpEntity entity = response.getEntity();
                    byte[] data = EntityUtils.toByteArray(entity);
                    list.add(data);
                } catch (IOException ex) {
                    fail(ex.getMessage());
                }

                if (i < reqNo) {
                    conn.requestInput();
                } else {
                    try {
                        conn.close();
                    } catch (IOException ex) {
                        fail(ex.getMessage());
                    }
                }
            }
            
        };
        
        CountingEventListener serverEventListener = new CountingEventListener();
        CountingEventListener clientEventListener = new CountingEventListener();
        
        NHttpServiceHandler serviceHandler = createHttpServiceHandler(
                requestHandler, 
                null,
                serverEventListener);

        NHttpClientHandler clientHandler = createHttpClientHandler(
                requestExecutionHandler, 
                clientEventListener);

        this.server.start(serviceHandler);
        this.client.start(clientHandler);
        
        InetSocketAddress serverAddress = (InetSocketAddress) this.server.getSocketAddress();
        
        for (int i = 0; i < responseData.length; i++) {
            this.client.openConnection(
                    new InetSocketAddress("localhost", serverAddress.getPort()), 
                    responseData[i]);
        }
     
        clientEventListener.await(connNo, 1000);
        assertEquals(connNo, clientEventListener.getConnCount());
        
        this.client.shutdown();
        this.server.shutdown();

        for (int c = 0; c < responseData.length; c++) {
            List receivedPackets = responseData[c];
            List expectedPackets = testData;
            assertEquals(expectedPackets.size(), receivedPackets.size());
            for (int p = 0; p < testData.size(); p++) {
                byte[] expected = (byte[]) testData.get(p);
                byte[] received = (byte[]) receivedPackets.get(p);
                
                assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    assertEquals(expected[i], received[i]);
                }
            }
        }
        
    }

    /**
     * This test case executes a series of simple (non-pipelined) POST requests 
     * over multiple connections that do not meet the target server expectations. 
     */
    public void testHttpPostsWithExpectationVerification() throws Exception {
        
        final int reqNo = 3;
        final List responses = new ArrayList(reqNo);
        
        HttpRequestHandler requestHandler = new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request, 
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException, IOException {
                
                StringEntity outgoing = new StringEntity("No content"); 
                response.setEntity(outgoing);
            }
            
        };
        
        HttpExpectationVerifier expectationVerifier = new HttpExpectationVerifier() {

            public void verify(
                    final HttpRequest request, 
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException {
                Header someheader = request.getFirstHeader("Secret");
                if (someheader != null) {
                    int secretNumber;
                    try {
                        secretNumber = Integer.parseInt(someheader.getValue());
                    } catch (NumberFormatException ex) {
                        response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
                        return;
                    }
                    if (secretNumber < 2) {
                        response.setStatusCode(HttpStatus.SC_EXPECTATION_FAILED);
                        ByteArrayEntity outgoing = new ByteArrayEntity(
                                EncodingUtils.getAsciiBytes("Wrong secret number")); 
                        response.setEntity(outgoing);
                    }
                }
            }
            
        };

        // Activate 'expect: continue' handshake
        this.client.getParams().setBooleanParameter(HttpProtocolParams.USE_EXPECT_CONTINUE, true);

        HttpRequestExecutionHandler requestExecutionHandler = new HttpRequestExecutionHandler() {

            public void initalizeContext(final HttpContext context, final Object attachment) {
                context.setAttribute("LIST", (List) attachment);
                context.setAttribute("REQ-COUNT", new Integer(0));
                context.setAttribute("RES-COUNT", new Integer(0));
            }

            public HttpRequest submitRequest(final HttpContext context) {
                int i = ((Integer) context.getAttribute("REQ-COUNT")).intValue();
                BasicHttpEntityEnclosingRequest post = null;
                if (i < reqNo) {
                    post = new BasicHttpEntityEnclosingRequest("POST", "/");
                    post.addHeader("Secret", Integer.toString(i));
                    ByteArrayEntity outgoing = new ByteArrayEntity(
                            EncodingUtils.getAsciiBytes("No content")); 
                    post.setEntity(outgoing);
                    
                    context.setAttribute("REQ-COUNT", new Integer(i + 1));
                }
                return post;
            }
            
            public void handleResponse(final HttpResponse response, final HttpContext context) {
                NHttpConnection conn = (NHttpConnection) context.getAttribute(
                        HttpExecutionContext.HTTP_CONNECTION);
                
                List list = (List) context.getAttribute("LIST");
                int i = ((Integer) context.getAttribute("RES-COUNT")).intValue();
                i++;
                context.setAttribute("RES-COUNT", new Integer(i));
                
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    try {
                        entity.consumeContent();
                    } catch (IOException ex) {
                        fail(ex.getMessage());
                    }
                }
                
                list.add(response);

                if (i < reqNo) {
                    conn.requestInput();
                } else {
                    try {
                        conn.close();
                    } catch (IOException ex) {
                        fail(ex.getMessage());
                    }
                }
            }
            
        };
        
        CountingEventListener serverEventListener = new CountingEventListener();
        CountingEventListener clientEventListener = new CountingEventListener();
        
        NHttpServiceHandler serviceHandler = createHttpServiceHandler(
                requestHandler, 
                expectationVerifier,
                serverEventListener);

        NHttpClientHandler clientHandler = createHttpClientHandler(
                requestExecutionHandler, 
                clientEventListener);

        this.server.start(serviceHandler);
        this.client.start(clientHandler);
        
        InetSocketAddress serverAddress = (InetSocketAddress) this.server.getSocketAddress();
        
        this.client.openConnection(
                new InetSocketAddress("localhost", serverAddress.getPort()), 
                responses);
     
        clientEventListener.await(1, 1000);
        
        this.client.shutdown();
        this.server.shutdown();

        assertEquals(reqNo, responses.size());
        HttpResponse response = (HttpResponse) responses.get(0);
        assertEquals(HttpStatus.SC_EXPECTATION_FAILED, response.getStatusLine().getStatusCode());
        response = (HttpResponse) responses.get(1);
        assertEquals(HttpStatus.SC_EXPECTATION_FAILED, response.getStatusLine().getStatusCode());
        response = (HttpResponse) responses.get(2);
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
    }
    
    /**
     * This test case executes a series of simple (non-pipelined) HEAD requests 
     * over multiple connections. 
     */
    public void testSimpleHttpHeads() throws Exception {
        
        final int connNo = 3;
        final int reqNo = 20;
        
        final String[] method = new String[1];
        
        Random rnd = new Random();
        
        // Prepare some random data
        final List testData = new ArrayList(reqNo);
        for (int i = 0; i < reqNo; i++) {
            int size = rnd.nextInt(5000);
            byte[] data = new byte[size];
            rnd.nextBytes(data);
            testData.add(data);
        }
        
        List[] responseData = new List[connNo];
        for (int i = 0; i < responseData.length; i++) {
            responseData[i] = new ArrayList();
        }
        
        HttpRequestHandler requestHandler = new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request, 
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException, IOException {
                
                String s = request.getRequestLine().getUri();
                URI uri;
                try {
                    uri = new URI(s);
                } catch (URISyntaxException ex) {
                    throw new HttpException("Invalid request URI: " + s);
                }
                int index = Integer.parseInt(uri.getQuery());

                byte[] data = (byte []) testData.get(index);
                ByteArrayEntity entity = new ByteArrayEntity(data); 
                response.setEntity(entity);
            }
            
        };
        
        HttpRequestExecutionHandler requestExecutionHandler = new HttpRequestExecutionHandler() {

            public void initalizeContext(final HttpContext context, final Object attachment) {
                context.setAttribute("LIST", (List) attachment);
                context.setAttribute("REQ-COUNT", new Integer(0));
                context.setAttribute("RES-COUNT", new Integer(0));
            }

            public HttpRequest submitRequest(final HttpContext context) {
                int i = ((Integer) context.getAttribute("REQ-COUNT")).intValue();
                BasicHttpRequest request = null;
                if (i < reqNo) {
                    request = new BasicHttpRequest(method[0], "/?" + i);
                    context.setAttribute("REQ-COUNT", new Integer(i + 1));
                }
                return request;
            }
            
            public void handleResponse(final HttpResponse response, final HttpContext context) {
                NHttpConnection conn = (NHttpConnection) context.getAttribute(
                        HttpExecutionContext.HTTP_CONNECTION);
                
                List list = (List) context.getAttribute("LIST");
                int i = ((Integer) context.getAttribute("RES-COUNT")).intValue();
                i++;
                context.setAttribute("RES-COUNT", new Integer(i));

                list.add(response);

                if (i < reqNo) {
                    conn.requestInput();
                } else {
                    try {
                        conn.close();
                    } catch (IOException ex) {
                        fail(ex.getMessage());
                    }
                }
            }
            
        };
        
        CountingEventListener serverEventListener = new CountingEventListener();
        CountingEventListener clientEventListener = new CountingEventListener();
        
        NHttpServiceHandler serviceHandler = createHttpServiceHandler(
                requestHandler, 
                null,
                serverEventListener);

        NHttpClientHandler clientHandler = createHttpClientHandler(
                requestExecutionHandler, 
                clientEventListener);

        this.server.start(serviceHandler);
        this.client.start(clientHandler);
        
        InetSocketAddress serverAddress = (InetSocketAddress) this.server.getSocketAddress();

        method[0] = "GET";
        
        for (int i = 0; i < responseData.length; i++) {
            this.client.openConnection(
                    new InetSocketAddress("localhost", serverAddress.getPort()), 
                    responseData[i]);
        }
     
        clientEventListener.await(connNo, 1000);
        assertEquals(connNo, clientEventListener.getConnCount());

        List[] responseDataGET = responseData; 

        method[0] = "HEAD";

        responseData = new List[connNo];
        for (int i = 0; i < responseData.length; i++) {
            responseData[i] = new ArrayList();
        }
        
        for (int i = 0; i < responseData.length; i++) {
            this.client.openConnection(
                    new InetSocketAddress("localhost", serverAddress.getPort()), 
                    responseData[i]);
        }
     
        clientEventListener.await(connNo * 2, 1000);
        assertEquals(connNo * 2, clientEventListener.getConnCount());
        
        this.client.shutdown();
        this.server.shutdown();

        for (int c = 0; c < responseData.length; c++) {
            List headResponses = responseData[c];
            List getResponses = responseDataGET[c];
            List expectedPackets = testData;
            assertEquals(expectedPackets.size(), headResponses.size());
            assertEquals(expectedPackets.size(), getResponses.size());
            for (int p = 0; p < testData.size(); p++) {
                HttpResponse getResponse = (HttpResponse) getResponses.get(p);
                HttpResponse headResponse = (HttpResponse) headResponses.get(p);
                assertEquals(null, headResponse.getEntity());
                
                Header[] getHeaders = getResponse.getAllHeaders();
                Header[] headHeaders = headResponse.getAllHeaders();
                assertEquals(getHeaders.length, headHeaders.length);
                for (int j = 0; j < getHeaders.length; j++) {
                    if ("Date".equals(getHeaders[j].getName())) {
                        continue;
                    }
                    assertEquals(getHeaders[j].toString(), headHeaders[j].toString());
                }
            }
        }
    }
    
}
