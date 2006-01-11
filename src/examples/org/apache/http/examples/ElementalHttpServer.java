/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.examples;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;

import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpMutableEntityEnclosingRequest;
import org.apache.http.HttpMutableRequest;
import org.apache.http.HttpMutableResponse;
import org.apache.http.HttpRequest;
import org.apache.http.HttpServerConnection;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.ProtocolException;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.ConnectionReuseStrategy;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpParams;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.AbstractHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExecutionContext;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 */
public class ElementalHttpServer {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Please specify document root directory");
            System.exit(1);
        }
        Thread t = new RequestListenerThread(8080, args[0]);
        t.setDaemon(false);
        t.start();
    }
    
    static interface ServiceHandler {

        void service(HttpRequest request, HttpMutableResponse response) 
            throws IOException;
        
    }
    
    static class HttpRequestProcessor extends AbstractHttpProcessor {
        
        private final HttpServerConnection conn;
        private final ConnectionReuseStrategy connStrategy;
        
        private HttpParams params = null;

        public HttpRequestProcessor(final HttpServerConnection conn) {
            super(new HttpExecutionContext(null));
            if (conn == null) {
                throw new IllegalArgumentException("HTTP server connection may not be null");
            }
            this.conn = conn;
            this.connStrategy = new DefaultConnectionReuseStrategy();
        }

        public HttpParams getParams() {
            return this.params;
        }
        
        public void setParams(final HttpParams params) {
            this.params = params;
        }
        
        public boolean isActive() {
            return this.conn.isOpen();
        }
        
        private void closeConnection() {
            try {
                this.conn.close();
                System.out.println("Connection closed");
            } catch (IOException ex) {
                System.err.println("I/O error closing connection: " + ex.getMessage());
            }
        }
                
        public void doService(final ServiceHandler handler) { 
            HttpContext localContext = getContext();
            localContext.setAttribute(HttpExecutionContext.HTTP_CONNECTION, this.conn);
            BasicHttpResponse response = new BasicHttpResponse();
            response.getParams().setDefaults(this.params);
            try {
                HttpMutableRequest request = this.conn.receiveRequestHeader(this.params);
                if (request instanceof HttpEntityEnclosingRequest) {
                    if (((HttpMutableEntityEnclosingRequest) request).expectContinue()) {

                        System.out.println("Expected 100 (Continue)");
                        
                        BasicHttpResponse ack = new BasicHttpResponse();
                        ack.getParams().setDefaults(this.params);
                        ack.setStatusCode(HttpStatus.SC_CONTINUE);
                        this.conn.sendResponseHeader(ack);
                        this.conn.flush();
                    }
                    this.conn.receiveRequestEntity((HttpMutableEntityEnclosingRequest) request);
                }
                preprocessRequest(request);
                System.out.println("Request received");

                HttpVersion ver = request.getRequestLine().getHttpVersion();
                if (ver.greaterEquals(HttpVersion.HTTP_1_1)) {
                    ver = HttpVersion.HTTP_1_1;
                }
                HttpProtocolParams.setVersion(response.getParams(), ver);
                
                localContext.setAttribute(HttpExecutionContext.HTTP_REQUEST, request);
                localContext.setAttribute(HttpExecutionContext.HTTP_RESPONSE, response);
                handler.service(request, response);
                
                if (request instanceof HttpEntityEnclosingRequest) {
                    // Make sure the request content is fully consumed
                    HttpEntity entity = ((HttpEntityEnclosingRequest)request).getEntity();
                    if (entity != null && entity.getContent() != null) {
                        entity.getContent().close();
                    }
                }
                
                postprocessResponse(response);
            } catch (ConnectionClosedException ex) {
                System.out.println("Client closed connection");
                closeConnection();
                return;
            } catch (HttpException ex) {
                handleException(ex, response);
            } catch (IOException ex) {
                System.err.println("I/O error receiving request: " + ex.getMessage());
                closeConnection();
                return;
            }
            try {
                this.conn.sendResponseHeader(response);
                this.conn.sendResponseEntity(response);
                this.conn.flush();
                System.out.println("Response sent");
            } catch (HttpException ex) {
                System.err.println("Malformed response: " + ex.getMessage());
                closeConnection();
                return;
            } catch (IOException ex) {
                System.err.println("I/O error sending response: " + ex.getMessage());
                closeConnection();
                return;
            }
            if (!this.connStrategy.keepAlive(response)) {
                closeConnection();
            } else {
                System.out.println("Connection kept alive");
            }
        }
        
        private void handleException(final HttpException ex, final HttpMutableResponse response) {
            if (ex instanceof MethodNotSupportedException) {
                response.setStatusCode(HttpStatus.SC_NOT_IMPLEMENTED);
            } else if (ex instanceof ProtocolException) {
                response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            } else {
                response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }
        }
                
    }
    
    static class FileServiceHandler implements ServiceHandler{
        
        public FileServiceHandler() {
            super();
        }
        
        public void service(final HttpRequest request, final HttpMutableResponse response) 
                throws IOException {
            String docroot = (String) request.getParams().getParameter("server.docroot");
            String target = request.getRequestLine().getUri();
            File file = new File(docroot, URLDecoder.decode(target));
            if (!file.exists()) {
                response.setStatusCode(HttpStatus.SC_NOT_FOUND);
                StringEntity body = new StringEntity("File not found", "UTF-8");
                response.setEntity(body);
                System.out.println("File " + file.getPath() + " not found");
            } else if (!file.canRead()) {
                response.setStatusCode(HttpStatus.SC_FORBIDDEN);
                StringEntity body = new StringEntity("Access Denied", "UTF-8");
                response.setEntity(body);
                System.out.println("Cannot read file " + file.getPath());
            } else {
                response.setStatusCode(HttpStatus.SC_OK);
                FileEntity body = new FileEntity(file, "text/html");
                response.setEntity(body);
                System.out.println("Serving file " + file.getPath());
            }
        }
        
    }
    
    static class RequestListenerThread extends Thread {

        private final ServerSocket serversocket;
        private HttpParams params; 
        
        public RequestListenerThread(int port, final String docroot) throws IOException {
            this.serversocket = new ServerSocket(port);
            this.params = new DefaultHttpParams(null);
            this.params
                .setIntParameter(HttpConnectionParams.SO_TIMEOUT, 5000)
                .setIntParameter(HttpConnectionParams.SOCKET_BUFFER_SIZE, 8 * 1024)
                .setBooleanParameter(HttpConnectionParams.STALE_CONNECTION_CHECK, false)
                .setBooleanParameter(HttpConnectionParams.TCP_NODELAY, true)
                .setParameter(HttpProtocolParams.ORIGIN_SERVER, "Jakarta-HttpComponents/1.1")
                .setParameter("server.docroot", docroot);
        }
        
        public void run() {
            System.out.println("Listening on port " + this.serversocket.getLocalPort());
            while (!Thread.interrupted()) {
                try {
                	Socket socket = this.serversocket.accept();
                    HttpServerConnection conn = new DefaultHttpServerConnection();
                    System.out.println("Incoming connection from " + socket.getInetAddress());
                    conn.bind(socket, this.params);
                    HttpRequestProcessor processor = new HttpRequestProcessor(conn);
                    // Add required protocol interceptors
                    processor.addInterceptor(new ResponseContent());
                    processor.addInterceptor(new ResponseConnControl());
                    processor.addInterceptor(new ResponseDate());
                    processor.addInterceptor(new ResponseServer());                    
                    processor.setParams(this.params);
                    Thread t = new ConnectionProcessorThread(processor, new FileServiceHandler());
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
    
    static class ConnectionProcessorThread extends Thread {

        private final HttpRequestProcessor processor;
        private final ServiceHandler handler;
        
        public ConnectionProcessorThread(
                final HttpRequestProcessor processor, 
                final ServiceHandler handler) {
            super();
            this.processor = processor;
            this.handler = handler;
        }
        
        public void run() {
            System.out.println("New connection thread");
            while (!Thread.interrupted() && this.processor.isActive()) {
                this.processor.doService(this.handler);
            }
        }

    }
}
