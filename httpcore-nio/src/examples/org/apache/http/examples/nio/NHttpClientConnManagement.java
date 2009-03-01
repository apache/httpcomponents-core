/*
 * $HeadURL: http://svn.apache.org/repos/asf/httpcomponents/httpcore/trunk/module-nio/src/examples/org/apache/http/examples/nio/NHttpClient.java $
 * $Revision: 741603 $
 * $Date: 2009-02-06 16:57:10 +0100 (Fri, 06 Feb 2009) $
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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.nio.DefaultClientIOEventDispatch;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.protocol.BufferingHttpClientHandler;
import org.apache.http.nio.protocol.EventListener;
import org.apache.http.nio.protocol.HttpRequestExecutionHandler;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.nio.reactor.SessionRequest;
import org.apache.http.nio.reactor.SessionRequestCallback;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.util.EntityUtils;

/**
 * Example of a very simple asynchronous connection manager that maintains a pool of persistent 
 * connections to one target host.
 * <p>
 * Please note the purpose of this application is demonstrate the usage of HttpCore APIs.
 * It is NOT intended to demonstrate the most efficient way of building an HTTP client. 
 *
 * 
 * @version $Revision:$
 */
public class NHttpClientConnManagement {

    public static void main(String[] args) throws Exception {
        HttpParams params = new BasicHttpParams();
        params
            .setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 5000)
            .setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 10000)
            .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
            .setParameter(CoreProtocolPNames.USER_AGENT, "HttpComponents/1.1");

        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        httpproc.addInterceptor(new RequestContent());
        httpproc.addInterceptor(new RequestTargetHost());
        httpproc.addInterceptor(new RequestConnControl());
        httpproc.addInterceptor(new RequestUserAgent());
        httpproc.addInterceptor(new RequestExpectContinue());
        
        // Set up protocol handler
        BufferingHttpClientHandler protocolHandler = new BufferingHttpClientHandler(
                httpproc,
                new MyHttpRequestExecutionHandler(),
                new DefaultConnectionReuseStrategy(),
                params);
        protocolHandler.setEventListener(new EventLogger());
        
        // Limit the total maximum of concurrent connections to 5
        int maxTotalConnections = 5;
        
        // Use the connection manager to maintain a pool of connections to localhost:8080
        final AsyncConnectionManager connMgr = new AsyncConnectionManager(
                new HttpHost("localhost", 8080),
                maxTotalConnections,
                protocolHandler,
                params);         
        
        // Start the I/O reactor in a separate thread 
        Thread t = new Thread(new Runnable() {
         
            public void run() {
                try {
                    connMgr.execute();
                } catch (InterruptedIOException ex) {
                    System.err.println("Interrupted");
                } catch (IOException e) {
                    System.err.println("I/O error: " + e.getMessage());
                }
                System.out.println("I/O reactor terminated");
            }
            
        });
        t.start();
        
        // Submit 50 requests using maximum 5 concurrent connections 
        Queue<RequestHandle> queue = new LinkedList<RequestHandle>();
        for (int i = 0; i < 50; i++) {
            AsyncConnectionRequest connRequest = connMgr.requestConnection();
            connRequest.waitFor();
            NHttpClientConnection conn = connRequest.getConnection();
            if (conn == null) {
                System.err.println("Failed to obtain connection");
                break;
            }
            
            HttpContext context = conn.getContext();
            BasicHttpRequest httpget = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
            RequestHandle handle = new RequestHandle(connMgr, conn); 
            
            context.setAttribute("request", httpget);
            context.setAttribute("request-handle", handle);
            
            queue.add(handle);            
            conn.requestOutput();
        }
        
        // Wait until all requests have been completed 
        while (!queue.isEmpty()) {
            RequestHandle handle = queue.remove();
            handle.waitFor();
        }
        
        // Give the I/O reactor 10 sec to shut down 
        connMgr.shutdown(10000);
        System.out.println("Done");
    }
    
    static class MyHttpRequestExecutionHandler implements HttpRequestExecutionHandler {

        public MyHttpRequestExecutionHandler() {
            super();
        }
        
        public void initalizeContext(final HttpContext context, final Object attachment) {
        }
        
        public void finalizeContext(final HttpContext context) {
            RequestHandle handle = (RequestHandle) context.removeAttribute("request-handle");
            if (handle != null) {
                handle.cancel();
            }
        }

        public HttpRequest submitRequest(final HttpContext context) {
            HttpRequest request = (HttpRequest) context.removeAttribute("request");
            return request;
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
            RequestHandle handle = (RequestHandle) context.removeAttribute("request-handle");
            if (handle != null) {
                handle.completed();
            }
        }
        
    }
    
    static class AsyncConnectionRequest {

        private volatile boolean completed;    
        private volatile NHttpClientConnection conn;    
        
        public AsyncConnectionRequest() {
            super();
        }
        
        public boolean isCompleted() {
            return this.completed;
        }
        
        public void setConnection(NHttpClientConnection conn) {
            if (this.completed) {
                return;
            }
            this.completed = true;
            synchronized (this) {
                this.conn = conn;
                notifyAll();
            }
        }

        public NHttpClientConnection getConnection() {
            return this.conn;
        }
        
        public void cancel() {
            if (this.completed) {
                return;
            }
            this.completed = true;
            synchronized (this) {
                notifyAll();
            }
        }
        
        public void waitFor() throws InterruptedException {
            if (this.completed) {
                return;
            }
            synchronized (this) {
                while (!this.completed) {
                    wait();
                }
            }
        }
        
    }

    static class AsyncConnectionManager {

        private final HttpHost target; 
        private final int maxConnections; 
        private final NHttpClientHandler handler;
        private final HttpParams params;
        private final ConnectingIOReactor ioreactor;
        private final Object lock;
        private final Set<NHttpClientConnection> allConns;
        private final Queue<NHttpClientConnection> availableConns;
        private final Queue<AsyncConnectionRequest> pendingRequests;
        
        private volatile boolean shutdown; 
        
        public AsyncConnectionManager(
                HttpHost target,
                int maxConnections,
                NHttpClientHandler handler, 
                HttpParams params) throws IOReactorException {
            super();
            this.target = target;
            this.maxConnections = maxConnections;
            this.handler = handler;
            this.params = params;
            this.lock = new Object();
            this.allConns = new HashSet<NHttpClientConnection>();
            this.availableConns = new LinkedList<NHttpClientConnection>();
            this.pendingRequests = new LinkedList<AsyncConnectionRequest>();
            this.ioreactor = new DefaultConnectingIOReactor(2, params);
        }

        public void execute() throws IOException {
            IOEventDispatch dispatch = new DefaultClientIOEventDispatch(
                    new ManagedClientHandler(this.handler, this), this.params);
            this.ioreactor.execute(dispatch);
        }
        
        public void shutdown(long waitMs) throws IOException {
            synchronized (this.lock) {
                if (!this.shutdown) {
                    this.shutdown = true;
                    while (!this.pendingRequests.isEmpty()) {
                        AsyncConnectionRequest request = this.pendingRequests.remove();
                        request.cancel();
                    }
                    this.availableConns.clear();
                    this.allConns.clear();
                }
            }
            this.ioreactor.shutdown(waitMs);
        }
        
        void addConnection(NHttpClientConnection conn) {
            if (conn == null) {
                return;
            }
            if (this.shutdown) {
                return;
            }
            synchronized (this.lock) {
                this.allConns.add(conn);
            }
        }
        
        void removeConnection(NHttpClientConnection conn) {
            if (conn == null) {
                return;
            }
            if (this.shutdown) {
                return;
            }
            synchronized (this.lock) {
                if (this.allConns.remove(conn)) {
                    this.availableConns.remove(conn);
                }
                processRequests();                
            }
        }
        
        public AsyncConnectionRequest requestConnection() {
            if (this.shutdown) {
                throw new IllegalStateException("Connection manager has been shut down");
            }
            AsyncConnectionRequest request = new AsyncConnectionRequest();
            synchronized (this.lock) {
                while (!this.availableConns.isEmpty()) {
                    NHttpClientConnection conn = this.availableConns.remove();
                    if (conn.isOpen()) {
                        System.out.println("Re-using persistent connection");
                        request.setConnection(conn);
                        break;
                    } else {
                        this.allConns.remove(conn);
                    }
                }
                if (!request.isCompleted()) {
                    this.pendingRequests.add(request);
                    processRequests();                
                }
            }
            return request;
        }
        
        public void releaseConnection(NHttpClientConnection conn) {
            if (conn == null) {
                return;
            }
            if (this.shutdown) {
                return;
            }
            synchronized (this.lock) {
                if (this.allConns.contains(conn)) {
                    if (conn.isOpen()) {
                        conn.setSocketTimeout(0);
                        AsyncConnectionRequest request = this.pendingRequests.poll();
                        if (request != null) {
                            System.out.println("Re-using persistent connection");
                            request.setConnection(conn);
                        } else {
                            this.availableConns.add(conn);
                        }
                    } else {
                        this.allConns.remove(conn);
                        processRequests();
                    }
                }
            }
        }
        
        private void processRequests() {
            while (this.allConns.size() < this.maxConnections) {
                AsyncConnectionRequest request = this.pendingRequests.poll();
                if (request == null) {
                    break;
                }
                InetSocketAddress address = new InetSocketAddress(
                        this.target.getHostName(),
                        this.target.getPort());
                ConnRequestCallback callback = new ConnRequestCallback(request);
                System.out.println("Opening new connection");
                this.ioreactor.connect(address, null, request, callback);
            }
        }
        
    }
    
    static class ManagedClientHandler implements NHttpClientHandler {

        private final NHttpClientHandler handler;
        private final AsyncConnectionManager connMgr;
        
        public ManagedClientHandler(NHttpClientHandler handler, AsyncConnectionManager connMgr) {
            super();
            this.handler = handler;
            this.connMgr = connMgr;
        }
        
        public void connected(NHttpClientConnection conn, Object attachment) {
            AsyncConnectionRequest request = (AsyncConnectionRequest) attachment;
            this.handler.connected(conn, attachment);
            this.connMgr.addConnection(conn);
            request.setConnection(conn);
        }

        public void closed(NHttpClientConnection conn) {
            this.connMgr.removeConnection(conn);
            this.handler.closed(conn);
        }

        public void requestReady(NHttpClientConnection conn) {
            this.handler.requestReady(conn);
        }

        public void outputReady(NHttpClientConnection conn, ContentEncoder encoder) {
            this.handler.outputReady(conn, encoder);
        }

        public void responseReceived(NHttpClientConnection conn) {
            this.handler.responseReceived(conn);
        }

        public void inputReady(NHttpClientConnection conn, ContentDecoder decoder) {
            this.handler.inputReady(conn, decoder);
        }

        public void exception(NHttpClientConnection conn, HttpException ex) {
            this.handler.exception(conn, ex);
        }

        public void exception(NHttpClientConnection conn, IOException ex) {
            this.handler.exception(conn, ex);
        }

        public void timeout(NHttpClientConnection conn) {
            this.handler.timeout(conn);
        }
        
    }

    static class RequestHandle {

        private final AsyncConnectionManager connMgr;    
        private final NHttpClientConnection conn;    
        
        private volatile boolean completed;    
        
        public RequestHandle(AsyncConnectionManager connMgr, NHttpClientConnection conn) {
            super();
            this.connMgr = connMgr;
            this.conn = conn;
        }
        
        public boolean isCompleted() {
            return this.completed;
        }
        
        public void completed() {
            if (this.completed) {
                return;
            }
            this.completed = true;
            this.connMgr.releaseConnection(this.conn);
            synchronized (this) {
                notifyAll();
            }
        }
        
        public void cancel() {
            if (this.completed) {
                return;
            }
            this.completed = true;
            synchronized (this) {
                notifyAll();
            }
        }
        
        public void waitFor() throws InterruptedException {
            if (this.completed) {
                return;
            }
            synchronized (this) {
                while (!this.completed) {
                    wait();
                }
            }
        }

    }

    static class ConnRequestCallback implements SessionRequestCallback {

        private final AsyncConnectionRequest request;
        
        ConnRequestCallback(AsyncConnectionRequest request) {
            super();
            this.request = request;
        }
        
        public void completed(SessionRequest request) {
            System.out.println(request.getRemoteAddress() +  " - request successful");
        }

        public void cancelled(SessionRequest request) {
            System.out.println(request.getRemoteAddress() +  " - request cancelled");
            this.request.cancel();
        }

        public void failed(SessionRequest request) {
            System.err.println(request.getRemoteAddress() +  " - request failed");
            IOException ex = request.getException();
            if (ex != null) {
                ex.printStackTrace();
            }
            this.request.cancel();
        }

        public void timeout(SessionRequest request) {
            System.out.println(request.getRemoteAddress() +  " - request timed out");
            this.request.cancel();
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
    
}
