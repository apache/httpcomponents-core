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

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.Header;
import org.apache.http.HttpConnection;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.nio.DefaultServerIOEventDispatch;
import org.apache.http.impl.nio.reactor.DefaultListeningIOReactor;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.FileContentDecoder;
import org.apache.http.nio.FileContentEncoder;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.NHttpServiceHandler;
import org.apache.http.nio.entity.ByteArrayNIOEntity;
import org.apache.http.nio.entity.FileNIOEntity;
import org.apache.http.nio.entity.HttpNIOEntity;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.ListeningIOReactor;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpParamsLinker;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HTTP;
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
        boolean useFileChannels = true;
        if (args.length >= 2) {
            String s = args[1];
            if (s.equalsIgnoreCase("disableFileChannels")) {
                useFileChannels = false;
            }
        }
        HttpParams params = new BasicHttpParams();
        params
            .setIntParameter(HttpConnectionParams.SO_TIMEOUT, 20000)
            .setIntParameter(HttpConnectionParams.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(HttpConnectionParams.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(HttpConnectionParams.TCP_NODELAY, true)
            .setParameter(HttpProtocolParams.ORIGIN_SERVER, "HttpComponents/1.1");

        ListeningIOReactor ioReactor = new DefaultListeningIOReactor(2, params);

        NHttpServiceHandler handler = new FileServiceHandler(args[0], useFileChannels, params);
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
    
    static class ConnState {

        private final ByteBuffer inbuf;
        private final ByteBuffer outbuf;
        
        private File fileHandle;
        private RandomAccessFile inputFile;
        private WritableByteChannel inputChannel;
        private ReadableByteChannel outputChannel;
        private long inputCount;
        private long outputCount;
        
        public ConnState() {
            super();
            this.inbuf = ByteBuffer.allocateDirect(2048);
            this.outbuf = ByteBuffer.allocateDirect(2048);
        }

        public ByteBuffer getInbuf() {
            return this.inbuf;
        }

        public ByteBuffer getOutbuf() {
            return this.outbuf;
        }

        public File getInputFile() throws IOException {
            if (this.fileHandle == null) {
                this.fileHandle = File.createTempFile("tmp", ".tmp", null);
            }
            return this.fileHandle;
        }
        
        public WritableByteChannel getInputChannel() throws IOException {
            if (this.inputFile == null) {
                this.inputFile = new RandomAccessFile(getInputFile(), "rw");
            }
            if (this.inputChannel == null) {
                this.inputChannel = this.inputFile.getChannel();
            }
            return this.inputChannel;
        }
        
        public void setOutputChannel(final ReadableByteChannel channel) {
            this.outputChannel = channel;
        }
        
        public ReadableByteChannel getOutputChannel() {
            return this.outputChannel;
        }
        
        public long getInputCount() {
            return this.inputCount;
        }
        
        public void incrementInputCount(long count) {
            this.inputCount += count;
        }
        
        public long getOutputCount() {
            return this.outputCount;
        }
        
        public void incrementOutputCount(long count) {
            this.outputCount += count;
        }
        
        public void reset() throws IOException {
            this.inbuf.clear();
            this.outbuf.clear();
            this.inputCount = 0;
            this.outputCount = 0;
            if (this.inputChannel != null) {
                this.inputChannel.close();
                this.inputChannel = null;
            }
            if (this.inputFile != null) {
                this.inputFile.close();
                this.inputFile = null;
            }
            if (this.fileHandle != null) {
                this.fileHandle.delete();
                this.fileHandle = null;
            }
        }
        
    }
    
    public static class FileServiceHandler implements NHttpServiceHandler {

        private final String docRoot;
        private boolean useFileChannels;
        private final HttpParams params;
        private final HttpResponseFactory responseFactory;
        private final HttpProcessor httpProcessor;
        private final ConnectionReuseStrategy connStrategy;
        
        public FileServiceHandler(
                final String docRoot, 
                boolean useFileChannels, 
                final HttpParams params) {
            super();
            this.docRoot = docRoot;
            this.useFileChannels = useFileChannels;
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

            ConnState connState = (ConnState) context.getAttribute(CONN_STATE);
            try {
                connState.reset();
            } catch (IOException ex) {
                System.err.println("I/O error: " + ex.getMessage());
            }
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
            if (conn.isResponseSubmitted()) {
                System.err.println("Unexpected HTTP protocol error: " + ex.getMessage());
                return;
            }
            
            HttpContext context = conn.getContext();

            ConnState connState = (ConnState) context.getAttribute(CONN_STATE);
            
            HttpResponse response =  this.responseFactory.newHttpResponse(
                    HttpVersion.HTTP_1_0, HttpStatus.SC_BAD_REQUEST, context);
            HttpParamsLinker.link(response, this.params);
            
            context.setAttribute(ExecutionContext.HTTP_RESPONSE, response);
            context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
            
            try {
                handleException(ex, response, context);
                this.httpProcessor.process(response, context);
                commitResponse(conn, connState, response);
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
            
            HttpRequest request = conn.getHttpRequest();
            ProtocolVersion ver = request.getRequestLine().getProtocolVersion();

            try {
                connState.reset();

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
            try {

                WritableByteChannel channel = connState.getInputChannel();
                long transferred;

                // Test if the decoder is capable of direct transfer to file
                if (this.useFileChannels && 
                        decoder instanceof FileContentDecoder && 
                        channel instanceof FileChannel) {
                    long pos = connState.getInputCount();
                    transferred = ((FileContentDecoder) decoder).transfer(
                            (FileChannel) channel, pos, Integer.MAX_VALUE);
                } else {
                    ByteBuffer buf = connState.getInbuf();
                    decoder.read(buf);
                    buf.flip();
                    transferred = channel.write(buf);
                    buf.compact();
                }
                connState.incrementInputCount(transferred);
                
                if (decoder.isCompleted()) {
                    // Request entity has been fully received
                    channel.close();
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
            HttpResponse response = conn.getHttpResponse();
            try {

                ReadableByteChannel channel = connState.getOutputChannel();
                long transferred;

                // Test if the encoder is capable of direct transfer from file
                if (this.useFileChannels && 
                        encoder instanceof FileContentEncoder && 
                        channel instanceof FileChannel) {
                    long pos = connState.getOutputCount();
                    transferred = ((FileContentEncoder) encoder).transfer(
                            (FileChannel) channel, pos, Integer.MAX_VALUE);
                } else {
                    ByteBuffer outbuf = connState.getOutbuf();
                    transferred = channel.read(outbuf);
                    if (transferred != -1) {
                        outbuf.flip();
                        encoder.write(outbuf);
                        outbuf.compact();
                    }
                }
                if (transferred == -1) {
                    encoder.complete();
                }
                if (transferred > 0) {
                    connState.incrementOutputCount(transferred);
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
                final ConnState connState,
                final HttpResponse response) throws HttpException, IOException {
            
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                ReadableByteChannel channel;
                if (entity instanceof HttpNIOEntity) {
                    channel = ((HttpNIOEntity) entity).getChannel();
                } else {
                    channel = Channels.newChannel(entity.getContent());
                }
                connState.setOutputChannel(channel);
            }
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
                Header h = request.getFirstHeader(HTTP.CONTENT_TYPE);
                String contentType = null;
                if (h != null) {
                    contentType = h.getValue();
                }
                HttpNIOEntity entity = new FileNIOEntity(connState.getInputFile(), contentType);
                eeRequest.setEntity(entity);
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
            commitResponse(conn, connState, response);
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
                ByteArrayNIOEntity entity = new ByteArrayNIOEntity(msg);
                entity.setContentType("text/plain; charset=US-ASCII");
                response.setEntity(entity);
                
            } else if (!file.canRead() || file.isDirectory()) {

                response.setStatusCode(HttpStatus.SC_FORBIDDEN);
                byte[] msg = EncodingUtils.getAsciiBytes(
                        file.getName() + ": access denied");
                ByteArrayNIOEntity entity = new ByteArrayNIOEntity(msg);
                entity.setContentType("text/plain; charset=US-ASCII");
                response.setEntity(entity);

            } else {

                FileNIOEntity entity = new FileNIOEntity(file, "text/html");
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
            ByteArrayNIOEntity entity = new ByteArrayNIOEntity(msg);
            entity.setContentType("text/plain; charset=US-ASCII");
            response.setEntity(entity);
        }
        
    }

}
