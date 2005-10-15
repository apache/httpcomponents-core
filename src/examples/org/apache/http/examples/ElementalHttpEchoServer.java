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
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpMutableResponse;
import org.apache.http.HttpRequest;
import org.apache.http.HttpServerConnection;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.ProtocolException;
import org.apache.http.entity.EntityConsumer;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.ConnectionReuseStrategy;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpParams;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 */
public class ElementalHttpEchoServer {

    private static final String TEST_SERVER = "Test server"; 
    
    public static void main(String[] args) throws Exception {
        Thread t = new RequestListenerThread(8080);
        t.setDaemon(false);
        t.start();
    }
    
    static class RequestHandler {
        
        public RequestHandler() {
            super();
        }
        
        public void handleRequest(final HttpRequest request, final HttpMutableResponse response) 
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
                EntityConsumer consume = new EntityConsumer((HttpEntityEnclosingRequest)request);
                if (entity.getContentType() != null 
                        && entity.getContentType().toLowerCase().startsWith("text/")) {
                    buffer.append("<p>");
                    buffer.append(consume.asString());
                    buffer.append("</p>");
                } else {
                    byte[] raw = consume.asByteArray();
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

            response.setStatusCode(HttpStatus.SC_OK);
            response.setHeader(new Header("Server", TEST_SERVER));
            response.setHeader(new Header("Connection", "Keep-Alive"));
            if (body.isChunked() || body.getContentLength() < 0) {
                response.setHeader(new Header("Transfer-Encoding", "chunked"));
            } else {
                response.setHeader(new Header("Content-Length", 
                        Long.toString(body.getContentLength())));
            }
            if (body.getContentType() != null) {
                response.setHeader(new Header("Content-Type", body.getContentType())); 
            }
            response.setEntity(body);
        }
        
        public void handleException(final HttpException ex, final HttpMutableResponse response) {
            if (ex instanceof MethodNotSupportedException) {
                response.setStatusCode(HttpStatus.SC_NOT_IMPLEMENTED);
            } else if (ex instanceof ProtocolException) {
                response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            } else {
                response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }
            response.setHeader(new Header("Server", TEST_SERVER));
            response.setHeader(new Header("Connection", "Close"));
        }
    }
    
    static class RequestListenerThread extends Thread {

        private final ServerSocket serversocket;
        private HttpParams params; 
        
        public RequestListenerThread(int port) throws IOException {
            this.serversocket = new ServerSocket(port);
            this.params = new DefaultHttpParams(null); 
        }
        
        public void run() {
            System.out.println("Listening on port " + this.serversocket.getLocalPort());
            while (!Thread.interrupted()) {
                try {
                	Socket socket = this.serversocket.accept();
                    HttpServerConnection conn = new DefaultHttpServerConnection();
                    System.out.println("Incoming connection from " + socket.getInetAddress());
                    conn.bind(socket, this.params);
                    Thread t = new HttpConnectionThread(conn);
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
    
    static class HttpConnectionThread extends Thread {

        private final HttpServerConnection conn;
        private final HttpParams params;
        private final RequestHandler handler;
        
        public HttpConnectionThread(final HttpServerConnection conn) {
            super();
            this.conn = conn;
            this.params = new DefaultHttpParams(null);
            HttpConnectionParams.setSoTimeout(this.params, 5000); 
            this.handler = new RequestHandler();
        }
        
        public HttpParams getParams() {
            return this.params;
        }
        
        public void closeConnection() {
            try {
                this.conn.close();
                System.out.println("Connection closed");
            } catch (IOException ex) {
                System.err.println("I/O error closing connection: " + ex.getMessage());
            }
        }
        
        public void run() {
            System.out.println("New connection thread");
            while (!Thread.interrupted()) {
                BasicHttpResponse response = new BasicHttpResponse();
                response.getParams().setDefaults(this.params);
                try {
                    HttpRequest request = this.conn.receiveRequest(this.params);
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
                    this.handler.handleRequest(request, response);
                } catch (ConnectionClosedException ex) {
                    System.out.println("Client closed connection");
                    break;
                } catch (HttpException ex) {
                    this.handler.handleException(ex, response);
                } catch (IOException ex) {
                    System.err.println("I/O error receiving request: " + ex.getMessage());
                    closeConnection();
                    break;
                }
                try {
                    this.conn.sendResponse(response);
                    System.out.println("Response sent");
                } catch (HttpException ex) {
                    System.err.println("Malformed response: " + ex.getMessage());
                    closeConnection();
                } catch (IOException ex) {
                    System.err.println("I/O error sending response: " + ex.getMessage());
                    closeConnection();
                    break;
                }
                ConnectionReuseStrategy connreuse = new DefaultConnectionReuseStrategy();
                if (!connreuse.keepAlive(response)) {
                    closeConnection();
                    break;
                } else {
                    System.out.println("Connection kept alive");
                }
            }
        }

    }
}
