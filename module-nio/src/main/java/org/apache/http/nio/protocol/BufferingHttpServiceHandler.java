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

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.ProtocolException;
import org.apache.http.UnsupportedHttpVersionException;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.NHttpServiceHandler;
import org.apache.http.nio.util.ContentInputBuffer;
import org.apache.http.nio.util.ContentOutputBuffer;
import org.apache.http.nio.util.SimpleInputBuffer;
import org.apache.http.nio.util.SimpleOutputBuffer;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExecutionContext;
import org.apache.http.protocol.HttpExpectationVerifier;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerResolver;
import org.apache.http.util.EncodingUtils;

/**
 * HTTP service handler implementation that buffers the content of HTTP messages 
 * entirely in memory and processes HTTP requests on the main I/O thread.
 * 
 * <p>This service handler should be used only when dealing with HTTP messages 
 * that are known to be limited in length</p>
 * 
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 */
public class BufferingHttpServiceHandler implements NHttpServiceHandler {

    private static final String CONN_STATE = "http.nio.conn-state";
    
    private HttpParams params;
    private HttpProcessor httpProcessor;
    private HttpResponseFactory responseFactory;
    private ConnectionReuseStrategy connStrategy;
    private HttpRequestHandlerResolver handlerResolver;
    private HttpExpectationVerifier expectationVerifier;
    private EventListener eventListener;
    
    public BufferingHttpServiceHandler(
            final HttpProcessor httpProcessor, 
            final HttpResponseFactory responseFactory,
            final ConnectionReuseStrategy connStrategy,
            final HttpParams params) {
        super();
        if (httpProcessor == null) {
            throw new IllegalArgumentException("HTTP processor may not be null.");
        }
        if (connStrategy == null) {
            throw new IllegalArgumentException("Connection reuse strategy may not be null");
        }
        if (responseFactory == null) {
            throw new IllegalArgumentException("Response factory may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.httpProcessor = httpProcessor;
        this.connStrategy = connStrategy;
        this.responseFactory = responseFactory;
        this.params = params;
    }

    public void setEventListener(final EventListener eventListener) {
        this.eventListener = eventListener;
    }

    public void setHandlerResolver(final HttpRequestHandlerResolver handlerResolver) {
        this.handlerResolver = handlerResolver;
    }

    public void setExpectationVerifier(final HttpExpectationVerifier expectationVerifier) {
        this.expectationVerifier = expectationVerifier;
    }

    public HttpParams getParams() {
        return this.params;
    }
    
    public void connected(final NHttpServerConnection conn) {
        HttpContext context = conn.getContext();

        ServerConnState connState = new ServerConnState(); 
        context.setAttribute(CONN_STATE, connState);

        if (this.eventListener != null) {
            this.eventListener.connectionOpen(conn);
        }
    }

    public void requestReceived(final NHttpServerConnection conn) {
        HttpContext context = conn.getContext();
        HttpRequest request = conn.getHttpRequest();

        ServerConnState connState = (ServerConnState) context.getAttribute(CONN_STATE);

        // Update connection state
        connState.resetInput();
        connState.setRequest(request);
        connState.setInputState(ServerConnState.REQUEST_RECEIVED);
        
        HttpVersion ver = request.getRequestLine().getHttpVersion();
        if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
            // Downgrade protocol version if greater than HTTP/1.1 
            ver = HttpVersion.HTTP_1_1;
        }

        HttpResponse response;
        
        try {

            if (request instanceof HttpEntityEnclosingRequest) {
                if (((HttpEntityEnclosingRequest) request).expectContinue()) {
                    response = this.responseFactory.newHttpResponse(
                            ver, HttpStatus.SC_CONTINUE, context);
                    response.getParams().setDefaults(this.params);
                    
                    if (this.expectationVerifier != null) {
                        try {
                            this.expectationVerifier.verify(request, response, context);
                        } catch (HttpException ex) {
                            response = this.responseFactory.newHttpResponse(
                                    HttpVersion.HTTP_1_0, 
                                    HttpStatus.SC_INTERNAL_SERVER_ERROR, 
                                    context);
                            response.getParams().setDefaults(this.params);
                            handleException(ex, response);
                        }
                    }
                    
                    if (response.getStatusLine().getStatusCode() < 200) {
                        // Send 1xx response indicating the server expections
                        // have been met
                        conn.submitResponse(response);
                    } else {
                        // The request does not meet the server expections
                        conn.resetInput();
                        connState.resetInput();
                        sendResponse(conn, response);
                    }
                }
                // Request content is expected. 
                // Wait until the request content is fully received
            } else {
                // No request content is expected. 
                // Process request right away
                conn.suspendInput();
                processRequest(conn, request);
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

    public void closed(final NHttpServerConnection conn) {
        if (this.eventListener != null) {
            this.eventListener.connectionClosed(conn);
        }
    }

    public void exception(final NHttpServerConnection conn, final HttpException httpex) {
        HttpContext context = conn.getContext();
        try {
            HttpResponse response = this.responseFactory.newHttpResponse(
                    HttpVersion.HTTP_1_0, HttpStatus.SC_INTERNAL_SERVER_ERROR, context);
            response.getParams().setDefaults(this.params);
            handleException(httpex, response);
            sendResponse(conn, response);
            
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

    public void exception(final NHttpServerConnection conn, final IOException ex) {
        shutdownConnection(conn);
        if (this.eventListener != null) {
            this.eventListener.fatalIOException(ex, conn);
        }
    }

    public void inputReady(final NHttpServerConnection conn, final ContentDecoder decoder) {
        HttpContext context = conn.getContext();
        HttpRequest request = conn.getHttpRequest();

        ServerConnState connState = (ServerConnState) context.getAttribute(CONN_STATE);
        ContentInputBuffer buffer = connState.getInbuffer();

        // Update connection state
        connState.setInputState(ServerConnState.REQUEST_BODY_STREAM);
        
        try {
            buffer.consumeContent(decoder);
            if (decoder.isCompleted()) {
                // Request entity has been fully received
                connState.setInputState(ServerConnState.REQUEST_BODY_DONE);

                // Create a wrapper entity instead of the original one
                HttpEntityEnclosingRequest entityReq = (HttpEntityEnclosingRequest) request;
                if (entityReq.getEntity() != null) {
                    entityReq.setEntity(new BufferedContent(
                            entityReq.getEntity(), 
                            connState.getInbuffer()));
                }
                conn.suspendInput();
                processRequest(conn, request);
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

    public void responseReady(final NHttpServerConnection conn) {
    }

    public void outputReady(final NHttpServerConnection conn, final ContentEncoder encoder) {

        HttpContext context = conn.getContext();
        HttpResponse response = conn.getHttpResponse();

        ServerConnState connState = (ServerConnState) context.getAttribute(CONN_STATE);
        ContentOutputBuffer buffer = connState.getOutbuffer();

        // Update connection state
        connState.setOutputState(ServerConnState.RESPONSE_BODY_STREAM);
        
        try {
            
            buffer.produceContent(encoder);
            if (encoder.isCompleted()) {
                connState.setOutputState(ServerConnState.RESPONSE_BODY_DONE);
                connState.resetOutput();
                if (!this.connStrategy.keepAlive(response, context)) {
                    conn.close();
                } else {
                    conn.requestInput();
                }
            }
            
        } catch (IOException ex) {
            shutdownConnection(conn);
            if (this.eventListener != null) {
                this.eventListener.fatalIOException(ex, conn);
            }
        }
    }

    public void timeout(final NHttpServerConnection conn) {
        shutdownConnection(conn);
        if (this.eventListener != null) {
            this.eventListener.connectionTimeout(conn);
        }
    }

    private void shutdownConnection(final NHttpConnection conn) {
        try {
            conn.shutdown();
        } catch (IOException ignore) {
        }
    }
    
    private void handleException(final HttpException ex, final HttpResponse response) {
        int code = HttpStatus.SC_INTERNAL_SERVER_ERROR;
        if (ex instanceof MethodNotSupportedException) {
            code = HttpStatus.SC_NOT_IMPLEMENTED;
        } else if (ex instanceof UnsupportedHttpVersionException) {
            code = HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED;
        } else if (ex instanceof ProtocolException) {
            code = HttpStatus.SC_BAD_REQUEST;
        }
        response.setStatusCode(code);
        
        byte[] msg = EncodingUtils.getAsciiBytes(ex.getMessage());
        ByteArrayEntity entity = new ByteArrayEntity(msg);
        entity.setContentType("text/plain; charset=US-ASCII");
        response.setEntity(entity);
    }
    
    private void processRequest(
            final NHttpServerConnection conn,
            final HttpRequest request) throws IOException, HttpException {
        
        HttpContext context = conn.getContext();
        HttpVersion ver = request.getRequestLine().getHttpVersion();

        if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
            // Downgrade protocol version if greater than HTTP/1.1 
            ver = HttpVersion.HTTP_1_1;
        }

        HttpResponse response = this.responseFactory.newHttpResponse(ver, HttpStatus.SC_OK, conn.getContext());
        response.getParams().setDefaults(this.params);
        
        context.setAttribute(HttpExecutionContext.HTTP_REQUEST, request);
        context.setAttribute(HttpExecutionContext.HTTP_CONNECTION, conn);
        context.setAttribute(HttpExecutionContext.HTTP_RESPONSE, response);
        
        try {

            this.httpProcessor.process(request, context);

            HttpRequestHandler handler = null;
            if (this.handlerResolver != null) {
                String requestURI = request.getRequestLine().getUri();
                handler = this.handlerResolver.lookup(requestURI);
            }
            if (handler != null) {
                handler.handle(request, response, context);
            } else {
                response.setStatusCode(HttpStatus.SC_NOT_IMPLEMENTED);
            }
            
        } catch (HttpException ex) {
            response = this.responseFactory.newHttpResponse(HttpVersion.HTTP_1_0, 
                    HttpStatus.SC_INTERNAL_SERVER_ERROR, context);
            response.getParams().setDefaults(this.params);
            handleException(ex, response);
        }

        sendResponse(conn, response);
    }

    private void sendResponse(
            final NHttpServerConnection conn,
            final HttpResponse response) throws IOException, HttpException {

        HttpContext context = conn.getContext();

        ServerConnState connState = (ServerConnState) context.getAttribute(CONN_STATE);
        ContentOutputBuffer buffer = connState.getOutbuffer();

        this.httpProcessor.process(response, context);

        if (!canResponseHaveBody(connState.getRequest(), response)) {
            response.setEntity(null);
        }
        
        conn.submitResponse(response);

        // Update connection state
        connState.setOutputState(ServerConnState.RESPONSE_SENT);

        HttpEntity entity = response.getEntity();
        if (entity != null) {
            OutputStream outstream = new ContentOutputStream(buffer);
            entity.writeTo(outstream);
            outstream.flush();
            outstream.close();
        } else {
            connState.resetOutput();
            conn.requestInput();
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

    static class ServerConnState {
        
        public static final int READY                      = 0;
        public static final int REQUEST_RECEIVED           = 1;
        public static final int REQUEST_BODY_STREAM        = 2;
        public static final int REQUEST_BODY_DONE          = 4;
        public static final int RESPONSE_SENT              = 8;
        public static final int RESPONSE_BODY_STREAM       = 16;
        public static final int RESPONSE_BODY_DONE         = 32;
        
        private SimpleInputBuffer inbuffer; 
        private ContentOutputBuffer outbuffer;

        private int inputState;
        private int outputState;
        
        private HttpRequest request;
        
        public ServerConnState() {
            super();
            this.inputState = READY;
            this.outputState = READY;
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

        public void resetInput() {
            this.inbuffer = null;
            this.request = null;
            this.inputState = READY;
        }
        
        public void resetOutput() {
            this.outbuffer = null;
            this.outputState = READY;
        }
        
    }
    
}
