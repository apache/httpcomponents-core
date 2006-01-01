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

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpMutableRequest;
import org.apache.http.HttpMutableResponse;
import org.apache.http.HttpRequest;
import org.apache.http.HttpServerConnection;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.ProtocolException;
import org.apache.http.entity.StringEntity;
import org.apache.http.executor.HttpExecutionContext;
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
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.util.EntityUtils;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 */
public class ElementalHttpEchoServer {

    public static void main(String[] args) throws Exception {
        Thread t = new RequestListenerThread(8080);
        t.setDaemon(false);
        t.start();
    }
    
    static interface ServiceHandler {

        void service(HttpRequest request, HttpMutableResponse response) 
            throws IOException;
        
    }
    
    static class HttpRequestProcessor extends AbstractHttpProcessor {
        
        private final HttpServerConnection conn;
        private final ConnectionReuseStrategy connreuse;
        
        private HttpParams params = null;

        public HttpRequestProcessor(final HttpServerConnection conn) {
            super(new HttpExecutionContext(null));
            if (conn == null) {
                throw new IllegalArgumentException("HTTP server connection may not be null");
            }
            this.conn = conn;
            this.connreuse = new DefaultConnectionReuseStrategy();
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
                HttpRequest request = this.conn.receiveRequest(this.params);
                
                if (request instanceof HttpMutableRequest) {
                    preprocessRequest((HttpMutableRequest)request);
                }

                HttpVersion ver = request.getRequestLine().getHttpVersion();
                if (ver.greaterEquals(HttpVersion.HTTP_1_1)) {
                    ver = HttpVersion.HTTP_1_1;
                }
                HttpProtocolParams.setVersion(response.getParams(), ver);
                
                if (request instanceof HttpEntityEnclosingRequest) {
                    if (((HttpEntityEnclosingRequest) request).expectContinue()) {

                        System.out.println("Expected 100 (Continue)");
                        
                        BasicHttpResponse ack = new BasicHttpResponse();
                        ack.getParams().setDefaults(this.params);
                        ack.setStatusCode(HttpStatus.SC_CONTINUE);
                        this.conn.sendResponse(ack);
                    }
                }
                System.out.println("Request received");
                localContext.setAttribute(HttpExecutionContext.HTTP_REQUEST, request);
                localContext.setAttribute(HttpExecutionContext.HTTP_RESPONSE, response);
                handler.service(request, response);
            } catch (ConnectionClosedException ex) {
                System.out.println("Client closed connection");
                return;
            } catch (HttpException ex) {
                handleException(ex, response);
            } catch (IOException ex) {
                System.err.println("I/O error receiving request: " + ex.getMessage());
                closeConnection();
                return;
            }
            try {
                if (response instanceof HttpMutableResponse) {
                    postprocessResponse((HttpMutableResponse)response);
                }
                this.conn.sendResponse(response);
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
            if (!this.connreuse.keepAlive(response)) {
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
    
    static class EchoServiceHandler implements ServiceHandler{
        
        public EchoServiceHandler() {
            super();
        }
        
        public void service(final HttpRequest request, final HttpMutableResponse response) 
                throws IOException {
            StringBuffer buffer = new StringBuffer();
            buffer.append("<html>");
            buffer.append("<body>");
            buffer.append("<p>Request method: ");
            buffer.append(request.getRequestLine().getMethod());
            buffer.append("</p>");
            buffer.append("<p>Request URI: ");
            buffer.append(request.getRequestLine().getUri());
            buffer.append("</p>");
            buffer.append("<p>Request Version: ");
            buffer.append(request.getRequestLine().getHttpVersion());
            buffer.append("</p>");
            if (request instanceof HttpEntityEnclosingRequest) {
                HttpEntity entity = ((HttpEntityEnclosingRequest)request).getEntity();
                buffer.append("<p>Content Type: ");
                buffer.append(entity.getContentType());
                buffer.append("</p>");
                buffer.append("<p>Content Chunk-coded: ");
                buffer.append(entity.isChunked());
                buffer.append("</p>");
                if (entity.getContentType() != null 
                        && entity.getContentType().getValue().toLowerCase().startsWith("text/")) {
                    buffer.append("<p>");
                    buffer.append(EntityUtils.toString(entity));
                    buffer.append("</p>");
                } else {
                    byte[] raw = EntityUtils.toByteArray(entity);
                    buffer.append("<p>");
                    for (int i = 0; i < raw.length; i++) {
                        buffer.append(Integer.toHexString(raw[i]).toLowerCase());
                        if (i % 20 == 19) {
                            buffer.append("<br>");
                        } else {
                            buffer.append(" ");
                        }
                    }
                    buffer.append("</p>");
                }
            }
            buffer.append("</body>");
            buffer.append("</html>");
            StringEntity body = new StringEntity(buffer.toString());
            body.setContentType("text/html; charset=UTF-8");
            response.setEntity(body);

            response.setStatusCode(HttpStatus.SC_OK);
        }
        
    }
    
    static class RequestListenerThread extends Thread {

        private final ServerSocket serversocket;
        private HttpParams params; 
        
        public RequestListenerThread(int port) throws IOException {
            this.serversocket = new ServerSocket(port);
            this.params = new DefaultHttpParams(null);
            this.params
                .setIntParameter(HttpConnectionParams.SO_TIMEOUT, 5000)
                .setIntParameter(HttpConnectionParams.SOCKET_BUFFER_SIZE, 8 * 1024)
                .setBooleanParameter(HttpConnectionParams.STALE_CONNECTION_CHECK, false)
                .setBooleanParameter(HttpConnectionParams.TCP_NODELAY, true)
                .setParameter(HttpProtocolParams.ORIGIN_SERVER, "Elemental Server/1.1");
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
                    processor.addResponseInterceptor(new ResponseContent());
                    processor.addResponseInterceptor(new ResponseConnControl());
                    processor.addResponseInterceptor(new ResponseDate());
                    processor.addResponseInterceptor(new ResponseServer());                    
                    processor.setParams(this.params);
                    Thread t = new ConnectionProcessorThread(processor, new EchoServiceHandler());
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
