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

package org.apache.http.impl.nio.reactor;

import java.io.IOException;
import java.net.InetSocketAddress;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.http.HttpCoreNIOTestBase;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.mockup.RequestCount;
import org.apache.http.mockup.SimpleEventListener;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.NHttpServiceHandler;
import org.apache.http.nio.protocol.EventListener;
import org.apache.http.nio.protocol.HttpRequestExecutionHandler;
import org.apache.http.nio.reactor.IOReactorExceptionHandler;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

/**
 * Simple tests for {@link DefaultListeningIOReactor}.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * 
 * @version $Id$
 */
public class TestDefaultIOReactors extends HttpCoreNIOTestBase {

    // ------------------------------------------------------------ Constructor
    public TestDefaultIOReactors(String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestDefaultIOReactors.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestDefaultIOReactors.class);
    }

    public void testRestartListeningIOReactor() throws Exception {
        HttpParams params = new BasicHttpParams();
        
        DefaultListeningIOReactor ioReactor = new DefaultListeningIOReactor(1, params);
        ioReactor.listen(new InetSocketAddress(9999));
        ioReactor.shutdown();
        
        ioReactor = new DefaultListeningIOReactor(1, params);
        ioReactor.listen(new InetSocketAddress(9999));
        ioReactor.shutdown();         
    }
    
    public void testGracefulShutdown() throws Exception {

        // Open some connection and make sure 
        // they get cleanly closed upon shutdown
        
        final int connNo = 10;
        final RequestCount requestConns = new RequestCount(connNo); 
        final RequestCount closedServerConns = new RequestCount(connNo); 
        final RequestCount closedClientConns = new RequestCount(connNo); 
        
        HttpRequestHandler requestHandler = new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request, 
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException, IOException {
            }
            
        };
        
        HttpRequestExecutionHandler requestExecutionHandler = new HttpRequestExecutionHandler() {

            public void initalizeContext(final HttpContext context, final Object attachment) {
            }

            public void finalizeContext(final HttpContext context) {
            }

            public HttpRequest submitRequest(final HttpContext context) {
                Boolean b = ((Boolean) context.getAttribute("done"));
                if (b == null) {
                    BasicHttpRequest get = new BasicHttpRequest("GET", "/");
                    context.setAttribute("done", Boolean.TRUE);
                    return get;
                } else {
                    return null;
                }
            }
            
            public void handleResponse(final HttpResponse response, final HttpContext context) {
                requestConns.decrement();                    
            }
            
        };
     
        EventListener serverEventListener = new SimpleEventListener() {

            public void connectionClosed(NHttpConnection conn) {
                closedServerConns.decrement();
                super.connectionClosed(conn);
            }
            
        };
        
        NHttpServiceHandler serviceHandler = createHttpServiceHandler(
                requestHandler, 
                null,
                serverEventListener);
        
        EventListener clientEventListener = new SimpleEventListener() {

            public void connectionClosed(NHttpConnection conn) {
                closedClientConns.decrement();
                super.connectionClosed(conn);
            }
            
        };
        
        NHttpClientHandler clientHandler = createHttpClientHandler(
                requestExecutionHandler,
                clientEventListener);

        this.server.start(serviceHandler);
        this.client.start(clientHandler);
        
        
        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();
        
        for (int i = 0; i < connNo; i++) {
            this.client.openConnection(
                    new InetSocketAddress("localhost", serverAddress.getPort()), 
                    null);
        }
     
        requestConns.await(10000);
        assertEquals(0, requestConns.getValue());
     
        this.client.shutdown();
        this.server.shutdown();
        
        closedClientConns.await(10000);
        assertEquals(0, closedClientConns.getValue());
     
        closedServerConns.await(10000);
        assertEquals(0, closedServerConns.getValue());
    }
    
    public void testRuntimeException() throws Exception {

        HttpRequestHandler requestHandler = new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request, 
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException, IOException {
                throw new IllegalStateException("Oppsie!!!");
            }
            
        };
        
        HttpRequestExecutionHandler requestExecutionHandler = new HttpRequestExecutionHandler() {

            public void initalizeContext(final HttpContext context, final Object attachment) {
            }

            public void finalizeContext(final HttpContext context) {
            }

            public HttpRequest submitRequest(final HttpContext context) {
                Boolean b = ((Boolean) context.getAttribute("done"));
                if (b == null) {
                    BasicHttpRequest get = new BasicHttpRequest("GET", "/");
                    context.setAttribute("done", Boolean.TRUE);
                    return get;
                } else {
                    return null;
                }
            }
            
            public void handleResponse(final HttpResponse response, final HttpContext context) {
            }
            
        };
     
        NHttpServiceHandler serviceHandler = createHttpServiceHandler(
                requestHandler, 
                null,
                new SimpleEventListener());
        
        NHttpClientHandler clientHandler = createHttpClientHandler(
                requestExecutionHandler,
                new SimpleEventListener());

        this.server.start(serviceHandler);
        this.client.start(clientHandler);
        
        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();
        
        this.client.openConnection(
                new InetSocketAddress("localhost", serverAddress.getPort()), 
                null);
     
        this.server.join(20000);
        
        Exception ex = this.server.getException();
        assertNotNull(ex);
        assertTrue(ex instanceof IllegalStateException);
        // I/O reactor shut down itself
        assertEquals(IOReactorStatus.SHUT_DOWN, this.server.getStatus());
        
        this.client.shutdown();
        this.server.shutdown();
    }

    public void testUnhandledRuntimeException() throws Exception {

        final RequestCount requestConns = new RequestCount(1); 
        
        HttpRequestHandler requestHandler = new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request, 
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException, IOException {
                throw new IllegalStateException("Oppsie!!!");
            }
            
        };
        
        HttpRequestExecutionHandler requestExecutionHandler = new HttpRequestExecutionHandler() {

            public void initalizeContext(final HttpContext context, final Object attachment) {
            }

            public void finalizeContext(final HttpContext context) {
            }

            public HttpRequest submitRequest(final HttpContext context) {
                Boolean b = ((Boolean) context.getAttribute("done"));
                if (b == null) {
                    BasicHttpRequest get = new BasicHttpRequest("GET", "/");
                    context.setAttribute("done", Boolean.TRUE);
                    return get;
                } else {
                    return null;
                }
            }
            
            public void handleResponse(final HttpResponse response, final HttpContext context) {
            }
            
        };
     
        IOReactorExceptionHandler exceptionHandler = new IOReactorExceptionHandler() {

            public boolean handle(final IOException ex) {
                return false;
            }

            public boolean handle(final RuntimeException ex) {
                requestConns.decrement();                    
                return false;
            }
          
        };
        
        NHttpServiceHandler serviceHandler = createHttpServiceHandler(
                requestHandler, 
                null,
                new SimpleEventListener());
        
        NHttpClientHandler clientHandler = createHttpClientHandler(
                requestExecutionHandler,
                new SimpleEventListener());

        this.server.setExceptionHandler(exceptionHandler);
        
        this.server.start(serviceHandler);
        this.client.start(clientHandler);
        
        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();
        
        this.client.openConnection(
                new InetSocketAddress("localhost", serverAddress.getPort()), 
                null);
     
        requestConns.await(10000);
        assertEquals(0, requestConns.getValue());
        
        this.server.join(20000);
        
        Exception ex = this.server.getException();
        assertNotNull(ex);
        assertTrue(ex instanceof IllegalStateException);
        // I/O reactor shut down itself
        assertEquals(IOReactorStatus.SHUT_DOWN, this.server.getStatus());
        
        this.client.shutdown();
        this.server.shutdown();
    }

    public void testHandledRuntimeException() throws Exception {

        final RequestCount requestConns = new RequestCount(1); 
        
        HttpRequestHandler requestHandler = new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request, 
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException, IOException {
                throw new IllegalStateException("Oppsie!!!");
            }
            
        };
        
        HttpRequestExecutionHandler requestExecutionHandler = new HttpRequestExecutionHandler() {

            public void initalizeContext(final HttpContext context, final Object attachment) {
            }

            public void finalizeContext(final HttpContext context) {
            }

            public HttpRequest submitRequest(final HttpContext context) {
                Boolean b = ((Boolean) context.getAttribute("done"));
                if (b == null) {
                    BasicHttpRequest get = new BasicHttpRequest("GET", "/");
                    context.setAttribute("done", Boolean.TRUE);
                    return get;
                } else {
                    return null;
                }
            }
            
            public void handleResponse(final HttpResponse response, final HttpContext context) {
            }
            
        };
     
        IOReactorExceptionHandler exceptionHandler = new IOReactorExceptionHandler() {

            public boolean handle(final IOException ex) {
                return false;
            }

            public boolean handle(final RuntimeException ex) {
                requestConns.decrement();                    
                return true;
            }
          
        };
        
        NHttpServiceHandler serviceHandler = createHttpServiceHandler(
                requestHandler, 
                null,
                new SimpleEventListener());
        
        NHttpClientHandler clientHandler = createHttpClientHandler(
                requestExecutionHandler,
                new SimpleEventListener());

        this.server.setExceptionHandler(exceptionHandler);
        
        this.server.start(serviceHandler);
        this.client.start(clientHandler);
        
        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();
        
        this.client.openConnection(
                new InetSocketAddress("localhost", serverAddress.getPort()), 
                null);
     
        requestConns.await(10000);
        assertEquals(0, requestConns.getValue());
        
        this.server.join(1000);
        
        assertEquals(IOReactorStatus.ACTIVE, this.server.getStatus());
        assertNull(this.server.getException());
        
        this.client.shutdown();
        this.server.shutdown();
    }
    
}
