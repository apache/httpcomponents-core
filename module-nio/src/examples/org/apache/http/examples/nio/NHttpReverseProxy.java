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
import java.nio.ByteBuffer;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.Header;
import org.apache.http.HttpConnection;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
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
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExecutionContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;

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
        
        HttpParams params = new BasicHttpParams();
        params
            .setIntParameter(HttpConnectionParams.SO_TIMEOUT, 30000)
            .setIntParameter(HttpConnectionParams.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(HttpConnectionParams.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(HttpConnectionParams.TCP_NODELAY, true)
            .setParameter(HttpProtocolParams.ORIGIN_SERVER, "Jakarta-HttpComponents-NIO/1.1")
            .setParameter(HttpProtocolParams.USER_AGENT, "Jakarta-HttpComponents-NIO/1.1");

        final ConnectingIOReactor connectingIOReactor = new DefaultConnectingIOReactor(
                1, params);

        final ListeningIOReactor listeningIOReactor = new DefaultListeningIOReactor(
                1, params);
        
        BasicHttpProcessor originServerProc = new BasicHttpProcessor();
        originServerProc.addInterceptor(new RequestContent());
        originServerProc.addInterceptor(new RequestTargetHost());
        originServerProc.addInterceptor(new RequestConnControl());
        originServerProc.addInterceptor(new RequestUserAgent());
        originServerProc.addInterceptor(new RequestExpectContinue());
        
        BasicHttpProcessor clientProxyProcessor = new BasicHttpProcessor();
        clientProxyProcessor.addInterceptor(new ResponseDate());
        clientProxyProcessor.addInterceptor(new ResponseServer());
        clientProxyProcessor.addInterceptor(new ResponseContent());
        clientProxyProcessor.addInterceptor(new ResponseConnControl());
        
        NHttpClientHandler connectingHandler = new ConnectingHandler(
                originServerProc,
                new DefaultConnectionReuseStrategy(),
                params);

        NHttpServiceHandler listeningHandler = new ListeningHandler(
                targetHost,
                connectingIOReactor,
                clientProxyProcessor, 
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
            System.out.println(conn + ": client conn open");

            ProxyTask proxyTask = new ProxyTask();
            
            synchronized (proxyTask) {

                // Initialize connection state
                proxyTask.setTarget(this.targetHost);
                proxyTask.setClientIOControl(conn);
                proxyTask.setClientState(ProxyTask.CONNECTED);
                
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
            System.out.println(conn + ": client conn request received");

            HttpContext context = conn.getContext();
            ProxyTask proxyTask = (ProxyTask) context.getAttribute(ProxyTask.ATTRIB);

            synchronized (proxyTask) {
                // Validate connection state
                if (proxyTask.getClientState() != ProxyTask.IDLE
                        && proxyTask.getClientState() != ProxyTask.CONNECTED) {
                    throw new IllegalStateException("Illegal connection state");
                }

                try {

                    HttpRequest request = conn.getHttpRequest();
                    
                    System.out.println(conn + ": [client] >> " + request.getRequestLine().toString());
                    Header[] headers = request.getAllHeaders();
                    for (int i = 0; i < headers.length; i++) {
                        System.out.println(conn +  ": [client] >> " + headers[i].toString());
                    }
                    
                    HttpVersion ver = request.getRequestLine().getHttpVersion();
                    if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
                        // Downgrade protocol version if greater than HTTP/1.1 
                        ver = HttpVersion.HTTP_1_1;
                    }
                    
                    // Update connection state
                    proxyTask.setRequest(request);
                    proxyTask.setClientState(ProxyTask.REQUEST_RECEIVED);
                    
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
            System.out.println(conn + ": client conn input ready " + decoder);

            HttpContext context = conn.getContext();
            ProxyTask proxyTask = (ProxyTask) context.getAttribute(ProxyTask.ATTRIB);

            synchronized (proxyTask) {
                // Validate connection state
                if (proxyTask.getClientState() != ProxyTask.REQUEST_RECEIVED
                        && proxyTask.getClientState() != ProxyTask.REQUEST_BODY_STREAM) {
                    throw new IllegalStateException("Illegal connection state");
                }
                try {

                    ByteBuffer dst = proxyTask.getInBuffer();
                    int bytesRead = decoder.read(dst);
                    System.out.println(conn + ": " + bytesRead + " bytes read");
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
                        System.out.println(conn + ": client conn request body received");
                        // Update connection state
                        proxyTask.setClientState(ProxyTask.REQUEST_BODY_DONE);
                        // Suspend client input
                        conn.suspendInput();
                    } else {
                        proxyTask.setClientState(ProxyTask.REQUEST_BODY_STREAM);
                    }
                    
                } catch (IOException ex) {
                    shutdownConnection(conn);
                }
            }
        }

        public void responseReady(final NHttpServerConnection conn) {
            System.out.println(conn + ": client conn response ready");

            HttpContext context = conn.getContext();
            ProxyTask proxyTask = (ProxyTask) context.getAttribute(ProxyTask.ATTRIB);

            synchronized (proxyTask) {
                if (proxyTask.getClientState() == ProxyTask.IDLE) {
                    // Response not available 
                    return;
                }
                // Validate connection state
                if (proxyTask.getClientState() != ProxyTask.REQUEST_RECEIVED
                        && proxyTask.getClientState() != ProxyTask.REQUEST_BODY_DONE) {
                    throw new IllegalStateException("Illegal connection state");
                }
                try {

                    HttpRequest request = proxyTask.getRequest();
                    HttpResponse response = proxyTask.getResponse();
                    if (response == null) {
                        throw new IllegalStateException("HTTP request is null");
                    }
                    // Remove connection specific headers
                    response.removeHeaders(HTTP.CONTENT_LEN);
                    response.removeHeaders(HTTP.TRANSFER_ENCODING);
                    response.removeHeaders(HTTP.SERVER_DIRECTIVE);
                    response.removeHeaders(HTTP.CONN_DIRECTIVE);
                    response.removeHeaders("Keep-Alive");
                    
                    response.getParams().setDefaults(this.params);
                    
                    // Pre-process HTTP request
                    context.setAttribute(HttpExecutionContext.HTTP_CONNECTION, conn);
                    context.setAttribute(HttpExecutionContext.HTTP_REQUEST, request);
                    this.httpProcessor.process(response, context);
                    
                    conn.submitResponse(response);

                    proxyTask.setClientState(ProxyTask.RESPONSE_SENT);

                    System.out.println(conn + ": [proxy] << " + response.getStatusLine().toString());
                    Header[] headers = response.getAllHeaders();
                    for (int i = 0; i < headers.length; i++) {
                        System.out.println(conn + ": [proxy] << " + headers[i].toString());
                    }
                    
                    if (!canResponseHaveBody(request, response)) {
                        conn.resetInput();
                        if (!this.connStrategy.keepAlive(response, context)) {
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
            System.out.println(conn + ": client conn output ready " + encoder);

            HttpContext context = conn.getContext();
            ProxyTask proxyTask = (ProxyTask) context.getAttribute(ProxyTask.ATTRIB);

            synchronized (proxyTask) {
                // Validate connection state
                if (proxyTask.getClientState() != ProxyTask.RESPONSE_SENT
                        && proxyTask.getClientState() != ProxyTask.RESPONSE_BODY_STREAM) {
                    throw new IllegalStateException("Illegal connection state");
                }

                HttpResponse response = proxyTask.getResponse();
                if (response == null) {
                    throw new IllegalStateException("HTTP request is null");
                }
                
                try {

                    ByteBuffer src = proxyTask.getOutBuffer();
                    src.flip();
                    if (src.hasRemaining()) {
                        int bytesWritten = encoder.write(src);
                        System.out.println(conn + ": " + bytesWritten + " bytes written");
                    }
                    src.compact();

                    if (src.position() == 0) {
                        if (proxyTask.getOriginState() == ProxyTask.RESPONSE_BODY_DONE) {
                            encoder.complete();
                        } else {
                            // Input output is empty. Wait until the origin handler 
                            // fills up the buffer
                            conn.suspendOutput();
                        }
                    }

                    // Update connection state
                    if (encoder.isCompleted()) {
                        System.out.println(conn + ": client conn response body sent");
                        proxyTask.setClientState(ProxyTask.RESPONSE_BODY_DONE);
                        if (!this.connStrategy.keepAlive(response, context)) {
                            conn.close();
                        } else {
                            // Reset connection state
                            proxyTask.reset();
                            conn.requestInput();
                            // Ready to deal with a new request
                        }
                    } else {
                        proxyTask.setOriginState(ProxyTask.RESPONSE_BODY_STREAM);
                        // Make sure origin input is active
                        proxyTask.getOriginIOControl().requestInput();
                    }
                    
                } catch (IOException ex) {
                    shutdownConnection(conn);
                } 
            }
        }

        public void closed(final NHttpServerConnection conn) {
            System.out.println(conn + ": client conn closed");
            HttpContext context = conn.getContext();
            ProxyTask proxyTask = (ProxyTask) context.getAttribute(ProxyTask.ATTRIB);

            if (proxyTask != null) {
                synchronized (proxyTask) {
                    IOControl ioControl = proxyTask.getOriginIOControl();
                    if (ioControl != null) {
                        try {
                            ioControl.shutdown();
                        } catch (IOException ex) {
                            // ignore
                        }
                    }
                }
            }
        }

        public void exception(final NHttpServerConnection conn, final HttpException httpex) {
            System.out.println(conn + ": " + httpex.getMessage());

            HttpContext context = conn.getContext();

            try {
                HttpResponse response = this.responseFactory.newHttpResponse(
                        HttpVersion.HTTP_1_0, HttpStatus.SC_BAD_REQUEST, context);
                response.getParams().setDefaults(this.params);
                response.addHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_CLOSE);
                // Pre-process HTTP request
                context.setAttribute(HttpExecutionContext.HTTP_CONNECTION, conn);
                context.setAttribute(HttpExecutionContext.HTTP_REQUEST, null);
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
            System.out.println(conn + ": " + ex.getMessage());
        }
        
        public void timeout(final NHttpServerConnection conn) {
            System.out.println(conn + ": timeout");
            shutdownConnection(conn);
        }
        
        private void shutdownConnection(final NHttpConnection conn) {
            try {
                conn.shutdown();
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
            System.out.println(conn + ": origin conn open");
            
            // The shared state object is expected to be passed as an attachment
            ProxyTask proxyTask = (ProxyTask) attachment;

            synchronized (proxyTask) {
                // Validate connection state
                if (proxyTask.getOriginState() != ProxyTask.IDLE) {
                    throw new IllegalStateException("Illegal connection state");
                }
                // Set origin IO control handle
                proxyTask.setOriginIOControl(conn);
                // Store the state object in the context
                HttpContext context = conn.getContext();
                context.setAttribute(ProxyTask.ATTRIB, proxyTask);
                // Update connection state
                proxyTask.setOriginState(ProxyTask.CONNECTED);
                
                if (proxyTask.getRequest() != null) {
                    conn.requestOutput();
                }
            }
        }

        public void requestReady(final NHttpClientConnection conn) {
            System.out.println(conn + ": origin conn request ready");

            HttpContext context = conn.getContext();
            ProxyTask proxyTask = (ProxyTask) context.getAttribute(ProxyTask.ATTRIB);

            synchronized (proxyTask) {
                // Validate connection state
                if (proxyTask.getOriginState() == ProxyTask.REQUEST_SENT) {
                    // Request sent but no response available yet
                    return;
                }
                if (proxyTask.getOriginState() != ProxyTask.IDLE
                        && proxyTask.getOriginState() != ProxyTask.CONNECTED) {
                    throw new IllegalStateException("Illegal connection state");
                }

                HttpRequest request = proxyTask.getRequest();
                if (request == null) {
                    throw new IllegalStateException("HTTP request is null");
                }
                
                // Remove connection specific headers
                request.removeHeaders(HTTP.CONTENT_LEN);
                request.removeHeaders(HTTP.TRANSFER_ENCODING);
                request.removeHeaders(HTTP.TARGET_HOST);
                request.removeHeaders(HTTP.CONN_DIRECTIVE);
                request.removeHeaders(HTTP.USER_AGENT);
                request.removeHeaders("Keep-Alive");
                
                HttpHost targetHost = proxyTask.getTarget();
                
                try {
                    
                    request.getParams().setDefaults(this.params);
                    
                    // Pre-process HTTP request
                    context.setAttribute(HttpExecutionContext.HTTP_CONNECTION, conn);
                    context.setAttribute(HttpExecutionContext.HTTP_TARGET_HOST, targetHost);

                    this.httpProcessor.process(request, context);
                    // and send it to the origin server
                    conn.submitRequest(request);
                    // Update connection state
                    proxyTask.setOriginState(ProxyTask.REQUEST_SENT);
                    
                    System.out.println(conn + ": [proxy] >> " + request.getRequestLine().toString());
                    Header[] headers = request.getAllHeaders();
                    for (int i = 0; i < headers.length; i++) {
                        System.out.println(conn +  ": [proxy] >> " + headers[i].toString());
                    }
                    
                } catch (IOException ex) {
                    shutdownConnection(conn);
                } catch (HttpException ex) {
                    shutdownConnection(conn);
                }
                
            }
        }

        public void outputReady(final NHttpClientConnection conn, final ContentEncoder encoder) {
            System.out.println(conn + ": origin conn output ready " + encoder);
            
            HttpContext context = conn.getContext();
            ProxyTask proxyTask = (ProxyTask) context.getAttribute(ProxyTask.ATTRIB);

            synchronized (proxyTask) {
                // Validate connection state
                if (proxyTask.getOriginState() != ProxyTask.REQUEST_SENT 
                        && proxyTask.getOriginState() != ProxyTask.REQUEST_BODY_STREAM) {
                    throw new IllegalStateException("Illegal connection state");
                }
                try {
                    
                    ByteBuffer src = proxyTask.getInBuffer();
                    src.flip();
                    if (src.hasRemaining()) {
                        int bytesWritten = encoder.write(src);
                        System.out.println(conn + ": " + bytesWritten + " bytes written");
                    }
                    src.compact();
                    
                    if (src.position() == 0) {
                        if (proxyTask.getClientState() == ProxyTask.REQUEST_BODY_DONE) {
                            encoder.complete();
                        } else {
                            // Input buffer is empty. Wait until the client fills up 
                            // the buffer
                            conn.suspendOutput();
                        }
                    }
                    // Update connection state
                    if (encoder.isCompleted()) {
                        System.out.println(conn + ": origin conn request body sent");
                        proxyTask.setOriginState(ProxyTask.REQUEST_BODY_DONE);
                    } else {
                        proxyTask.setOriginState(ProxyTask.REQUEST_BODY_STREAM);
                        // Make sure client input is active
                        proxyTask.getClientIOControl().requestInput();
                    }
                    
                } catch (IOException ex) {
                    shutdownConnection(conn);
                }
            }
        }

        public void responseReceived(final NHttpClientConnection conn) {
            System.out.println(conn + ": origin conn response received");
            
            HttpContext context = conn.getContext();
            ProxyTask proxyTask = (ProxyTask) context.getAttribute(ProxyTask.ATTRIB);

            synchronized (proxyTask) {
                // Validate connection state
                if (proxyTask.getOriginState() != ProxyTask.REQUEST_SENT 
                        && proxyTask.getOriginState() != ProxyTask.REQUEST_BODY_DONE) {
                    throw new IllegalStateException("Illegal connection state");
                }

                HttpResponse response = conn.getHttpResponse();
                HttpRequest request = proxyTask.getRequest();

                System.out.println(conn + ": [origin] << " + response.getStatusLine().toString());
                Header[] headers = response.getAllHeaders();
                for (int i = 0; i < headers.length; i++) {
                    System.out.println(conn + ": [origin] << " + headers[i].toString());
                }
                
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode < HttpStatus.SC_OK) {
                    // Ignore 1xx response
                    return;
                }
                try {
                
                    // Update connection state
                    proxyTask.setResponse(response);
                    proxyTask.setOriginState(ProxyTask.RESPONSE_RECEIVED);
                    
                    if (!canResponseHaveBody(request, response)) {
                        conn.resetInput();
                        if (!this.connStrategy.keepAlive(response, context)) {
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
            System.out.println(conn + ": origin conn input ready " + decoder);

            HttpContext context = conn.getContext();
            ProxyTask proxyTask = (ProxyTask) context.getAttribute(ProxyTask.ATTRIB);

            synchronized (proxyTask) {
                // Validate connection state
                if (proxyTask.getOriginState() != ProxyTask.RESPONSE_RECEIVED
                        && proxyTask.getOriginState() != ProxyTask.RESPONSE_BODY_STREAM) {
                    throw new IllegalStateException("Illegal connection state");
                }
                HttpResponse response = proxyTask.getResponse();
                try {
                    
                    ByteBuffer dst = proxyTask.getOutBuffer();
                    int bytesRead = decoder.read(dst);
                    System.out.println(conn + ": " + bytesRead + " bytes read");
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
                        System.out.println(conn + ": origin conn response body received");
                        proxyTask.setOriginState(ProxyTask.RESPONSE_BODY_DONE);
                        if (!this.connStrategy.keepAlive(response, context)) {
                            conn.close();
                        }
                    } else {
                        proxyTask.setOriginState(ProxyTask.RESPONSE_BODY_STREAM);
                    }
                    
                } catch (IOException ex) {
                    shutdownConnection(conn);
                }
            }
        }

        public void closed(final NHttpClientConnection conn) {
            System.out.println(conn + ": origin conn closed");
            HttpContext context = conn.getContext();
            ProxyTask proxyTask = (ProxyTask) context.getAttribute(ProxyTask.ATTRIB);

            if (proxyTask != null) {
                synchronized (proxyTask) {
                    IOControl ioControl = proxyTask.getClientIOControl();
                    if (ioControl != null) {
                        try {
                            ioControl.shutdown();
                        } catch (IOException ignore) {
                        }
                    }
                }
            }
        }

        public void exception(final NHttpClientConnection conn, final HttpException ex) {
            shutdownConnection(conn);
            System.out.println(conn + ": " + ex.getMessage());
        }

        public void exception(final NHttpClientConnection conn, final IOException ex) {
            shutdownConnection(conn);
            System.out.println(conn + ": " + ex.getMessage());
        }
        
        public void timeout(final NHttpClientConnection conn) {
            System.out.println(conn + ": timeout");
            shutdownConnection(conn);
        }
     
        private void shutdownConnection(final HttpConnection conn) {
            try {
                conn.shutdown();
            } catch (IOException ignore) {
            }
        }
        
    }    
    
    static class ProxyTask {
        
        public static final String ATTRIB = "nhttp.proxy-task";
        
        public static final int IDLE                       = 0;
        public static final int CONNECTED                  = 1;
        public static final int REQUEST_RECEIVED           = 2;
        public static final int REQUEST_SENT               = 3;
        public static final int REQUEST_BODY_STREAM        = 4;
        public static final int REQUEST_BODY_DONE          = 5;
        public static final int RESPONSE_RECEIVED          = 6;
        public static final int RESPONSE_SENT              = 7;
        public static final int RESPONSE_BODY_STREAM       = 8;
        public static final int RESPONSE_BODY_DONE         = 9;
        
        private final ByteBuffer inBuffer;
        private final ByteBuffer outBuffer;

        private HttpHost target;
        
        private IOControl originIOControl;
        private IOControl clientIOControl;
        
        private int originState;
        private int clientState;
        
        private HttpRequest request;
        private HttpResponse response;
        
        public ProxyTask() {
            super();
            this.originState = IDLE;
            this.clientState = IDLE;
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
        
        public int getOriginState() {
            return this.originState;
        }

        public void setOriginState(int state) {
            this.originState = state;
        }
        
        public int getClientState() {
            return this.clientState;
        }

        public void setClientState(int state) {
            this.clientState = state;
        }

        public void reset() {
            this.inBuffer.clear();
            this.outBuffer.clear();
            this.originState = IDLE;
            this.clientState = IDLE;
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
