/*
 * $HeadURL: http://svn.apache.org/repos/asf/httpcomponents/httpcore/trunk/module-main/src/examples/org/apache/http/examples/ElementalHttpServer.java $
 * $Revision: 702589 $
 * $Date: 2008-10-07 21:13:28 +0200 (Tue, 07 Oct 2008) $
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

package org.apache.http.examples;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.http.ConnectionClosedException;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpServerConnection;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpClientConnection;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.apache.http.protocol.HttpService;
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
 * Rudimentary HTTP/1.1 reverse proxy.
 * <p>
 * Please note the purpose of this application is demonstrate the usage of HttpCore APIs.
 * It is NOT intended to demonstrate the most efficient way of building an HTTP reverse proxy. 
 * 
 *
 * @version $Revision: $
 */
public class ElementalReverseProxy {

    private static final String HTTP_IN_CONN = "http.proxy.in-conn";
    private static final String HTTP_OUT_CONN = "http.proxy.out-conn";
    private static final String HTTP_CONN_KEEPALIVE = "http.proxy.conn-keepalive";
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Please specified target hostname and port");
            System.exit(1);
        }
        String hostname = args[0];
        int port = 80;
        if (args.length > 1) {
            port = Integer.parseInt(args[1]);
        }
        HttpHost target = new HttpHost(hostname, port);
        
        Thread t = new RequestListenerThread(8888, target);
        t.setDaemon(false);
        t.start();
    }
    
    static class ProxyHandler implements HttpRequestHandler  {
        
        private final HttpHost target;
        private final HttpProcessor httpproc;
        private final HttpRequestExecutor httpexecutor;
        private final ConnectionReuseStrategy connStrategy;
        
        public ProxyHandler(
                final HttpHost target,
                final HttpProcessor httpproc,
                final HttpRequestExecutor httpexecutor) {
            super();
            this.target = target;
            this.httpproc = httpproc;
            this.httpexecutor = httpexecutor;
            this.connStrategy = new DefaultConnectionReuseStrategy();
        }
        
        public void handle(
                final HttpRequest request, 
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            
            HttpClientConnection conn = (HttpClientConnection) context.getAttribute(
                    HTTP_OUT_CONN);

            context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
            context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, this.target);

            System.out.println(">> Request URI: " + request.getRequestLine().getUri());

            // Remove hop-by-hop headers
            request.removeHeaders(HTTP.CONTENT_LEN);
            request.removeHeaders(HTTP.TRANSFER_ENCODING);
            request.removeHeaders(HTTP.CONN_DIRECTIVE);
            request.removeHeaders("Keep-Alive");
            request.removeHeaders("Proxy-Authenticate");
            request.removeHeaders("TE");
            request.removeHeaders("Trailers");
            request.removeHeaders("Upgrade");
            
            this.httpexecutor.preProcess(request, this.httpproc, context);
            HttpResponse targetResponse = this.httpexecutor.execute(request, conn, context);
            this.httpexecutor.postProcess(response, this.httpproc, context);
            
            // Remove hop-by-hop headers
            targetResponse.removeHeaders(HTTP.CONTENT_LEN);
            targetResponse.removeHeaders(HTTP.TRANSFER_ENCODING);
            targetResponse.removeHeaders(HTTP.CONN_DIRECTIVE);
            targetResponse.removeHeaders("Keep-Alive");
            targetResponse.removeHeaders("TE");
            targetResponse.removeHeaders("Trailers");
            targetResponse.removeHeaders("Upgrade");
            
            response.setStatusLine(targetResponse.getStatusLine());
            response.setHeaders(targetResponse.getAllHeaders());
            response.setEntity(targetResponse.getEntity());
            
            System.out.println("<< Response: " + response.getStatusLine());

            boolean keepalive = this.connStrategy.keepAlive(response, context);
            context.setAttribute(HTTP_CONN_KEEPALIVE, new Boolean(keepalive));
        }
        
    }
    
    static class RequestListenerThread extends Thread {

        private final HttpHost target;
        private final ServerSocket serversocket;
        private final HttpParams params; 
        private final HttpService httpService;
        
        public RequestListenerThread(int port, final HttpHost target) throws IOException {
            this.target = target;
            this.serversocket = new ServerSocket(port);
            this.params = new BasicHttpParams();
            this.params
                .setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 5000)
                .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
                .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
                .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
                .setParameter(CoreProtocolPNames.ORIGIN_SERVER, "HttpComponents/1.1");

            // Set up HTTP protocol processor for incoming connections
            BasicHttpProcessor inhttpproc = new BasicHttpProcessor();
            inhttpproc.addInterceptor(new ResponseDate());
            inhttpproc.addInterceptor(new ResponseServer());
            inhttpproc.addInterceptor(new ResponseContent());
            inhttpproc.addInterceptor(new ResponseConnControl());

            // Set up HTTP protocol processor for outgoing connections
            BasicHttpProcessor outhttpproc = new BasicHttpProcessor();
            outhttpproc.addInterceptor(new RequestContent());
            outhttpproc.addInterceptor(new RequestTargetHost());
            outhttpproc.addInterceptor(new RequestConnControl());
            outhttpproc.addInterceptor(new RequestUserAgent());
            outhttpproc.addInterceptor(new RequestExpectContinue());

            // Set up outgoing request executor 
            HttpRequestExecutor httpexecutor = new HttpRequestExecutor();
            
            // Set up incoming request handler
            HttpRequestHandlerRegistry reqistry = new HttpRequestHandlerRegistry();
            reqistry.register("*", new ProxyHandler(
                    this.target, 
                    outhttpproc, 
                    httpexecutor));
            
            // Set up the HTTP service
            this.httpService = new HttpService(
                    inhttpproc, 
                    new DefaultConnectionReuseStrategy(), 
                    new DefaultHttpResponseFactory());
            this.httpService.setParams(this.params);
            this.httpService.setHandlerResolver(reqistry);
        }
        
        public void run() {
            System.out.println("Listening on port " + this.serversocket.getLocalPort());
            while (!Thread.interrupted()) {
                try {
                    // Set up incoming HTTP connection
                    Socket insocket = this.serversocket.accept();
                    DefaultHttpServerConnection inconn = new DefaultHttpServerConnection();
                    System.out.println("Incoming connection from " + insocket.getInetAddress());
                    inconn.bind(insocket, this.params);

                    // Set up outgoing HTTP connection
                    Socket outsocket = new Socket(this.target.getHostName(), this.target.getPort());
                    DefaultHttpClientConnection outconn = new DefaultHttpClientConnection();
                    outconn.bind(outsocket, this.params);
                    System.out.println("Outgoing connection to " + outsocket.getInetAddress());
                    
                    // Start worker thread
                    Thread t = new ProxyThread(this.httpService, inconn, outconn);
                    t.setDaemon(true);
                    t.start();
                } catch (InterruptedIOException ex) {
                    break;
                } catch (IOException e) {
                    System.err.println("I/O error initialising connection thread: " 
                            + e.getMessage());
                    break;
                }
            }
        }
    }
    
    static class ProxyThread extends Thread {

        private final HttpService httpservice;
        private final HttpServerConnection inconn;
        private final HttpClientConnection outconn;
        
        public ProxyThread(
                final HttpService httpservice, 
                final HttpServerConnection inconn,
                final HttpClientConnection outconn) {
            super();
            this.httpservice = httpservice;
            this.inconn = inconn;
            this.outconn = outconn;
        }
        
        public void run() {
            System.out.println("New connection thread");
            HttpContext context = new BasicHttpContext(null);
            
            // Bind connection objects to the execution context
            context.setAttribute(HTTP_IN_CONN, this.inconn);
            context.setAttribute(HTTP_OUT_CONN, this.outconn);
            
            try {
                while (!Thread.interrupted()) {
                    if (!this.inconn.isOpen()) {
                        this.outconn.close();
                        break;
                    }
                    
                    this.httpservice.handleRequest(this.inconn, context);
                    
                    Boolean keepalive = (Boolean) context.getAttribute(HTTP_CONN_KEEPALIVE);
                    if (!Boolean.TRUE.equals(keepalive)) {
                        this.outconn.close();
                        this.inconn.close();
                        break;
                    }
                }
            } catch (ConnectionClosedException ex) {
                System.err.println("Client closed connection");
            } catch (IOException ex) {
                System.err.println("I/O error: " + ex.getMessage());
            } catch (HttpException ex) {
                System.err.println("Unrecoverable HTTP protocol violation: " + ex.getMessage());
            } finally {
                try {
                    this.inconn.shutdown();
                } catch (IOException ignore) {}
                try {
                    this.outconn.shutdown();
                } catch (IOException ignore) {}
            }
        }

    }
    
}
