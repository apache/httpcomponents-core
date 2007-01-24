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
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpInetConnection;
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
import org.apache.http.nio.impl.InputBuffer;
import org.apache.http.nio.impl.OutputBuffer;
import org.apache.http.nio.impl.entity.BufferedContent;
import org.apache.http.nio.impl.entity.ContentOutputStream;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExecutionContext;
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

    private static final String IN_BUF = "http.nio.in-buffer";
    private static final String OUT_BUF = "http.nio.out-buffer";
    
    private HttpParams params;
    private HttpProcessor httpProcessor;
    private HttpResponseFactory responseFactory;
    private ConnectionReuseStrategy connStrategy;
    private HttpRequestHandlerResolver handlerResolver;
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

    public HttpParams getParams() {
        return this.params;
    }
    
    public void connected(final NHttpServerConnection conn) {
        HttpContext context = conn.getContext();

        InputBuffer inbuffer = new InputBuffer(2048); 
        OutputBuffer outbuffer = new OutputBuffer(2048);
        
        context.setAttribute(IN_BUF, inbuffer);
        context.setAttribute(OUT_BUF, outbuffer);

        if (this.eventListener != null) {
            InetAddress address = null;
            if (conn instanceof HttpInetConnection) {
                address = ((HttpInetConnection) conn).getRemoteAddress();
            }
            this.eventListener.connectionOpen(address);
        }
    }

    public void requestReceived(final NHttpServerConnection conn) {
        HttpContext context = conn.getContext();
        HttpRequest request = conn.getHttpRequest();
        HttpVersion ver = request.getRequestLine().getHttpVersion();

        InputBuffer inbuffer = (InputBuffer) context.getAttribute(IN_BUF);
        OutputBuffer outbuffer = (OutputBuffer) context.getAttribute(OUT_BUF);

        // Clean the buffers just in case
        inbuffer.clear();
        outbuffer.clear();
        
        try {

            if (request instanceof HttpEntityEnclosingRequest) {
                if (((HttpEntityEnclosingRequest) request).expectContinue()) {
                    HttpResponse ack = this.responseFactory.newHttpResponse(ver, 100);
                    conn.submitResponse(ack);
                }
                // Request content is expected. 
                // Wait until the request content is fully received
            } else {
                // No request content is expected. 
                // Process request right away
                processRequest(conn, request);
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

    public void closed(final NHttpServerConnection conn) {
        if (this.eventListener != null) {
            InetAddress address = null;
            if (conn instanceof HttpInetConnection) {
                address = ((HttpInetConnection) conn).getRemoteAddress();
            }
            this.eventListener.connectionClosed(address);
        }
    }

    public void exception(final NHttpServerConnection conn, final HttpException httpex) {
        try {
            
            HttpResponse response = handleException(conn, httpex);
            commitResponse(conn, response);
            
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

    public void exception(final NHttpServerConnection conn, final IOException ex) {
        shutdownConnection(conn);
        if (this.eventListener != null) {
            this.eventListener.fatalIOException(ex);
        }
    }

    public void inputReady(final NHttpServerConnection conn, final ContentDecoder decoder) {
        HttpContext context = conn.getContext();
        HttpRequest request = conn.getHttpRequest();

        InputBuffer inbuffer = (InputBuffer) context.getAttribute(IN_BUF);

        try {
            inbuffer.consumeContent(decoder);
            if (decoder.isCompleted()) {
                // Request entity has been fully received

                // Create a wrapper entity instead of the original one
                BufferedContent.wrapEntity((HttpEntityEnclosingRequest) request, inbuffer);
                processRequest(conn, request);
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

    public void outputReady(final NHttpServerConnection conn, final ContentEncoder encoder) {

        HttpContext context = conn.getContext();
        HttpResponse response = conn.getHttpResponse();
        OutputBuffer outbuffer = (OutputBuffer) context.getAttribute(OUT_BUF);

        try {
            
            outbuffer.produceContent(encoder);
            if (encoder.isCompleted()) {
                if (!this.connStrategy.keepAlive(response, context)) {
                    conn.close();
                }
            }
            
        } catch (IOException ex) {
            shutdownConnection(conn);
            if (this.eventListener != null) {
                this.eventListener.fatalIOException(ex);
            }
        }
    }

    public void timeout(final NHttpServerConnection conn) {
        shutdownConnection(conn);
        if (this.eventListener != null) {
            InetAddress address = null;
            if (conn instanceof HttpInetConnection) {
                address = ((HttpInetConnection) conn).getRemoteAddress();
            }
            this.eventListener.connectionTimeout(address);
        }
    }

    private void shutdownConnection(final NHttpConnection conn) {
        try {
            conn.shutdown();
        } catch (IOException ignore) {
        }
    }
    
    private HttpResponse handleException(
            final NHttpServerConnection conn,
            final HttpException ex) {

        HttpRequest request = conn.getHttpRequest();
        HttpVersion ver = request.getRequestLine().getHttpVersion();
        HttpResponse response =  this.responseFactory.newHttpResponse(
                ver, HttpStatus.SC_BAD_REQUEST);

        int code = HttpStatus.SC_INTERNAL_SERVER_ERROR;
        if (ex instanceof MethodNotSupportedException) {
            code = HttpStatus.SC_NOT_IMPLEMENTED;
        } else if (ex instanceof UnsupportedHttpVersionException) {
            code = HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED;
        } else if (ex instanceof ProtocolException) {
            code = HttpStatus.SC_BAD_REQUEST;
        }
        
        response.setStatusLine(HttpVersion.HTTP_1_0, code);
        byte[] msg = EncodingUtils.getAsciiBytes(ex.getMessage());
        ByteArrayEntity entity = new ByteArrayEntity(msg);
        entity.setContentType("text/plain; charset=US-ASCII");
        response.setEntity(entity);
        return response;
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

        HttpResponse response = this.responseFactory.newHttpResponse(ver, HttpStatus.SC_OK);
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
            response = handleException(conn, ex);
        }

        commitResponse(conn, response);
    }

    private void commitResponse(
            final NHttpServerConnection conn,
            final HttpResponse response) throws IOException, HttpException {

        HttpContext context = conn.getContext();
        OutputBuffer outbuffer = (OutputBuffer) context.getAttribute(OUT_BUF);

        this.httpProcessor.process(response, context);
        conn.submitResponse(response);

        if (response.getEntity() != null) {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                OutputStream outstream = new ContentOutputStream(outbuffer);
                entity.writeTo(outstream);
                outstream.flush();
            }
        }
    }

}