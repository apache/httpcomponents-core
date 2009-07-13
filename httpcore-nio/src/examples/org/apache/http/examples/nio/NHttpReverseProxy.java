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
package org.apache.http.examples.nio;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpConnection;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.nio.DefaultClientIOEventDispatch;
import org.apache.http.impl.nio.DefaultServerIOEventDispatch;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.DefaultListeningIOReactor;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.NHttpServiceHandler;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.ListeningIOReactor;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.DefaultedHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.SyncBasicHttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.ImmutableHttpProcessor;
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
 * Rudimentary HTTP/1.1 reverse proxy based on the non-blocking I/O model.
 * <p>
 * Please note the purpose of this application is demonstrate the usage of HttpCore APIs.
 * It is NOT intended to demonstrate the most efficient way of building an HTTP reverse proxy. 
 * 
 *
 */
public class NHttpReverseProxy {

    public static void main(String[] args) throws Exception {
        
        if (args.length < 1) {
            System.out.println("Usage: NHttpReverseProxy <hostname> [port]");
            System.exit(1);
        }
        String hostname = args[0];
        int port = 80;
        if (args.length > 1) {
            port = Integer.parseInt(args[1]);
        }
        
        // Target host
        HttpHost targetHost = new HttpHost(hostname, port); 
        
        HttpParams params = new SyncBasicHttpParams();
        params
            .setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 30000)
            .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
            .setParameter(CoreProtocolPNames.ORIGIN_SERVER, "HttpComponents/1.1")
            .setParameter(CoreProtocolPNames.USER_AGENT, "HttpComponents/1.1");

        final ConnectingIOReactor connectingIOReactor = new DefaultConnectingIOReactor(
                1, params);

        final ListeningIOReactor listeningIOReactor = new DefaultListeningIOReactor(
                1, params);
        
        // Set up HTTP protocol processor for incoming connections
        HttpProcessor inhttpproc = new ImmutableHttpProcessor(
                new HttpRequestInterceptor[] {
                        new RequestContent(),
                        new RequestTargetHost(),
                        new RequestConnControl(),
                        new RequestUserAgent(),
                        new RequestExpectContinue()
         });
        
        // Set up HTTP protocol processor for outgoing connections
        HttpProcessor outhttpproc = new ImmutableHttpProcessor(
                new HttpResponseInterceptor[] {
                        new ResponseDate(),
                        new ResponseServer(),
                        new ResponseContent(),
                        new ResponseConnControl()
        });
        
        NHttpClientHandler connectingHandler = new ConnectingHandler(
                inhttpproc,
                new DefaultConnectionReuseStrategy(),
                params);

        NHttpServiceHandler listeningHandler = new ListeningHandler(
                targetHost,
                connectingIOReactor,
                outhttpproc, 
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                params);
        
        final IOEventDispatch connectingEventDispatch = new DefaultClientIOEventDispatch(
                connectingHandler, params);

        final IOEventDispatch listeningEventDispatch = new DefaultServerIOEventDispatch(
                listeningHandler, params);
        
        Thread t = new Thread(new Runnable() {
            
            public void run() {
                try {
                    connectingIOReactor.execute(connectingEventDispatch);
                } catch (InterruptedIOException ex) {
                    System.err.println("Interrupted");
                } catch (IOException e) {
                    System.err.println("I/O error: " + e.getMessage());
                }
            }
            
        });
        t.start();
        
        try {
            listeningIOReactor.listen(new InetSocketAddress(8888));
            listeningIOReactor.execute(listeningEventDispatch);
        } catch (InterruptedIOException ex) {
            System.err.println("Interrupted");
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
        }
    }

    static class ListeningHandler implements NHttpServiceHandler {

        private final HttpHost targetHost;
        private final ConnectingIOReactor connectingIOReactor;    
        private final HttpProcessor httpProcessor;
        private final HttpResponseFactory responseFactory;
        private final ConnectionReuseStrategy connStrategy;
        private final HttpParams params;
        
        public ListeningHandler(
                final HttpHost targetHost,
                final ConnectingIOReactor connectingIOReactor,
                final HttpProcessor httpProcessor, 
                final HttpResponseFactory responseFactory,
                final ConnectionReuseStrategy connStrategy,
                final HttpParams params) {
            super();
            this.targetHost = targetHost;
            this.connectingIOReactor = connectingIOReactor;
            this.httpProcessor = httpProcessor;
            this.connStrategy = connStrategy;
            this.responseFactory = responseFactory;
            this.params = params;
        }

        public void connected(final NHttpServerConnection conn) {
            System.out.println(conn + " [client->proxy] conn open");

            ProxyTask proxyTask = new ProxyTask();
            
            synchronized (proxyTask) {

                // Initialize connection state
                proxyTask.setTarget(this.targetHost);
                proxyTask.setClientIOControl(conn);
                proxyTask.setClientState(ConnState.CONNECTED);
                
                HttpContext context = conn.getContext();
                context.setAttribute(ProxyTask.ATTRIB, proxyTask);
                
                InetSocketAddress address = new InetSocketAddress(
                        this.targetHost.getHostName(), 
                        this.targetHost.getPort()); 
                
                this.connectingIOReactor.connect(
                        address, 
                        null, 
                        proxyTask, 
                        null);            
            }
        }

        public void requestReceived(final NHttpServerConnection conn) {
            System.out.println(conn + " [client->proxy] request received");

            HttpContext context = conn.getContext();
            ProxyTask proxyTask = (ProxyTask) context.getAttribute(ProxyTask.ATTRIB);

            synchronized (proxyTask) {
                ConnState connState = proxyTask.getClientState();
                if (connState != ConnState.IDLE
                        && connState != ConnState.CONNECTED) {
                    throw new IllegalStateException("Illegal client connection state: " + connState);
                }

                try {

                    HttpRequest request = conn.getHttpRequest();
                    
                    System.out.println(conn + " [client->proxy] >> " + request.getRequestLine());
                    
                    ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
                    if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
                        // Downgrade protocol version if greater than HTTP/1.1 
                        ver = HttpVersion.HTTP_1_1;
                    }
                    
                    // Update connection state
                    proxyTask.setRequest(request);
                    proxyTask.setClientState(ConnState.REQUEST_RECEIVED);
                    
                    // See if the client expects a 100-Continue
                    if (request instanceof HttpEntityEnclosingRequest) {
                        if (((HttpEntityEnclosingRequest) request).expectContinue()) {
                            HttpResponse ack = this.responseFactory.newHttpResponse(
                                    ver, 
                                    HttpStatus.SC_CONTINUE, 
                                    context);
                            conn.submitResponse(ack);
                        }
                    } else {
                        // No request content expected. Suspend client input
                        conn.suspendInput();
                    }
                    
                    // If there is already a connection to the origin server
                    // make sure origin output is active
                    if (proxyTask.getOriginIOControl() != null) {
                        proxyTask.getOriginIOControl().requestOutput();
                    }
                    
                } catch (IOException ex) {
                    shutdownConnection(conn);
                } catch (HttpException ex) {
                    shutdownConnection(conn);
                }
            }
        }

        public void inputReady(final NHttpServerConnection conn, final ContentDecoder decoder) {
            System.out.println(conn + " [client->proxy] input ready");

            HttpContext context = conn.getContext();
            ProxyTask proxyTask = (ProxyTask) context.getAttribute(ProxyTask.ATTRIB);

            synchronized (proxyTask) {
                ConnState connState = proxyTask.getClientState();
                if (connState != ConnState.REQUEST_RECEIVED
                        && connState != ConnState.REQUEST_BODY_STREAM) {
                    throw new IllegalStateException("Illegal client connection state: " + connState);
                }
                
                try {

                    ByteBuffer dst = proxyTask.getInBuffer();
                    int bytesRead = decoder.read(dst);
                    System.out.println(conn + " [client->proxy] " + bytesRead + " bytes read");
                    System.out.println(conn + " [client->proxy] " + decoder);
                    if (!dst.hasRemaining()) {
                        // Input buffer is full. Suspend client input
                        // until the origin handler frees up some space in the buffer
                        conn.suspendInput();
                    }
                    // If there is some content in the input buffer make sure origin 
                    // output is active
                    if (dst.position() > 0) {
                        if (proxyTask.getOriginIOControl() != null) {
                            proxyTask.getOriginIOControl().requestOutput();
                        }
                    }

                    if (decoder.isCompleted()) {
                        System.out.println(conn + " [client->proxy] request body received");
                        // Update connection state
                        proxyTask.setClientState(ConnState.REQUEST_BODY_DONE);
                        // Suspend client input
                        conn.suspendInput();
                    } else {
                        proxyTask.setClientState(ConnState.REQUEST_BODY_STREAM);
                    }
                    
                } catch (IOException ex) {
                    shutdownConnection(conn);
                }
            }
        }

        public void responseReady(final NHttpServerConnection conn) {
            System.out.println(conn + " [client<-proxy] response ready");

            HttpContext context = conn.getContext();
            ProxyTask proxyTask = (ProxyTask) context.getAttribute(ProxyTask.ATTRIB);

            synchronized (proxyTask) {
                ConnState connState = proxyTask.getClientState();
                if (connState == ConnState.IDLE) {
                    // Response not available 
                    return;
                }
                if (connState != ConnState.REQUEST_RECEIVED
                        && connState != ConnState.REQUEST_BODY_DONE) {
                    throw new IllegalStateException("Illegal client connection state: " + connState);
                }

                try {

                    HttpRequest request = proxyTask.getRequest();
                    HttpResponse response = proxyTask.getResponse();
                    if (response == null) {
                        throw new IllegalStateException("HTTP request is null");
                    }
                    // Remove hop-by-hop headers
                    response.removeHeaders(HTTP.CONTENT_LEN);
                    response.removeHeaders(HTTP.TRANSFER_ENCODING);
                    response.removeHeaders(HTTP.CONN_DIRECTIVE);
                    response.removeHeaders("Keep-Alive");
                    response.removeHeaders("Proxy-Authenticate");
                    response.removeHeaders("Proxy-Authorization");
                    response.removeHeaders("TE");
                    response.removeHeaders("Trailers");
                    response.removeHeaders("Upgrade");
                    
                    response.setParams(
                            new DefaultedHttpParams(response.getParams(), this.params));

                    // Close client connection if the connection to the target 
                    // is no longer active / open
                    if (proxyTask.getOriginState().compareTo(ConnState.CLOSING) >= 0) {
                        response.addHeader(HTTP.CONN_DIRECTIVE, "Close");    
                    }
                    
                    // Pre-process HTTP request
                    context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
                    context.setAttribute(ExecutionContext.HTTP_REQUEST, request);
                    this.httpProcessor.process(response, context);
                    
                    conn.submitResponse(response);

                    proxyTask.setClientState(ConnState.RESPONSE_SENT);

                    System.out.println(conn + " [client<-proxy] << " + response.getStatusLine());
                    
                    if (!canResponseHaveBody(request, response)) {
                        conn.resetInput();
                        if (!this.connStrategy.keepAlive(response, context)) {
                            System.out.println(conn + " [client<-proxy] close connection");
                            proxyTask.setClientState(ConnState.CLOSING);
                            conn.close();
                        } else {
                            // Reset connection state
                            proxyTask.reset();
                            conn.requestInput();
                            // Ready to deal with a new request
                        }
                    }
                    
                } catch (IOException ex) {
                    shutdownConnection(conn);
                } catch (HttpException ex) {
                    shutdownConnection(conn);
                }
            }
        }
        
        private boolean canResponseHaveBody(
                final HttpRequest request, final HttpResponse response) {

            if (request != null && "HEAD".equalsIgnoreCase(request.getRequestLine().getMethod())) {
                return false;
            }
            
            int status = response.getStatusLine().getStatusCode(); 
            return status >= HttpStatus.SC_OK 
                && status != HttpStatus.SC_NO_CONTENT 
                && status != HttpStatus.SC_NOT_MODIFIED
                && status != HttpStatus.SC_RESET_CONTENT; 
        }
        
        public void outputReady(final NHttpServerConnection conn, final ContentEncoder encoder) {
            System.out.println(conn + " [client<-proxy] output ready");

            HttpContext context = conn.getContext();
            ProxyTask proxyTask = (ProxyTask) context.getAttribute(ProxyTask.ATTRIB);

            synchronized (proxyTask) {
                ConnState connState = proxyTask.getClientState();
                if (connState != ConnState.RESPONSE_SENT
                        && connState != ConnState.RESPONSE_BODY_STREAM) {
                    throw new IllegalStateException("Illegal client connection state: " + connState);
                }

                HttpResponse response = proxyTask.getResponse();
                if (response == null) {
                    throw new IllegalStateException("HTTP request is null");
                }
                
                try {

                    ByteBuffer src = proxyTask.getOutBuffer();
                    src.flip();
                    int bytesWritten = encoder.write(src);
                    System.out.println(conn + " [client<-proxy] " + bytesWritten + " bytes written");
                    System.out.println(conn + " [client<-proxy] " + encoder);
                    src.compact();

                    if (src.position() == 0) {

                        if (proxyTask.getOriginState() == ConnState.RESPONSE_BODY_DONE) {
                            encoder.complete();
                        } else {
                            // Input output is empty. Wait until the origin handler 
                            // fills up the buffer
                            conn.suspendOutput();
                        }
                    }

                    // Update connection state
                    if (encoder.isCompleted()) {
                        System.out.println(conn + " [proxy] response body sent");
                        proxyTask.setClientState(ConnState.RESPONSE_BODY_DONE);
                        if (!this.connStrategy.keepAlive(response, context)) {
                            System.out.println(conn + " [client<-proxy] close connection");
                            proxyTask.setClientState(ConnState.CLOSING);
                            conn.close();
                        } else {
                            // Reset connection state
                            proxyTask.reset();
                            conn.requestInput();
                            // Ready to deal with a new request
                        }
                    } else {
                        proxyTask.setClientState(ConnState.RESPONSE_BODY_STREAM);
                        // Make sure origin input is active
                        proxyTask.getOriginIOControl().requestInput();
                    }
                    
                } catch (IOException ex) {
                    shutdownConnection(conn);
                } 
            }
        }

        public void closed(final NHttpServerConnection conn) {
            System.out.println(conn + " [client->proxy] conn closed");
            HttpContext context = conn.getContext();
            ProxyTask proxyTask = (ProxyTask) context.getAttribute(ProxyTask.ATTRIB);

            if (proxyTask != null) {
                synchronized (proxyTask) {
                    proxyTask.setClientState(ConnState.CLOSED);
                }
            }
        }

        public void exception(final NHttpServerConnection conn, final HttpException httpex) {
            System.out.println(conn + " [client->proxy] HTTP error: " + httpex.getMessage());

            if (conn.isResponseSubmitted()) {
                shutdownConnection(conn);
                return;
            }
            
            HttpContext context = conn.getContext();

            try {
                HttpResponse response = this.responseFactory.newHttpResponse(
                        HttpVersion.HTTP_1_0, HttpStatus.SC_BAD_REQUEST, context);
                response.setParams(
                        new DefaultedHttpParams(this.params, response.getParams()));
                response.addHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_CLOSE);
                // Pre-process HTTP request
                context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
                context.setAttribute(ExecutionContext.HTTP_REQUEST, null);
                this.httpProcessor.process(response, context);
                
                conn.submitResponse(response);

                conn.close();
                
            } catch (IOException ex) {
                shutdownConnection(conn);
            } catch (HttpException ex) {
                shutdownConnection(conn);
            }
        }

        public void exception(final NHttpServerConnection conn, final IOException ex) {
            shutdownConnection(conn);
            System.out.println(conn + " [client->proxy] I/O error: " + ex.getMessage());
        }
        
        public void timeout(final NHttpServerConnection conn) {
            System.out.println(conn + " [client->proxy] timeout");
            closeConnection(conn);
        }
        
        private void shutdownConnection(final NHttpConnection conn) {
            try {
                conn.shutdown();
            } catch (IOException ignore) {
            }
        }

        private void closeConnection(final NHttpConnection conn) {
            try {
                conn.close();
            } catch (IOException ignore) {
            }
        }

    }
    
    static class ConnectingHandler implements NHttpClientHandler {

        private final HttpProcessor httpProcessor;
        private final ConnectionReuseStrategy connStrategy;
        private final HttpParams params;
        
        public ConnectingHandler(
                final HttpProcessor httpProcessor, 
                final ConnectionReuseStrategy connStrategy,
                final HttpParams params) {
            super();
            this.httpProcessor = httpProcessor;
            this.connStrategy = connStrategy;
            this.params = params;
        }
        
        public void connected(final NHttpClientConnection conn, final Object attachment) {
            System.out.println(conn + " [proxy->origin] conn open");
            
            // The shared state object is expected to be passed as an attachment
            ProxyTask proxyTask = (ProxyTask) attachment;

            synchronized (proxyTask) {
                ConnState connState = proxyTask.getOriginState();
                if (connState != ConnState.IDLE) {
                    throw new IllegalStateException("Illegal target connection state: " + connState);
                }

                // Set origin IO control handle
                proxyTask.setOriginIOControl(conn);
                // Store the state object in the context
                HttpContext context = conn.getContext();
                context.setAttribute(ProxyTask.ATTRIB, proxyTask);
                // Update connection state
                proxyTask.setOriginState(ConnState.CONNECTED);
                
                if (proxyTask.getRequest() != null) {
                    conn.requestOutput();
                }
            }
        }

        public void requestReady(final NHttpClientConnection conn) {
            System.out.println(conn + " [proxy->origin] request ready");

            HttpContext context = conn.getContext();
            ProxyTask proxyTask = (ProxyTask) context.getAttribute(ProxyTask.ATTRIB);

            synchronized (proxyTask) {
                ConnState connState = proxyTask.getOriginState();
                if (connState == ConnState.REQUEST_SENT 
                        || connState == ConnState.REQUEST_BODY_DONE) {
                    // Request sent but no response available yet
                    return;
                }

                if (connState != ConnState.IDLE
                        && connState != ConnState.CONNECTED) {
                    throw new IllegalStateException("Illegal target connection state: " + connState);
                }

                HttpRequest request = proxyTask.getRequest();
                if (request == null) {
                    throw new IllegalStateException("HTTP request is null");
                }
                
                // Remove hop-by-hop headers
                request.removeHeaders(HTTP.CONTENT_LEN);
                request.removeHeaders(HTTP.TRANSFER_ENCODING);
                request.removeHeaders(HTTP.CONN_DIRECTIVE);
                request.removeHeaders("Keep-Alive");
                request.removeHeaders("Proxy-Authenticate");
                request.removeHeaders("Proxy-Authorization");
                request.removeHeaders("TE");
                request.removeHeaders("Trailers");
                request.removeHeaders("Upgrade");
                // Remove host header
                request.removeHeaders(HTTP.TARGET_HOST);
                
                HttpHost targetHost = proxyTask.getTarget();
                
                try {
                    
                    request.setParams(
                            new DefaultedHttpParams(request.getParams(), this.params));
                    
                    // Pre-process HTTP request
                    context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
                    context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, targetHost);

                    this.httpProcessor.process(request, context);
                    // and send it to the origin server
                    conn.submitRequest(request);
                    // Update connection state
                    proxyTask.setOriginState(ConnState.REQUEST_SENT);
                    
                    System.out.println(conn + " [proxy->origin] >> " + request.getRequestLine().toString());
                    
                } catch (IOException ex) {
                    shutdownConnection(conn);
                } catch (HttpException ex) {
                    shutdownConnection(conn);
                }
                
            }
        }

        public void outputReady(final NHttpClientConnection conn, final ContentEncoder encoder) {
            System.out.println(conn + " [proxy->origin] output ready");
            
            HttpContext context = conn.getContext();
            ProxyTask proxyTask = (ProxyTask) context.getAttribute(ProxyTask.ATTRIB);

            synchronized (proxyTask) {
                ConnState connState = proxyTask.getOriginState();
                if (connState != ConnState.REQUEST_SENT
                        && connState != ConnState.REQUEST_BODY_STREAM) {
                    throw new IllegalStateException("Illegal target connection state: " + connState);
                }
                
                try {
                    
                    ByteBuffer src = proxyTask.getInBuffer();
                    src.flip();
                    int bytesWritten = encoder.write(src);
                    System.out.println(conn + " [proxy->origin] " + bytesWritten + " bytes written");
                    System.out.println(conn + " [proxy->origin] " + encoder);
                    src.compact();
                    
                    if (src.position() == 0) {
                        if (proxyTask.getClientState() == ConnState.REQUEST_BODY_DONE) {
                            encoder.complete();
                        } else {
                            // Input buffer is empty. Wait until the client fills up 
                            // the buffer
                            conn.suspendOutput();
                        }
                    }
                    // Update connection state
                    if (encoder.isCompleted()) {
                        System.out.println(conn + " [proxy->origin] request body sent");
                        proxyTask.setOriginState(ConnState.REQUEST_BODY_DONE);
                    } else {
                        proxyTask.setOriginState(ConnState.REQUEST_BODY_STREAM);
                        // Make sure client input is active
                        proxyTask.getClientIOControl().requestInput();
                    }
                    
                } catch (IOException ex) {
                    shutdownConnection(conn);
                }
            }
        }

        public void responseReceived(final NHttpClientConnection conn) {
            System.out.println(conn + " [proxy<-origin] response received");
            
            HttpContext context = conn.getContext();
            ProxyTask proxyTask = (ProxyTask) context.getAttribute(ProxyTask.ATTRIB);

            synchronized (proxyTask) {
                ConnState connState = proxyTask.getOriginState();
                if (connState != ConnState.REQUEST_SENT
                        && connState != ConnState.REQUEST_BODY_DONE) {
                    throw new IllegalStateException("Illegal target connection state: " + connState);
                }

                HttpResponse response = conn.getHttpResponse();
                HttpRequest request = proxyTask.getRequest();

                System.out.println(conn + " [proxy<-origin] << " + response.getStatusLine());
                
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode < HttpStatus.SC_OK) {
                    // Ignore 1xx response
                    return;
                }
                try {
                
                    // Update connection state
                    proxyTask.setResponse(response);
                    proxyTask.setOriginState(ConnState.RESPONSE_RECEIVED);
                    
                    if (!canResponseHaveBody(request, response)) {
                        conn.resetInput();
                        if (!this.connStrategy.keepAlive(response, context)) {
                            System.out.println(conn + " [proxy<-origin] close connection");
                            proxyTask.setOriginState(ConnState.CLOSING);
                            conn.close();
                        }
                    }
                    // Make sure client output is active
                    proxyTask.getClientIOControl().requestOutput();

                } catch (IOException ex) {
                    shutdownConnection(conn);
                }
            }

        }

        private boolean canResponseHaveBody(
                final HttpRequest request, final HttpResponse response) {

            if (request != null && "HEAD".equalsIgnoreCase(request.getRequestLine().getMethod())) {
                return false;
            }
            
            int status = response.getStatusLine().getStatusCode(); 
            return status >= HttpStatus.SC_OK 
                && status != HttpStatus.SC_NO_CONTENT 
                && status != HttpStatus.SC_NOT_MODIFIED
                && status != HttpStatus.SC_RESET_CONTENT; 
        }
        
        public void inputReady(final NHttpClientConnection conn, final ContentDecoder decoder) {
            System.out.println(conn + " [proxy<-origin] input ready");

            HttpContext context = conn.getContext();
            ProxyTask proxyTask = (ProxyTask) context.getAttribute(ProxyTask.ATTRIB);

            synchronized (proxyTask) {
                ConnState connState = proxyTask.getOriginState();
                if (connState != ConnState.RESPONSE_RECEIVED
                        && connState != ConnState.RESPONSE_BODY_STREAM) {
                    throw new IllegalStateException("Illegal target connection state: " + connState);
                }

                HttpResponse response = proxyTask.getResponse();
                try {
                    
                    ByteBuffer dst = proxyTask.getOutBuffer();
                    int bytesRead = decoder.read(dst);
                    System.out.println(conn + " [proxy<-origin] " + bytesRead + " bytes read");
                    System.out.println(conn + " [proxy<-origin] " + decoder);
                    if (!dst.hasRemaining()) {
                        // Output buffer is full. Suspend origin input until 
                        // the client handler frees up some space in the buffer
                        conn.suspendInput();
                    }
                    // If there is some content in the buffer make sure client output 
                    // is active
                    if (dst.position() > 0) {
                        proxyTask.getClientIOControl().requestOutput();
                    }
                    
                    if (decoder.isCompleted()) {
                        System.out.println(conn + " [proxy<-origin] response body received");
                        proxyTask.setOriginState(ConnState.RESPONSE_BODY_DONE);

                        if (!this.connStrategy.keepAlive(response, context)) {
                            System.out.println(conn + " [proxy<-origin] close connection");
                            proxyTask.setOriginState(ConnState.CLOSING);
                            conn.close();
                        }
                    } else {
                        proxyTask.setOriginState(ConnState.RESPONSE_BODY_STREAM);
                    }
                    
                } catch (IOException ex) {
                    shutdownConnection(conn);
                }
            }
        }

        public void closed(final NHttpClientConnection conn) {
            System.out.println(conn + " [proxy->origin] conn closed");
            HttpContext context = conn.getContext();
            ProxyTask proxyTask = (ProxyTask) context.getAttribute(ProxyTask.ATTRIB);

            if (proxyTask != null) {
                synchronized (proxyTask) {
                    proxyTask.setOriginState(ConnState.CLOSED);
                }
            }
        }

        public void exception(final NHttpClientConnection conn, final HttpException ex) {
            shutdownConnection(conn);
            System.out.println(conn + " [proxy->origin] HTTP error: " + ex.getMessage());
        }

        public void exception(final NHttpClientConnection conn, final IOException ex) {
            shutdownConnection(conn);
            System.out.println(conn + " [proxy->origin] I/O error: " + ex.getMessage());
        }
        
        public void timeout(final NHttpClientConnection conn) {
            System.out.println(conn + " [proxy->origin] timeout");
            closeConnection(conn);
        }
     
        private void shutdownConnection(final HttpConnection conn) {
            try {
                conn.shutdown();
            } catch (IOException ignore) {
            }
        }
        
        private void closeConnection(final HttpConnection conn) {
            try {
                conn.shutdown();
            } catch (IOException ignore) {
            }
        }

    }    
    
    enum ConnState {
        IDLE,
        CONNECTED,
        REQUEST_RECEIVED,
        REQUEST_SENT,
        REQUEST_BODY_STREAM,
        REQUEST_BODY_DONE,
        RESPONSE_RECEIVED,
        RESPONSE_SENT,
        RESPONSE_BODY_STREAM,
        RESPONSE_BODY_DONE,
        CLOSING,
        CLOSED
    }
    
    static class ProxyTask {
        
        public static final String ATTRIB = "nhttp.proxy-task";
        
        private final ByteBuffer inBuffer;
        private final ByteBuffer outBuffer;

        private HttpHost target;
        
        private IOControl originIOControl;
        private IOControl clientIOControl;
        
        private ConnState originState;
        private ConnState clientState;
        
        private HttpRequest request;
        private HttpResponse response;
        
        public ProxyTask() {
            super();
            this.originState = ConnState.IDLE;
            this.clientState = ConnState.IDLE;
            this.inBuffer = ByteBuffer.allocateDirect(10240);
            this.outBuffer = ByteBuffer.allocateDirect(10240);
        }

        public ByteBuffer getInBuffer() {
            return this.inBuffer;
        }

        public ByteBuffer getOutBuffer() {
            return this.outBuffer;
        }
        
        public HttpHost getTarget() {
            return this.target;
        }

        public void setTarget(final HttpHost target) {
            this.target = target;
        }

        public HttpRequest getRequest() {
            return this.request;
        }

        public void setRequest(final HttpRequest request) {
            this.request = request;
        }

        public HttpResponse getResponse() {
            return this.response;
        }

        public void setResponse(final HttpResponse response) {
            this.response = response;
        }

        public IOControl getClientIOControl() {
            return this.clientIOControl;
        }

        public void setClientIOControl(final IOControl clientIOControl) {
            this.clientIOControl = clientIOControl;
        }

        public IOControl getOriginIOControl() {
            return this.originIOControl;
        }

        public void setOriginIOControl(final IOControl originIOControl) {
            this.originIOControl = originIOControl;
        }
        
        public ConnState getOriginState() {
            return this.originState;
        }

        public void setOriginState(final ConnState state) {
            this.originState = state;
        }
        
        public ConnState getClientState() {
            return this.clientState;
        }

        public void setClientState(final ConnState state) {
            this.clientState = state;
        }

        public void reset() {
            this.inBuffer.clear();
            this.outBuffer.clear();
            this.originState = ConnState.IDLE;
            this.clientState = ConnState.IDLE;
            this.request = null;
            this.response = null;
        }
        
        public void shutdown() {
            if (this.clientIOControl != null) {
                try {
                    this.clientIOControl.shutdown();
                } catch (IOException ignore) {
                }
            }
            if (this.originIOControl != null) {
                try {
                    this.originIOControl.shutdown();
                } catch (IOException ignore) {
                }
            }
        }

    }
    
}
