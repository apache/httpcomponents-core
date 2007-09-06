/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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
package org.apache.http.examples.nio;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.impl.nio.DefaultClientIOEventDispatch;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.protocol.BufferingHttpClientHandler;
import org.apache.http.nio.protocol.EventListener;
import org.apache.http.nio.protocol.HttpRequestExecutionHandler;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.SessionRequest;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.util.EntityUtils;

public class NHttpClient {

    public static void main(String[] args) throws Exception {
        HttpParams params = new BasicHttpParams(null);
        params
            .setIntParameter(HttpConnectionParams.SO_TIMEOUT, 5000)
            .setIntParameter(HttpConnectionParams.CONNECTION_TIMEOUT, 10000)
            .setIntParameter(HttpConnectionParams.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(HttpConnectionParams.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(HttpConnectionParams.TCP_NODELAY, true)
            .setParameter(HttpProtocolParams.USER_AGENT, "Jakarta-HttpComponents-NIO/1.1");

        final ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor(2, params);

        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        httpproc.addInterceptor(new RequestContent());
        httpproc.addInterceptor(new RequestTargetHost());
        httpproc.addInterceptor(new RequestConnControl());
        httpproc.addInterceptor(new RequestUserAgent());
        httpproc.addInterceptor(new RequestExpectContinue());
        
        // We are going to use this object to synchronize between the 
        // I/O event and main threads
        RequestCount requestCount = new RequestCount(3);
        
        BufferingHttpClientHandler handler = new BufferingHttpClientHandler(
                httpproc,
                new MyHttpRequestExecutionHandler(requestCount),
                new DefaultConnectionReuseStrategy(),
                params);

        handler.setEventListener(new EventLogger());
        
        final IOEventDispatch ioEventDispatch = new DefaultClientIOEventDispatch(handler, params);
        
        Thread t = new Thread(new Runnable() {
         
            public void run() {
                try {
                    ioReactor.execute(ioEventDispatch);
                } catch (InterruptedIOException ex) {
                    System.err.println("Interrupted");
                } catch (IOException e) {
                    System.err.println("I/O error: " + e.getMessage());
                }
                System.out.println("Shutdown");
            }
            
        });
        t.start();

        SessionRequest[] reqs = new SessionRequest[requestCount.getValue()];
        reqs[0] = ioReactor.connect(
                new InetSocketAddress("www.yahoo.com", 80), 
                null, 
                new HttpHost("www.yahoo.com"),
                null);
        reqs[1] = ioReactor.connect(
                new InetSocketAddress("www.google.com", 80), 
                null,
                new HttpHost("www.google.ch"),
                null);
        reqs[2] = ioReactor.connect(
                new InetSocketAddress("www.apache.org", 80), 
                null,
                new HttpHost("www.apache.org"),
                null);
     
        // Block until all connections signal
        // completion of the request execution
        synchronized (requestCount) {
            while (requestCount.getValue() > 0) {
                requestCount.wait();
            }
        }

        System.out.println("Shutting down I/O reactor");
        
        ioReactor.shutdown();
        
        System.out.println("Done");
    }
    
    static class MyHttpRequestExecutionHandler implements HttpRequestExecutionHandler {

        private final static String DONE = "done";
        
        private final RequestCount requestCount;
        
        public MyHttpRequestExecutionHandler(final RequestCount requestCount) {
            super();
            this.requestCount = requestCount;
        }
        
        public void initalizeContext(final HttpContext context, final Object attachment) {
            HttpHost targetHost = (HttpHost) attachment;
            context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, targetHost);
        }

        public HttpRequest submitRequest(final HttpContext context) {
            HttpHost targetHost = (HttpHost) context.getAttribute(
                    ExecutionContext.HTTP_TARGET_HOST);
            Object token = context.getAttribute(DONE);
            if (token == null) {
                // Stick some object into the context
                context.setAttribute(DONE, Boolean.TRUE);

                System.out.println("--------------");
                System.out.println("Sending request to " + targetHost);
                System.out.println("--------------");
                
                return new BasicHttpRequest("GET", "/");
            } else {
                // No new request to submit
                return null;
            }
        }
        
        public void handleResponse(final HttpResponse response, final HttpContext context) {
            HttpEntity entity = response.getEntity();
            try {
                String content = EntityUtils.toString(entity);
                
                System.out.println("--------------");
                System.out.println(response.getStatusLine());
                System.out.println("--------------");
                System.out.println("Document length: " + content.length());
                System.out.println("--------------");
            } catch (IOException ex) {
                System.err.println("I/O error: " + ex.getMessage());
            }

            // Signal completion of the request execution
            synchronized (this.requestCount) {
                this.requestCount.decrement();
                this.requestCount.notifyAll();
            }
        }
        
    }
    
    static class EventLogger implements EventListener {

        public void connectionOpen(final NHttpConnection conn) {
            System.out.println("Connection open: " + conn);
        }

        public void connectionTimeout(final NHttpConnection conn) {
            System.out.println("Connection timed out: " + conn);
        }

        public void connectionClosed(final NHttpConnection conn) {
            System.out.println("Connection closed: " + conn);
        }

        public void fatalIOException(final IOException ex, final NHttpConnection conn) {
            System.err.println("I/O error: " + ex.getMessage());
        }

        public void fatalProtocolException(final HttpException ex, final NHttpConnection conn) {
            System.err.println("HTTP error: " + ex.getMessage());
        }
        
    }

    static class RequestCount {
        
        private int value;
        
        public RequestCount(int initialValue) {
            this.value = initialValue;
        }
        
        public int getValue() {
            return this.value;
        }
        
        public void decrement() {
            this.value--;
        }
        
    }
    
}
