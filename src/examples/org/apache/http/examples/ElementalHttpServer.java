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

import org.apache.http.HttpException;
import org.apache.http.HttpMutableResponse;
import org.apache.http.HttpRequest;
import org.apache.http.HttpServerConnection;
import org.apache.http.HttpStatus;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultHttpParams;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HttpService;
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
    
    static class FileServiceHandler extends HttpService {
        
        public FileServiceHandler(final HttpServerConnection conn) {
            super(conn);
        }

        protected void doService(final HttpRequest request, final HttpMutableResponse response) 
                throws HttpException, IOException {
            String method = request.getRequestLine().getMethod();
            if (!method.equalsIgnoreCase("GET") && !method.equalsIgnoreCase("HEAD")) {
                throw new MethodNotSupportedException(method + " method not supported"); 
            }
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
        
        protected void logMessage(final String s) {
            System.out.println(s);
        }
        
        protected void logIOException(final IOException ex) {
            System.err.println("IO error: " + ex.getMessage());
        }
        
        protected void logProtocolException(final HttpException ex) {
            System.err.println("HTTP protocol error: " + ex.getMessage());
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
                    FileServiceHandler fileServiceHandler = new FileServiceHandler(conn);
                    // Add required protocol interceptors
                    fileServiceHandler.addInterceptor(new ResponseContent());
                    fileServiceHandler.addInterceptor(new ResponseConnControl());
                    fileServiceHandler.addInterceptor(new ResponseDate());
                    fileServiceHandler.addInterceptor(new ResponseServer());                    
                    fileServiceHandler.setParams(this.params);
                    Thread t = new ConnectionProcessorThread(fileServiceHandler);
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

        private final HttpService httpservice;
        
        public ConnectionProcessorThread(final HttpService httpservice) {
            super();
            this.httpservice = httpservice;
        }
        
        public void run() {
            System.out.println("New connection thread");
            while (!Thread.interrupted() && this.httpservice.isActive()) {
                this.httpservice.handleRequest();
            }
        }

    }
}
