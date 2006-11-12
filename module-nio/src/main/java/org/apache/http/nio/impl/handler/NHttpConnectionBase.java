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

package org.apache.http.nio.impl.handler;

import java.io.IOException;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.ContentLengthStrategy;
import org.apache.http.impl.entity.LaxContentLengthStrategy;
import org.apache.http.impl.entity.StrictContentLengthStrategy;
import org.apache.http.nio.EventMask;
import org.apache.http.nio.IOSession;
import org.apache.http.nio.handler.ContentDecoder;
import org.apache.http.nio.handler.ContentEncoder;
import org.apache.http.nio.handler.NHttpConnection;
import org.apache.http.nio.impl.SessionInputBuffer;
import org.apache.http.nio.impl.SessionOutputBuffer;
import org.apache.http.nio.impl.codecs.ChunkDecoder;
import org.apache.http.nio.impl.codecs.ChunkEncoder;
import org.apache.http.nio.impl.codecs.IdentityDecoder;
import org.apache.http.nio.impl.codecs.IdentityEncoder;
import org.apache.http.nio.impl.codecs.LengthDelimitedDecoder;
import org.apache.http.nio.impl.codecs.LengthDelimitedEncoder;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.SyncHttpExecutionContext;
import org.apache.http.util.CharArrayBuffer;

public class NHttpConnectionBase implements NHttpConnection {

    protected final IOSession session;
    protected final HttpContext context;
    
    protected ContentLengthStrategy incomingContentStrategy;
    protected ContentLengthStrategy outgoingContentStrategy;
    
    protected final SessionInputBuffer inbuf;
    protected final SessionOutputBuffer outbuf;
    protected final CharArrayBuffer lineBuffer;
    
    protected ContentDecoder contentDecoder;
    protected ContentEncoder contentEncoder;
    protected HttpRequest request;
    protected HttpResponse response;
    
    protected volatile boolean closed;
    
    public NHttpConnectionBase(final IOSession session, final HttpParams params) {
        super();
        if (session == null) {
            throw new IllegalArgumentException("I/O session may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP params may not be null");
        }
        this.session = session;
        this.context = new SyncHttpExecutionContext(null);
        
        int buffersize = HttpConnectionParams.getSocketBufferSize(params);
        int linebuffersize = buffersize;
        if (linebuffersize > 512) {
            linebuffersize = 512;
        }
        
        this.inbuf = new SessionInputBuffer(buffersize, linebuffersize); 
        this.inbuf.reset(params);
        this.outbuf = new SessionOutputBuffer(buffersize, linebuffersize); 
        this.outbuf.reset(params);
        this.lineBuffer = new CharArrayBuffer(64); 
        
        this.incomingContentStrategy = new LaxContentLengthStrategy();
        this.outgoingContentStrategy = new StrictContentLengthStrategy();
        
        this.closed = false;
        this.session.setEvent(EventMask.READ);
    }

    public HttpContext getContext() {
        return this.context;
    }

    public HttpRequest getHttpRequest() {
        return this.request;
    }

    public HttpResponse getHttpResponse() {
        return this.response;
    }

    public void requestInput() {
        this.session.setEvent(EventMask.READ);
    }

    public void requestOutput() {
        this.session.setEvent(EventMask.WRITE);
    }

    public void suspendInput() {
        this.session.clearEvent(EventMask.READ);
    }

    public void suspendOutput() {
        this.session.clearEvent(EventMask.WRITE);
    }

    protected HttpEntity prepareDecoder(final HttpMessage message) throws HttpException {
        BasicHttpEntity entity = new BasicHttpEntity();
        long len = this.incomingContentStrategy.determineLength(message);
        if (len == ContentLengthStrategy.CHUNKED) {
            this.contentDecoder = new ChunkDecoder(this.session.channel(), this.inbuf);
            entity.setChunked(true);
            entity.setContentLength(-1);
        } else if (len == ContentLengthStrategy.IDENTITY) {
            this.contentDecoder = new IdentityDecoder(this.session.channel(), this.inbuf);
            entity.setChunked(false);
            entity.setContentLength(-1);
        } else {
            this.contentDecoder = new LengthDelimitedDecoder(this.session.channel(), this.inbuf, len);
            entity.setChunked(false);
            entity.setContentLength(len);
        }
        
        Header contentTypeHeader = message.getFirstHeader(HTTP.CONTENT_TYPE);
        if (contentTypeHeader != null) {
            entity.setContentType(contentTypeHeader);    
        }
        Header contentEncodingHeader = message.getFirstHeader(HTTP.CONTENT_ENCODING);
        if (contentEncodingHeader != null) {
            entity.setContentEncoding(contentEncodingHeader);    
        }
        return entity;
    }

    protected void prepareEncoder(final HttpMessage message) throws HttpException {
        long len = this.outgoingContentStrategy.determineLength(message);
        if (len == ContentLengthStrategy.CHUNKED) {
            this.contentEncoder = new ChunkEncoder(this.outbuf);
        } else if (len == ContentLengthStrategy.IDENTITY) {
            this.contentEncoder = new IdentityEncoder(this.session.channel());
        } else {
            this.contentEncoder = new LengthDelimitedEncoder(this.session.channel(), len);
        }
    }

    public void close() throws IOException {
        this.closed = true;
        if (this.outbuf.hasData()) {
            this.session.setEvent(EventMask.WRITE);
        } else {
            this.session.close();
        }
    }

    public boolean isOpen() {
        return !this.closed;
    }

    public boolean isStale() {
        return this.session.isClosed();
    }

    public void shutdown() throws IOException {
        this.closed = true;
        this.session.close();
    }
    
}
