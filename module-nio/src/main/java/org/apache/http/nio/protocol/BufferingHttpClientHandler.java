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
        
        ClientConnState connState = new ClientConnState(
                new SimpleInputBuffer(2048),
                new SimpleOutputBuffer(2048)); 

        context.setAttribute(CONN_STATE, connState);

        if (this.eventListener != null) {
            InetAddress address = null;
            if (conn instanceof HttpInetConnection) {
                address = ((HttpInetConnection) conn).getRemoteAddress();
            }
            this.eventListener.connectionOpen(address);
        }
        
        requestReady(conn);        
    }

    public void closed(final NHttpClientConnection conn) {
        if (this.eventListener != null) {
            InetAddress address = null;
            if (conn instanceof HttpInetConnection) {
                address = ((HttpInetConnection) conn).getRemoteAddress();
            }
            this.eventListener.connectionClosed(address);
        }
    }

    public void exception(final NHttpClientConnection conn, final HttpException ex) {
        shutdownConnection(conn);
        if (this.eventListener != null) {
            this.eventListener.fatalProtocolException(ex);
        }
    }

    public void exception(final NHttpClientConnection conn, final IOException ex) {
        shutdownConnection(conn);
        if (this.eventListener != null) {
            this.eventListener.fatalIOException(ex);
        }
    }
    
    public void requestReady(final NHttpClientConnection conn) {
        HttpContext context = conn.getContext();

        ClientConnState connState = (ClientConnState) context.getAttribute(CONN_STATE);
        
        try {
            
            submitRequest(conn, connState);                
            
        } catch (IOException ex) {
            shutdownConnection(conn);
            if (this.eventListener != null) {
                this.eventListener.fatalIOException(ex);
            }
        } catch (HttpException ex) {
            shutdownConnection(conn);
            if (this.eventListener != null) {
                this.eventListener.fatalProtocolException(ex);
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
                this.eventListener.fatalIOException(ex);
            }
        } catch (HttpException ex) {
            shutdownConnection(conn);
            if (this.eventListener != null) {
                this.eventListener.fatalProtocolException(ex);
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
                this.eventListener.fatalIOException(ex);
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
            }
            
            if (!canResponseHaveBody(request, response)) {
                processResponse(conn, connState);
            }
            
        } catch (IOException ex) {
            shutdownConnection(conn);
            if (this.eventListener != null) {
                this.eventListener.fatalIOException(ex);
            }
        } catch (HttpException ex) {
            shutdownConnection(conn);
            if (this.eventListener != null) {
                this.eventListener.fatalProtocolException(ex);
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
                this.eventListener.fatalIOException(ex);
            }
        }
        
        shutdownConnection(conn);
        if (this.eventListener != null) {
            InetAddress address = null;
            if (conn instanceof HttpInetConnection) {
                address = ((HttpInetConnection) conn).getRemoteAddress();
            }
            this.eventListener.connectionTimeout(address);
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
        
        // Create a wrapper entity instead of the original one
        if (response.getEntity() != null) {
            response.setEntity(new BufferedContent(
                    response.getEntity(), 
                    connState.getInbuffer()));
        }
        
        context.setAttribute(HttpExecutionContext.HTTP_RESPONSE, response);
        
        this.httpProcessor.process(response, context);
        
        this.execHandler.handleResponse(response, context);
        
        if (!this.connStrategy.keepAlive(response, context)) {
            conn.close();
            connState.shutdown();
        } else {
            // Ready for another request
            connState.resetInput();
            conn.requestOutput();
        }
    }
    
}