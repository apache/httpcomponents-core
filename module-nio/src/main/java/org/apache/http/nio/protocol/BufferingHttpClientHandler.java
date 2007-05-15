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

package org.apache.http.nio.protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpConnection;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpInetConnection;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.entity.ContentBufferEntity;
import org.apache.http.nio.entity.ContentOutputStream;
import org.apache.http.nio.util.ContentInputBuffer;
import org.apache.http.nio.util.ContentOutputBuffer;
import org.apache.http.nio.util.SimpleInputBuffer;
import org.apache.http.nio.util.SimpleOutputBuffer;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExecutionContext;
import org.apache.http.protocol.HttpProcessor;

/**
 * HTTP client handler implementation that buffers the content of HTTP messages 
 * entirely in memory and executes HTTP requests on the main I/O thread.
 * 
 * <p>This service handler should be used only when dealing with HTTP messages 
 * that are known to be limited in length</p>
 * 
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 */
public class BufferingHttpClientHandler implements NHttpClientHandler {

    private static final String CONN_STATE = "http.nio.conn-state";
    
    private HttpParams params;
    private HttpProcessor httpProcessor;
    private HttpRequestExecutionHandler execHandler;
    private ConnectionReuseStrategy connStrategy;
    private EventListener eventListener;
    
    public BufferingHttpClientHandler(
            final HttpProcessor httpProcessor, 
            final HttpRequestExecutionHandler execHandler,
            final ConnectionReuseStrategy connStrategy,
            final HttpParams params) {
        super();
        if (httpProcessor == null) {
            throw new IllegalArgumentException("HTTP processor may not be null.");
        }
        if (execHandler == null) {
            throw new IllegalArgumentException("HTTP request execution handler may not be null.");
        }
        if (connStrategy == null) {
            throw new IllegalArgumentException("Connection reuse strategy may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.httpProcessor = httpProcessor;
        this.execHandler = execHandler;
        this.connStrategy = connStrategy;
        this.params = params;
    }
    
    public void setEventListener(final EventListener eventListener) {
        this.eventListener = eventListener;
    }

    private void shutdownConnection(final HttpConnection conn) {
        try {
            conn.shutdown();
        } catch (IOException ignore) {
        }
    }
    
    public void connected(final NHttpClientConnection conn, final Object attachment) {
        HttpContext context = conn.getContext();

        // Populate the context with a default HTTP host based on the 
        // inet address of the target host
        if (conn instanceof HttpInetConnection) {
            InetAddress address = ((HttpInetConnection) conn).getRemoteAddress();
            int port = ((HttpInetConnection) conn).getRemotePort();
            if (address != null) {
                HttpHost host = new HttpHost(address.getHostName(), port);
                context.setAttribute(HttpExecutionContext.HTTP_TARGET_HOST, host);
            }
        }
        
        initialize(conn, attachment);
        
        ClientConnState connState = new ClientConnState(); 
        context.setAttribute(CONN_STATE, connState);

        if (this.eventListener != null) {
            this.eventListener.connectionOpen(conn);
        }
        
        requestReady(conn);        
    }

    public void closed(final NHttpClientConnection conn) {
        if (this.eventListener != null) {
            this.eventListener.connectionClosed(conn);
        }
    }

    public void exception(final NHttpClientConnection conn, final HttpException ex) {
        shutdownConnection(conn);
        if (this.eventListener != null) {
            this.eventListener.fatalProtocolException(ex, conn);
        }
    }

    public void exception(final NHttpClientConnection conn, final IOException ex) {
        shutdownConnection(conn);
        if (this.eventListener != null) {
            this.eventListener.fatalIOException(ex, conn);
        }
    }
    
    public void requestReady(final NHttpClientConnection conn) {
        HttpContext context = conn.getContext();

        ClientConnState connState = (ClientConnState) context.getAttribute(CONN_STATE);
        if (connState.getInputState() != ClientConnState.READY) {
            return;
        }
        
        try {
            
            submitRequest(conn, connState);                
            
        } catch (IOException ex) {
            shutdownConnection(conn);
            if (this.eventListener != null) {
                this.eventListener.fatalIOException(ex, conn);
            }
        } catch (HttpException ex) {
            shutdownConnection(conn);
            if (this.eventListener != null) {
                this.eventListener.fatalProtocolException(ex, conn);
            }
        }
    }

    public void inputReady(final NHttpClientConnection conn, final ContentDecoder decoder) {
        HttpContext context = conn.getContext();

        ClientConnState connState = (ClientConnState) context.getAttribute(CONN_STATE);
        
        connState.setInputState(ClientConnState.RESPONSE_BODY_STREAM);
        ContentInputBuffer buffer = connState.getInbuffer();

        try {

            buffer.consumeContent(decoder);
            if (decoder.isCompleted()) {

                connState.setInputState(ClientConnState.RESPONSE_BODY_DONE);
                processResponse(conn, connState);
                
            }
            
        } catch (IOException ex) {
            shutdownConnection(conn);
            if (this.eventListener != null) {
                this.eventListener.fatalIOException(ex, conn);
            }
        } catch (HttpException ex) {
            shutdownConnection(conn);
            if (this.eventListener != null) {
                this.eventListener.fatalProtocolException(ex, conn);
            }
        }
    }

    public void outputReady(final NHttpClientConnection conn, final ContentEncoder encoder) {
        HttpContext context = conn.getContext();

        ClientConnState connState = (ClientConnState) context.getAttribute(CONN_STATE);
        
        if (connState.getOutputState() == ClientConnState.EXPECT_CONTINUE) {
            conn.suspendOutput();
            return;
        }
        
        connState.setInputState(ClientConnState.REQUEST_BODY_STREAM);
        ContentOutputBuffer buffer = connState.getOutbuffer();

        try {
            
            buffer.produceContent(encoder);
            if (encoder.isCompleted()) {

                connState.setInputState(ClientConnState.REQUEST_BODY_DONE);
                
            }
            
        } catch (IOException ex) {
            shutdownConnection(conn);
            if (this.eventListener != null) {
                this.eventListener.fatalIOException(ex, conn);
            }
        }
    }

    public void responseReceived(final NHttpClientConnection conn) {
        HttpContext context = conn.getContext();
        ClientConnState connState = (ClientConnState) context.getAttribute(CONN_STATE);

        HttpResponse response = conn.getHttpResponse();
        HttpRequest request = connState.getRequest();
        
        try {
            
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode < HttpStatus.SC_OK) {
                // 1xx intermediate response
                if (statusCode == HttpStatus.SC_CONTINUE 
                        && connState.getOutputState() == ClientConnState.EXPECT_CONTINUE) {
                    continueRequest(conn, connState);
                }
                return;
            } else {
                connState.setResponse(response);
                connState.setInputState(ClientConnState.RESPONSE_RECEIVED);
                
                if (connState.getOutputState() == ClientConnState.EXPECT_CONTINUE) {
                    cancelRequest(conn, connState);
                }
            }
            if (!canResponseHaveBody(request, response)) {
                conn.resetInput();
                response.setEntity(null);
                processResponse(conn, connState);
            }
            
        } catch (IOException ex) {
            shutdownConnection(conn);
            if (this.eventListener != null) {
                this.eventListener.fatalIOException(ex, conn);
            }
        } catch (HttpException ex) {
            shutdownConnection(conn);
            if (this.eventListener != null) {
                this.eventListener.fatalProtocolException(ex, conn);
            }
        }
    }

    public void timeout(final NHttpClientConnection conn) {
        HttpContext context = conn.getContext();
        ClientConnState connState = (ClientConnState) context.getAttribute(CONN_STATE);

        try {
            
            if (connState.getOutputState() == ClientConnState.EXPECT_CONTINUE) {
                continueRequest(conn, connState);
                return;
            }
            
        } catch (IOException ex) {
            shutdownConnection(conn);
            if (this.eventListener != null) {
                this.eventListener.fatalIOException(ex, conn);
            }
        }
        
        shutdownConnection(conn);
        if (this.eventListener != null) {
            this.eventListener.connectionTimeout(conn);
        }
    }
    
    private void initialize(
            final NHttpClientConnection conn,
            final Object attachment) {
        HttpContext context = conn.getContext();

        context.setAttribute(HttpExecutionContext.HTTP_CONNECTION, conn);
        this.execHandler.initalizeContext(context, attachment);
    }
    
    private void submitRequest(
            final NHttpClientConnection conn, 
            final ClientConnState connState) throws IOException, HttpException {
        
        connState.resetOutput();
        
        HttpContext context = conn.getContext();
        HttpRequest request = this.execHandler.submitRequest(context);
        if (request == null) {
            return;
        }
        
        request.getParams().setDefaults(this.params);
        
        context.setAttribute(HttpExecutionContext.HTTP_REQUEST, request);
        this.httpProcessor.process(request, context);
        connState.setRequest(request);
        conn.submitRequest(request);
        connState.setOutputState(ClientConnState.REQUEST_SENT);
        
        if (request instanceof HttpEntityEnclosingRequest) {
            if (((HttpEntityEnclosingRequest) request).expectContinue()) {
                int timeout = conn.getSocketTimeout();
                connState.setTimeout(timeout);
                timeout = this.params.getIntParameter(
                        HttpProtocolParams.WAIT_FOR_CONTINUE, 3000);
                conn.setSocketTimeout(timeout);
                connState.setOutputState(ClientConnState.EXPECT_CONTINUE);
            } else {
                prepareRequestBody((HttpEntityEnclosingRequest) request, connState);
            }
        }
    }
    
    private void continueRequest(
            final NHttpClientConnection conn, 
            final ClientConnState connState) throws IOException {

        HttpRequest request = connState.getRequest();

        int timeout = connState.getTimeout();
        conn.setSocketTimeout(timeout);

        prepareRequestBody((HttpEntityEnclosingRequest) request, connState);
        conn.requestOutput();
        connState.setOutputState(ClientConnState.REQUEST_SENT);
    }
    
    private void cancelRequest(
            final NHttpClientConnection conn, 
            final ClientConnState connState) throws IOException {

        int timeout = connState.getTimeout();
        conn.setSocketTimeout(timeout);

        conn.resetOutput();
        connState.resetOutput();
    }
    
    private void prepareRequestBody(
            final HttpEntityEnclosingRequest request,
            final ClientConnState connState) throws IOException {
        HttpEntity entity = request.getEntity();
        if (entity != null) {
            OutputStream outstream = new ContentOutputStream(connState.getOutbuffer());
            entity.writeTo(outstream);
            outstream.flush();
            outstream.close();
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
    
    private void processResponse(
            final NHttpClientConnection conn, 
            final ClientConnState connState) throws IOException, HttpException {

        HttpContext context = conn.getContext();
        HttpResponse response = connState.getResponse();
        
        if (response.getEntity() != null) {
            response.setEntity(new ContentBufferEntity(
                    response.getEntity(), 
                    connState.getInbuffer()));
        }
        
        context.setAttribute(HttpExecutionContext.HTTP_RESPONSE, response);
        
        this.httpProcessor.process(response, context);
        
        this.execHandler.handleResponse(response, context);
        
        if (!this.connStrategy.keepAlive(response, context)) {
            conn.close();
        } else {
            // Ready for another request
            connState.resetInput();
            conn.requestOutput();
        }
    }
    
    static class ClientConnState {
        
        public static final int READY                      = 0;
        public static final int REQUEST_SENT               = 1;
        public static final int EXPECT_CONTINUE            = 2;
        public static final int REQUEST_BODY_STREAM        = 4;
        public static final int REQUEST_BODY_DONE          = 8;
        public static final int RESPONSE_RECEIVED          = 16;
        public static final int RESPONSE_BODY_STREAM       = 32;
        public static final int RESPONSE_BODY_DONE         = 64;
        
        private SimpleInputBuffer inbuffer; 
        private ContentOutputBuffer outbuffer;

        private int inputState;
        private int outputState;
        
        private HttpRequest request;
        private HttpResponse response;

        private int timeout;
        
        public ClientConnState() {
            super();
        }

        public ContentInputBuffer getInbuffer() {
            if (this.inbuffer == null) {
                this.inbuffer = new SimpleInputBuffer(2048);
            }
            return this.inbuffer;
        }

        public ContentOutputBuffer getOutbuffer() {
            if (this.outbuffer == null) {
                this.outbuffer = new SimpleOutputBuffer(2048);
            }
            return this.outbuffer;
        }
        
        public int getInputState() {
            return this.inputState;
        }

        public void setInputState(int inputState) {
            this.inputState = inputState;
        }

        public int getOutputState() {
            return this.outputState;
        }

        public void setOutputState(int outputState) {
            this.outputState = outputState;
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

        public int getTimeout() {
            return this.timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }
            
        public void resetInput() {
            this.inbuffer = null;
            this.response = null;
            this.inputState = READY;
        }
        
        public void resetOutput() {
            this.outbuffer = null;
            this.request = null;
            this.outputState = READY;
        }
    }
    
}