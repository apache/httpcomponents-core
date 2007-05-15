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
import java.io.InterruptedIOException;
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
import org.apache.http.nio.IOControl;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.NHttpServiceHandler;
import org.apache.http.nio.params.HttpNIOParams;
import org.apache.http.nio.util.ContentInputBuffer;
import org.apache.http.nio.util.ContentOutputBuffer;
import org.apache.http.nio.util.SharedInputBuffer;
import org.apache.http.nio.util.SharedOutputBuffer;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExecutionContext;
import org.apache.http.protocol.HttpExpectationVerifier;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerResolver;
import org.apache.http.util.EncodingUtils;
import org.apache.http.util.concurrent.Executor;

/**
 * HTTP service handler implementation that allocates content buffers of limited 
 * size upon initialization and is capable of controlling the frequency of I/O 
 * events in order to guarantee those content buffers do not ever get overflown. 
 * This helps ensure near constant memory footprint of HTTP connections and to 
 * avoid the 'out of memory' condition while streaming out response content.
 * 
 * <p>The service handler will delegate the task of processing requests and 
 * generating response content to an {@link Executor}, which is expected to
 * perform those tasks using dedicated worker threads in order to avoid 
 * blocking the I/O thread.</p>
 * 
 * @see HttpNIOParams#CONTENT_BUFFER_SIZE
 * 
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 */
public class ThrottlingHttpServiceHandler implements NHttpServiceHandler {

    private static final String CONN_STATE = "http.nio.conn-state";
    
    private HttpParams params;
    private HttpProcessor httpProcessor;
    private HttpResponseFactory responseFactory;
    private ConnectionReuseStrategy connStrategy;
    private HttpRequestHandlerResolver handlerResolver;
    private HttpExpectationVerifier expectationVerifier;
    private EventListener eventListener;
    private Executor executor;
    
    public ThrottlingHttpServiceHandler(
            final HttpProcessor httpProcessor, 
            final HttpResponseFactory responseFactory,
            final ConnectionReuseStrategy connStrategy,
            final Executor executor,
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
        if (executor == null) {
            throw new IllegalArgumentException("Executor may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.httpProcessor = httpProcessor;
        this.connStrategy = connStrategy;
        this.responseFactory = responseFactory;
        this.executor = executor;
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

        int bufsize = this.params.getIntParameter(
                HttpNIOParams.CONTENT_BUFFER_SIZE, 20480);
        ServerConnState connState = new ServerConnState(bufsize, conn); 
        context.setAttribute(CONN_STATE, connState);

        if (this.eventListener != null) {
            this.eventListener.connectionOpen(conn);
        }
    }

    public void closed(final NHttpServerConnection conn) {
        HttpContext context = conn.getContext();
        
        ServerConnState connState = (ServerConnState) context.getAttribute(CONN_STATE);

        connState.shutdown();
        
        if (this.eventListener != null) {
            this.eventListener.connectionClosed(conn);
        }
    }
    
    public void exception(final NHttpServerConnection conn, final HttpException httpex) {

        final HttpContext context = conn.getContext();
        final ServerConnState connState = (ServerConnState) context.getAttribute(CONN_STATE);
        
        this.executor.execute(new Runnable() {
            
            public void run() {
                try {

                    HttpContext context = new HttpExecutionContext(conn.getContext());
                    context.setAttribute(HttpExecutionContext.HTTP_CONNECTION, conn);
                    handleException(connState, httpex, context);
                    commitResponse(connState, conn);
                    
                } catch (IOException ex) {
                    shutdownConnection(conn);
                    if (eventListener != null) {
                        eventListener.fatalIOException(ex, conn);
                    }
                } catch (HttpException ex) {
                    shutdownConnection(conn);
                    if (eventListener != null) {
                        eventListener.fatalProtocolException(ex, conn);
                    }
                }
            }
            
        });        
    }

    public void exception(final NHttpServerConnection conn, final IOException ex) {
        shutdownConnection(conn);
        
        if (this.eventListener != null) {
            this.eventListener.fatalIOException(ex, conn);
        }
    }

    public void timeout(final NHttpServerConnection conn) {
        shutdownConnection(conn);

        if (this.eventListener != null) {
            this.eventListener.connectionTimeout(conn);
        }
    }

    public void requestReceived(final NHttpServerConnection conn) {
        HttpContext context = conn.getContext();
        HttpRequest request = conn.getHttpRequest();

        final ServerConnState connState = (ServerConnState) context.getAttribute(CONN_STATE);

        connState.resetInput();
        connState.setRequest(request);
        connState.setInputState(ServerConnState.REQUEST_RECEIVED);

        this.executor.execute(new Runnable() {
            
            public void run() {
                try {
                    HttpContext context = new HttpExecutionContext(conn.getContext());
                    context.setAttribute(HttpExecutionContext.HTTP_CONNECTION, conn);
                    handleRequest(connState, context);
                    commitResponse(connState, conn);
                    
                } catch (IOException ex) {
                    shutdownConnection(conn);
                    if (eventListener != null) {
                        eventListener.fatalIOException(ex, conn);
                    }
                } catch (HttpException ex) {
                    shutdownConnection(conn);
                    if (eventListener != null) {
                        eventListener.fatalProtocolException(ex, conn);
                    }
                }
            }
            
        });

    }

    public void inputReady(final NHttpServerConnection conn, final ContentDecoder decoder) {
        HttpContext context = conn.getContext();

        ServerConnState connState = (ServerConnState) context.getAttribute(CONN_STATE);
        ContentInputBuffer buffer = connState.getInbuffer();

        // Update connection state
        connState.setInputState(ServerConnState.REQUEST_BODY_STREAM);
        
        try {
            
            buffer.consumeContent(decoder);
            if (decoder.isCompleted()) {
                connState.setInputState(ServerConnState.REQUEST_BODY_DONE);
            }
            
        } catch (IOException ex) {
            shutdownConnection(conn);
            if (this.eventListener != null) {
                this.eventListener.fatalIOException(ex, conn);
            }
        }
    }
    
    public void responseReady(final NHttpServerConnection conn) {
        HttpContext context = conn.getContext();

        ServerConnState connState = (ServerConnState) context.getAttribute(CONN_STATE);
        HttpResponse response = connState.getResponse();
        if (connState.getOutputState() != ServerConnState.RESPONSE_SENT 
                && response != null 
                && !conn.isResponseSubmitted()) {
            try {
                conn.submitResponse(response);

                // Notify the worker thread of the connection state
                // change
                synchronized (connState) {
                    connState.setOutputState(ServerConnState.RESPONSE_SENT);
                    connState.notifyAll();
                }
                
            } catch (IOException ex) {
                shutdownConnection(conn);
                if (eventListener != null) {
                    eventListener.fatalIOException(ex, conn);
                }
            } catch (HttpException ex) {
                shutdownConnection(conn);
                if (eventListener != null) {
                    eventListener.fatalProtocolException(ex, conn);
                }
            }
        }
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

                // Notify the worker thread of the connection state
                // change
                synchronized (connState) {
                    connState.setOutputState(ServerConnState.RESPONSE_BODY_DONE);
                    connState.notifyAll();
                }
                
                if (!this.connStrategy.keepAlive(response, context)) {
                    conn.close();
                }
            }
            
        } catch (IOException ex) {
            shutdownConnection(conn);
            if (this.eventListener != null) {
                this.eventListener.fatalIOException(ex, conn);
            }
        }
    }
 
    private void shutdownConnection(final NHttpConnection conn) {
        HttpContext context = conn.getContext();

        ServerConnState connState = (ServerConnState) context.getAttribute(CONN_STATE);
        
        try {
            conn.shutdown();
        } catch (IOException ignore) {
        }
        if (connState != null) {
            connState.shutdown();
        }
    }
    
    private void waitForOutput(
            final ServerConnState connState, 
            int expectedState) throws InterruptedIOException {
        synchronized (connState) {
            try {
                for (;;) {
                    int currentState = connState.getOutputState();
                    if (currentState == expectedState) {
                        break;
                    }
                    if (currentState == ServerConnState.SHUTDOWN) {
                        throw new InterruptedIOException("Service interrupted");
                    }
                    connState.wait();
                }
            } catch (InterruptedException ex) {
                connState.shutdown();
            }
        }
    }
    
    private void handleException(
            final ServerConnState connState,
            final HttpException ex,
            final HttpContext context) throws HttpException, IOException {

        HttpRequest request = connState.getRequest();
        context.setAttribute(HttpExecutionContext.HTTP_REQUEST, request);
        
        HttpVersion ver;
        if (request != null) {
            ver = request.getRequestLine().getHttpVersion(); 
        } else {
            ver = HttpVersion.HTTP_1_0;
        }
        int code = HttpStatus.SC_INTERNAL_SERVER_ERROR;
        if (ex instanceof MethodNotSupportedException) {
            code = HttpStatus.SC_NOT_IMPLEMENTED;
        } else if (ex instanceof UnsupportedHttpVersionException) {
            code = HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED;
        } else if (ex instanceof ProtocolException) {
            code = HttpStatus.SC_BAD_REQUEST;
        }
        
        HttpResponse response =  this.responseFactory.newHttpResponse(
                ver, 
                code, 
                context);

        byte[] msg = EncodingUtils.getAsciiBytes(ex.getMessage());
        ByteArrayEntity entity = new ByteArrayEntity(msg);
        entity.setContentType("text/plain; charset=US-ASCII");
        response.setEntity(entity);

        context.setAttribute(HttpExecutionContext.HTTP_RESPONSE, response);

        this.httpProcessor.process(response, context);
        
        connState.setResponse(response);
    }
    
    private void handleRequest(
            final ServerConnState connState,
            final HttpContext context) throws HttpException, IOException {

        HttpRequest request = connState.getRequest();
        context.setAttribute(HttpExecutionContext.HTTP_REQUEST, request);

        HttpVersion ver = request.getRequestLine().getHttpVersion();

        if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
            // Downgrade protocol version if greater than HTTP/1.1 
            ver = HttpVersion.HTTP_1_1;
        }

        HttpResponse response;

        if (request instanceof HttpEntityEnclosingRequest) {
            HttpEntityEnclosingRequest entityReq = (HttpEntityEnclosingRequest) request;
            
            if (entityReq.expectContinue()) {
                response = this.responseFactory.newHttpResponse(
                        ver, 
                        HttpStatus.SC_CONTINUE, 
                        context);
                response.getParams().setDefaults(this.params);
                if (this.expectationVerifier != null) {
                    try {
                        this.expectationVerifier.verify(request, response, context);
                    } catch (HttpException ex) {
                        handleException(connState, ex ,context);
                        return;
                    }
                }
            
                if (response.getStatusLine().getStatusCode() < 200) {
                    // Send 1xx response indicating the server expections
                    // have been met
                    waitForOutput(connState, ServerConnState.READY);
                    connState.setResponse(response);
                    synchronized (connState) {
                        waitForOutput(connState, ServerConnState.RESPONSE_SENT);
                        connState.resetOutput();
                    }
                } else {
                    // The request does not meet the server expections
                    context.setAttribute(HttpExecutionContext.HTTP_RESPONSE, response);
                    this.httpProcessor.process(response, context);
                    connState.setResponse(response);
                    return;
                }
            }

            // Create a wrapper entity instead of the original one
            if (entityReq.getEntity() != null) {
                entityReq.setEntity(new BufferedContent(
                        entityReq.getEntity(), 
                        connState.getInbuffer()));
            }
        }

        response = this.responseFactory.newHttpResponse(
                ver, 
                HttpStatus.SC_OK, 
                context);
        response.getParams().setDefaults(this.params);

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
            handleException(connState, ex ,context);
            return;
        }

        this.httpProcessor.process(response, context);
        
        connState.setResponse(response);
    }
    
    private void commitResponse(
            final ServerConnState connState,
            final IOControl ioControl) throws IOException, HttpException {

        waitForOutput(connState, ServerConnState.READY);
        
        // Response is ready to be committed
        HttpResponse response = connState.getResponse();

        int terminalState;
        if (response.getEntity() != null) {
            ContentOutputBuffer buffer = connState.getOutbuffer();
            OutputStream outstream = new ContentOutputStream(buffer);

            HttpEntity entity = response.getEntity();
            entity.writeTo(outstream);
            outstream.flush();
            outstream.close();
            terminalState = ServerConnState.RESPONSE_BODY_DONE;
        } else {
            ioControl.requestOutput();
            terminalState = ServerConnState.RESPONSE_SENT;
        }
        synchronized (connState) {
            waitForOutput(connState, terminalState);
            connState.resetOutput();
        }
    }

    static class ServerConnState {
        
        public static final int SHUTDOWN                   = -1;
        public static final int READY                      = 0;
        public static final int REQUEST_RECEIVED           = 1;
        public static final int REQUEST_BODY_STREAM        = 2;
        public static final int REQUEST_BODY_DONE          = 4;
        public static final int RESPONSE_SENT              = 8;
        public static final int RESPONSE_BODY_STREAM       = 16;
        public static final int RESPONSE_BODY_DONE         = 32;
        
        private final SharedInputBuffer inbuffer; 
        private final SharedOutputBuffer outbuffer;

        private volatile int inputState;
        private volatile int outputState;
        
        private volatile HttpRequest request;
        private volatile HttpResponse response;
        
        public ServerConnState(int bufsize, final IOControl ioControl) {
            super();
            this.inbuffer = new SharedInputBuffer(bufsize, ioControl);
            this.outbuffer = new SharedOutputBuffer(bufsize, ioControl);
            this.inputState = READY;
            this.outputState = READY;
        }

        public ContentInputBuffer getInbuffer() {
            return this.inbuffer;
        }

        public ContentOutputBuffer getOutbuffer() {
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

        public void shutdown() {
            this.inbuffer.shutdown();
            this.outbuffer.shutdown();
            this.inputState = SHUTDOWN;
            this.outputState = SHUTDOWN;
        }

        public void resetInput() {
            this.inbuffer.reset();
            this.request = null;
            this.inputState = READY;
        }
        
        public void resetOutput() {
            this.outbuffer.reset();
            this.response = null;
            this.outputState = READY;
        }
        
    }    
    
}
