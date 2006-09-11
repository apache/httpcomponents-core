/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2006 The Apache Software Foundation
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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;

import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpServerConnection;
import org.apache.http.HttpStatus;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.entity.ContentProducer;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.DefaultHttpParams;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExecutionContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.protocol.SyncHttpExecutionContext;

/**
 * Basic, yet fully functional and spec compliant, HTTP/1.1 file server.
 * 
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
    
    static class HttpFileHandler implements HttpRequestHandler  {
        
        public void handle(
                final HttpRequest request, 
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {

            String method = request.getRequestLine().getMethod();
            if (!method.equalsIgnoreCase("GET") && !method.equalsIgnoreCase("HEAD")) {
                throw new MethodNotSupportedException(method + " method not supported"); 
            }
            String docroot = (String) context.getAttribute("server.docroot");
            
            String target = request.getRequestLine().getUri();
            
            final File file = new File(docroot, URLDecoder.decode(target));
            if (!file.exists()) {

                response.setStatusCode(HttpStatus.SC_NOT_FOUND);
                EntityTemplate body = new EntityTemplate(new ContentProducer() {
                    
                    public void writeTo(final OutputStream outstream) throws IOException {
                        OutputStreamWriter writer = new OutputStreamWriter(outstream, "UTF-8"); 
                        writer.write("<html><body><h1>");
                        writer.write("File ");
                        writer.write(file.getPath());
                        writer.write(" not found");
                        writer.write("</h1></body></html>");
                        writer.flush();
                    }
                    
                });
                body.setContentType("text/html; charset=UTF-8");
                response.setEntity(body);
                System.out.println("File " + file.getPath() + " not found");
                
            } else if (!file.canRead() || file.isDirectory()) {
                
                response.setStatusCode(HttpStatus.SC_FORBIDDEN);
                EntityTemplate body = new EntityTemplate(new ContentProducer() {
                    
                    public void writeTo(final OutputStream outstream) throws IOException {
                        OutputStreamWriter writer = new OutputStreamWriter(outstream, "UTF-8"); 
                        writer.write("<html><body><h1>");
                        writer.write("Access denied");
                        writer.write("</h1></body></html>");
                        writer.flush();
                    }
                    
                });
                body.setContentType("text/html; charset=UTF-8");
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
        private final HttpParams params; 
        private final HttpContext globalContext;
        
        public RequestListenerThread(int port, final String docroot) throws IOException {
            this.serversocket = new ServerSocket(port);
            this.params = new DefaultHttpParams(null);
            this.params
                .setIntParameter(HttpConnectionParams.SO_TIMEOUT, 5000)
                .setIntParameter(HttpConnectionParams.SOCKET_BUFFER_SIZE, 8 * 1024)
                .setBooleanParameter(HttpConnectionParams.STALE_CONNECTION_CHECK, false)
                .setBooleanParameter(HttpConnectionParams.TCP_NODELAY, true)
                .setParameter(HttpProtocolParams.ORIGIN_SERVER, "Jakarta-HttpComponents/1.1");
            this.globalContext = new SyncHttpExecutionContext(null);
            this.globalContext.setAttribute("server.docroot", docroot);
        }
        
        public void run() {
            System.out.println("Listening on port " + this.serversocket.getLocalPort());
            while (!Thread.interrupted()) {
                try {
                    // Set up HTTP connection
                	Socket socket = this.serversocket.accept();
                    DefaultHttpServerConnection conn = new DefaultHttpServerConnection();
                    System.out.println("Incoming connection from " + socket.getInetAddress());
                    conn.bind(socket, this.params);

                    // Set up HTTP service
                    HttpService httpService = new HttpService();
                    httpService.addInterceptor(new ResponseDate());
                    httpService.addInterceptor(new ResponseServer());                    
                    httpService.addInterceptor(new ResponseContent());
                    httpService.addInterceptor(new ResponseConnControl());
                    httpService.setParams(this.params);
                    httpService.registerRequestHandler("*", new HttpFileHandler());
                    
                    // Start worker thread
                    Thread t = new WorkerThread(httpService, conn, this.globalContext);
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
    
    static class WorkerThread extends Thread {

        private final HttpService httpservice;
        private final HttpServerConnection conn;
        private final HttpContext context;
        
        public WorkerThread(
                final HttpService httpservice, 
                final HttpServerConnection conn, 
                final HttpContext parentContext) {
            super();
            this.httpservice = httpservice;
            this.conn = conn;
            this.context = new HttpExecutionContext(parentContext);
        }
        
        public void run() {
            System.out.println("New connection thread");
            try {
                while (!Thread.interrupted() && this.conn.isOpen()) {
                    this.httpservice.handleRequest(this.conn, this.context);
                }
            } catch (ConnectionClosedException ex) {
                System.err.println("Client closed connection");
            } catch (IOException ex) {
                System.err.println("I/O error: " + ex.getMessage());
            } catch (HttpException ex) {
                System.err.println("Unrecoverable HTTP protocol violation: " + ex.getMessage());
            } finally {
                try {
                    this.conn.shutdown();
                } catch (IOException ignore) {}
            }
        }

    }
    
}
