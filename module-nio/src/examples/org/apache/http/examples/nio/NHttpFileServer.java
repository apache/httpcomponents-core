/*
 * $HeadURL:$
 * $Revision:$
 * $Date:$
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpConnection;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.nio.DefaultServerIOEventDispatch;
import org.apache.http.impl.nio.reactor.DefaultListeningIOReactor;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.NHttpServiceHandler;
import org.apache.http.nio.entity.ContentBufferEntity;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.ListeningIOReactor;
import org.apache.http.nio.util.ContentInputBuffer;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.nio.util.SimpleInputBuffer;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpParamsLinker;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.util.EncodingUtils;

public class NHttpFileServer {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Please specify document root directory");
            System.exit(1);
        }
        HttpParams params = new BasicHttpParams();
        params
            .setIntParameter(HttpConnectionParams.SO_TIMEOUT, 20000)
            .setIntParameter(HttpConnectionParams.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(HttpConnectionParams.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(HttpConnectionParams.TCP_NODELAY, true)
            .setParameter(HttpProtocolParams.ORIGIN_SERVER, "HttpComponents/1.1");

        ListeningIOReactor ioReactor = new DefaultListeningIOReactor(2, params);

        NHttpServiceHandler handler = new FileServiceHandler(args[0], params);
        IOEventDispatch ioEventDispatch = new DefaultServerIOEventDispatch(handler, params);
        
        try {
            ioReactor.listen(new InetSocketAddress(8080));
            ioReactor.execute(ioEventDispatch);
        } catch (InterruptedIOException ex) {
            System.err.println("Interrupted");
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
        }
        System.out.println("Shutdown");
    }

    private static final String CONN_STATE    = "http.conn-state";
    private static final String OUT_CHANNEL   = "http.out-channel";
    
    static class ConnState {

        private final SimpleInputBuffer inbuf;
        private final ByteBuffer outbuf;
        
        public ConnState() {
            super();
            this.inbuf = new SimpleInputBuffer(2048, new HeapByteBufferAllocator());
            this.outbuf = ByteBuffer.allocateDirect(2048);
        }

        public ContentInputBuffer getInbuf() {
            return this.inbuf;
        }

        public ByteBuffer getOutbuf() {
            return this.outbuf;
        }

        public void reset() {
            this.inbuf.reset();
            this.outbuf.clear();
        }
        
    }
    
    public static class FileServiceHandler implements NHttpServiceHandler {

        private final String docRoot;
        private final HttpParams params;
        private final HttpResponseFactory responseFactory;
        private final HttpProcessor httpProcessor;
        private final ConnectionReuseStrategy connStrategy;
        
        public FileServiceHandler(final String docRoot, final HttpParams params) {
            super();
            this.docRoot = docRoot;
            this.params = params;
            this.responseFactory = new DefaultHttpResponseFactory();
            this.httpProcessor = createProtocolProcessor();
            this.connStrategy = new DefaultConnectionReuseStrategy();
        }

        private HttpProcessor createProtocolProcessor() {
            BasicHttpProcessor httpProcessor = new BasicHttpProcessor();
            httpProcessor.addInterceptor(new ResponseDate());
            httpProcessor.addInterceptor(new ResponseServer());                    
            httpProcessor.addInterceptor(new ResponseContent());
            httpProcessor.addInterceptor(new ResponseConnControl());
            return httpProcessor;
        }
        
        public void connected(final NHttpServerConnection conn) {
            System.out.println("New incoming connection");
            
            HttpContext context = conn.getContext();
            ConnState connState = new ConnState();
            
            context.setAttribute(CONN_STATE, connState);
        }

        public void closed(final NHttpServerConnection conn) {
            System.out.println("Connection closed");
            HttpContext context = conn.getContext();
            context.setAttribute(CONN_STATE, null);
        }

        public void timeout(final NHttpServerConnection conn) {
            System.err.println("Timeout");
            shutdownConnection(conn);
        }
        
        public void exception(final NHttpServerConnection conn, final IOException ex) {
            System.err.println("I/O error: " + ex.getMessage());
            shutdownConnection(conn);
        }

        public void exception(final NHttpServerConnection conn, final HttpException ex) {
            HttpContext context = conn.getContext();
            HttpResponse response =  this.responseFactory.newHttpResponse(
                    HttpVersion.HTTP_1_0, HttpStatus.SC_BAD_REQUEST, context);
            HttpParamsLinker.link(response, this.params);
            
            context.setAttribute(ExecutionContext.HTTP_RESPONSE, response);
            context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
            
            try {
                handleException(ex, response, context);
                this.httpProcessor.process(response, context);
                commitResponse(conn, response);
            } catch (HttpException ex2) {
                shutdownConnection(conn);
                System.err.println("Unexpected HTTP protocol error: " + ex2.getMessage());
            } catch (IOException ex2) {
                shutdownConnection(conn);
                System.err.println("I/O error: " + ex2.getMessage());
            }
        }

        private void shutdownConnection(final HttpConnection conn) {
            try {
                conn.shutdown();
            } catch (IOException ignore) {
            }
        }
        
        public void requestReceived(final NHttpServerConnection conn) {
            HttpContext context = conn.getContext();
            
            ConnState connState = (ConnState) context.getAttribute(CONN_STATE);
            connState.reset();
            
            HttpRequest request = conn.getHttpRequest();
            ProtocolVersion ver = request.getRequestLine().getProtocolVersion();

            try {
                if (request instanceof HttpEntityEnclosingRequest) {
                    
                    HttpEntityEnclosingRequest eeRequest = (HttpEntityEnclosingRequest) request;
                    if (eeRequest.expectContinue()) {
                        HttpResponse ack = this.responseFactory.newHttpResponse(
                                ver, 100, context);
                        conn.submitResponse(ack);
                    }
                    // Wait until the request content is fully received
                } else {
                    // No request content is expected. 
                    // Proceed with service right away
                    doService(conn, connState);
                }
            } catch (HttpException ex) {
                shutdownConnection(conn);
                System.err.println("Unexpected HTTP protocol error: " + ex.getMessage());
            } catch (IOException ex) {
                shutdownConnection(conn);
                System.err.println("I/O error: " + ex.getMessage());
            }
        }

        public void responseReady(final NHttpServerConnection conn) {
        }

        public void inputReady(final NHttpServerConnection conn, final ContentDecoder decoder) {
            HttpContext context = conn.getContext();

            ConnState connState = (ConnState) context.getAttribute(
                    CONN_STATE);
            
            ContentInputBuffer inbuf = connState.getInbuf();
            try {
                inbuf.consumeContent(decoder);
                if (decoder.isCompleted()) {
                    // Request entity has been fully received
                    doService(conn, connState);
                }
                
            } catch (HttpException ex) {
                shutdownConnection(conn);
                System.err.println("Unexpected HTTP protocol error: " + ex.getMessage());
            } catch (IOException ex) {
                shutdownConnection(conn);
                System.err.println("I/O error: " + ex.getMessage());
            }
        }

        public void outputReady(final NHttpServerConnection conn, final ContentEncoder encoder) {
            HttpContext context = conn.getContext();

            ConnState connState = (ConnState) context.getAttribute(
                    CONN_STATE);
            ReadableByteChannel channel = (ReadableByteChannel) context.getAttribute(
                    OUT_CHANNEL);

            HttpResponse response = conn.getHttpResponse();
            ByteBuffer outbuf = connState.getOutbuf();
            try {
                int bytesRead = channel.read(outbuf);
                if (bytesRead == -1) {
                    encoder.complete();
                } else {
                    outbuf.flip();
                    encoder.write(outbuf);
                    outbuf.compact();
                }

                if (encoder.isCompleted()) {
                    channel.close();
                    if (!this.connStrategy.keepAlive(response, context)) {
                        conn.close();
                    }
                }
            
            } catch (IOException ex) {
                shutdownConnection(conn);
                System.err.println("I/O error: " + ex.getMessage());
            }

        }

        private void commitResponse(
                final NHttpServerConnection conn,
                final HttpResponse response) throws HttpException, IOException {
            ReadableByteChannel channel = null;
            if (response.getEntity() != null) {
                InputStream instream = response.getEntity().getContent(); 
                if (instream instanceof FileInputStream) {
                    channel = ((FileInputStream)instream).getChannel();
                } else {
                    channel = Channels.newChannel(instream);
                }
                
            }
            conn.getContext().setAttribute(OUT_CHANNEL, channel);
            conn.submitResponse(response);
        }

        private void doService(
                final NHttpServerConnection conn,
                final ConnState connState) throws HttpException, IOException {
            HttpContext context = conn.getContext();
            HttpRequest request = conn.getHttpRequest();
            HttpParamsLinker.link(request, this.params);

            if (request instanceof HttpEntityEnclosingRequest) {
                HttpEntityEnclosingRequest eeRequest = (HttpEntityEnclosingRequest) request;
                // Create a wrapper entity instead of the original one
                if (eeRequest.getEntity() != null) {
                    eeRequest.setEntity(new ContentBufferEntity(
                            eeRequest.getEntity(), 
                            connState.getInbuf()));
                }
            }
            
            ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
            HttpResponse response =  this.responseFactory.newHttpResponse(ver, 200, context);
            HttpParamsLinker.link(response, this.params);
            
            context.setAttribute(ExecutionContext.HTTP_REQUEST, request);
            context.setAttribute(ExecutionContext.HTTP_RESPONSE, response);
            context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);

            try {
                this.httpProcessor.process(request, context);
                handleRequest(request, response, context);
            } catch (HttpException ex) {
                handleException(ex, response, context);
            }
            this.httpProcessor.process(response, context);
            commitResponse(conn, response);
        }
        
        private void handleRequest(
                final HttpRequest request, 
                final HttpResponse response,
                final HttpContext context) {

            String target = request.getRequestLine().getUri();
            File file;
            try {
                file = new File(this.docRoot, URLDecoder.decode(target, "UTF-8"));
            } catch (UnsupportedEncodingException ex) {
                throw new Error("UTF-8 not supported");
            }

            if (!file.exists()) {
                
                response.setStatusCode(HttpStatus.SC_NOT_FOUND);
                byte[] msg = EncodingUtils.getAsciiBytes(
                        file.getName() + ": not found");
                ByteArrayEntity entity = new ByteArrayEntity(msg);
                entity.setContentType("text/plain; charset=US-ASCII");
                response.setEntity(entity);
                
            } else if (!file.canRead() || file.isDirectory()) {

                response.setStatusCode(HttpStatus.SC_FORBIDDEN);
                byte[] msg = EncodingUtils.getAsciiBytes(
                        file.getName() + ": access denied");
                ByteArrayEntity entity = new ByteArrayEntity(msg);
                entity.setContentType("text/plain; charset=US-ASCII");
                response.setEntity(entity);

            } else {

                FileEntity entity = new FileEntity(file, "text/html");
                response.setEntity(entity);
                
            }
        }
     
        private void handleException(
                final HttpException ex, 
                final HttpResponse response,
                final HttpContext context) {
            response.setStatusLine(HttpVersion.HTTP_1_0, HttpStatus.SC_BAD_REQUEST);
            byte[] msg = EncodingUtils.getAsciiBytes(
                    "Malformed HTTP request: " + ex.getMessage());
            ByteArrayEntity entity = new ByteArrayEntity(msg);
            entity.setContentType("text/plain; charset=US-ASCII");
            response.setEntity(entity);
        }
        
    }

}
