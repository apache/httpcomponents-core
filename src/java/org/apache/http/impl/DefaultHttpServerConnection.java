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

package org.apache.http.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpMutableEntity;
import org.apache.http.HttpMutableEntityEnclosingRequest;
import org.apache.http.HttpMutableRequest;
import org.apache.http.HttpMutableResponse;
import org.apache.http.HttpOutgoingEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpServerConnection;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;
import org.apache.http.RequestLine;
import org.apache.http.io.ChunkedOutputStream;
import org.apache.http.io.HttpDataOutputStream;
import org.apache.http.params.HttpParams;
import org.apache.http.util.HeadersParser;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class DefaultHttpServerConnection 
        extends AbstractHttpConnection implements HttpServerConnection {

    private static final String EXPECT_DIRECTIVE = "Expect";
    private static final String EXPECT_CONTINUE = "100-Continue";

    public DefaultHttpServerConnection() {
        super();
    }
    
    public void bind(final Socket socket, final HttpParams params) throws IOException {
        super.bind(socket, params);
    }

    public HttpRequest receiveRequest(final HttpParams params) 
            throws HttpException, IOException {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        assertOpen();
        // reset the data transmitter
        this.datareceiver.reset(params);

        HttpMutableRequest request = receiveRequestLine(params);
        receiveRequestHeaders(request);

        HttpVersion responsever = request.getRequestLine().getHttpVersion();
        if (responsever.greaterEquals(HttpVersion.HTTP_1_1)) {
            responsever = HttpVersion.HTTP_1_1;
        }

        boolean validated = false;
        if (request instanceof HttpMutableEntityEnclosingRequest) {
            if (expectContinue(request)) {
                validateRequest(request);
                validated = true;
                sendContinue(responsever);
            }
            receiveRequestBody((HttpMutableEntityEnclosingRequest)request);
        }
        if (!validated) {
            validateRequest(request);
        }
        return request;
    }
    
    protected HttpMutableRequest receiveRequestLine(final HttpParams params)
            throws HttpException, IOException {
        String line = this.datareceiver.readLine();
        if (line == null) {
            throw new ConnectionClosedException("Client closed connection"); 
        }
        RequestLine requestline = RequestLine.parse(line);
        if (isWirelogEnabled()) {
            wirelog(">> " + line + "[\\r][\\n]");
        }
        HttpMutableRequest request = HttpRequestFactory.newHttpRequest(requestline);
        request.setParams((HttpParams)params.clone());
        return request;
    }
    
    protected void receiveRequestHeaders(final HttpMutableRequest request) 
            throws HttpException, IOException {
        Header[] headers = HeadersParser.processHeaders(this.datareceiver);
        for (int i = 0; i < headers.length; i++) {
            request.addHeader(headers[i]);
            if (isWirelogEnabled()) {
                wirelog(">> " + headers[i].toString() + "[\\r][\\n]");
            }
        }
        wirelog(">> [\\r][\\n]");
    }

    protected boolean expectContinue(final HttpRequest request) {
        Header expect = request.getFirstHeader(EXPECT_DIRECTIVE);
        return expect != null && EXPECT_CONTINUE.equalsIgnoreCase(expect.getValue());
    }

    protected void validateRequest(final HttpRequest request)
            throws RequestValidationException {
    }
    
    protected void sendContinue(final HttpVersion ver) 
            throws IOException, HttpException {
        HttpMutableResponse response = 
            HttpResponseFactory.newHttpResponse(ver, HttpStatus.SC_CONTINUE);
        sendResponse(response);
    }

    protected void receiveRequestBody(final HttpMutableEntityEnclosingRequest request)
            throws HttpException, IOException {
        EntityGenerator entitygen = new DefaultEntityGenerator();
        HttpMutableEntity entity = entitygen.generate(this.datareceiver, request);
        request.setEntity(entity);
    }
    
    
    public void sendResponse(final HttpResponse response) 
            throws HttpException, IOException {
        if (response == null) {
            throw new IllegalArgumentException("HTTP response may not be null");
        }
        assertOpen();

        // reset the data transmitter
        this.datatransmitter.reset(response.getParams());
        sendResponseStatusLine(response);
        sendResponseHeaders(response);
        sendResponseBody(response);
        this.datatransmitter.flush();
    }
    
    protected void sendResponseStatusLine(final HttpResponse response) 
            throws HttpException, IOException {
        String line = response.getStatusLine().toString();
        this.datatransmitter.writeLine(line);
        if (isWirelogEnabled()) {
            wirelog(">> " + line + "[\\r][\\n]");
        }
    }

    protected void sendResponseHeaders(final HttpResponse response) 
            throws HttpException, IOException {
        Header[] headers = response.getAllHeaders();
        for (int i = 0; i < headers.length; i++) {
            String line = headers[i].toString();
            this.datatransmitter.writeLine(line);
            if (isWirelogEnabled()) {
                wirelog(">> " + line + "[\\r][\\n]");
            }
        }
        this.datatransmitter.writeLine("");
        if (isWirelogEnabled()) {
            wirelog(">> [\\r][\\n]");
        }
    }

    protected void sendResponseBody(final HttpResponse response) 
            throws HttpException, IOException {
        if (response.getEntity() == null) {
            return;
        }
        HttpOutgoingEntity entity = (HttpOutgoingEntity)response.getEntity();
        HttpVersion ver = response.getStatusLine().getHttpVersion();
        boolean chunked = entity.isChunked() || entity.getContentLength() < 0;  
        if (chunked && ver.lessEquals(HttpVersion.HTTP_1_0)) {
            throw new ProtocolException(
                    "Chunked transfer encoding not allowed for " + ver);
        }
        OutputStream outstream = new HttpDataOutputStream(this.datatransmitter);
        if (chunked) {
            outstream = new ChunkedOutputStream(outstream);
        }
        entity.writeTo(outstream);
        if (outstream instanceof ChunkedOutputStream) {
            ((ChunkedOutputStream) outstream).finish();
        }
        outstream.flush();
    }
        
}
