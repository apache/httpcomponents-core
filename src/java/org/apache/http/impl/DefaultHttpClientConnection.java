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
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;

import org.apache.http.Header;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpMutableEntity;
import org.apache.http.HttpMutableResponse;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.NoHttpResponseException;
import org.apache.http.ProtocolException;
import org.apache.http.SocketFactory;
import org.apache.http.StatusLine;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
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
public class DefaultHttpClientConnection 
        extends AbstractHttpConnection implements HttpClientConnection {

    private static final String EXPECT_DIRECTIVE = "Expect";
    private static final String EXPECT_CONTINUE = "100-Continue";
    private static final int WAIT_FOR_CONTINUE_MS = 10000;
    
    private final HttpHost targethost;
    private final InetAddress localAddress;
    
    public DefaultHttpClientConnection(final HttpHost targethost, final InetAddress localAddress) {
        super();
        if (targethost == null) {
            throw new IllegalArgumentException("Target host may not be null");
        }
        this.targethost = targethost;
        this.localAddress = localAddress;
    }
    
    public DefaultHttpClientConnection(final HttpHost targethost) {
        this(targethost, null);
    }
    
    public void open(final HttpParams params) throws IOException {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        assertNotOpen();
        
        SocketFactory socketfactory = targethost.getProtocol().getSocketFactory();
        Socket socket = socketfactory.createSocket(
                this.targethost.getHostName(), this.targethost.getPort(), 
                this.localAddress, 0, 
                params);
        bind(socket, params);
    }
    
    public HttpHost getHost() {
        return this.targethost;
    }
    
    public void close() throws IOException {
        super.close();
    }

    public HttpResponse sendRequest(final HttpRequest request) 
            throws HttpException, IOException {
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        assertOpen();
        // reset the data transmitter
        this.datatransmitter.reset(request.getParams());
        
        sendRequestLine(request);
        sendRequestHeaders(request);
        
        if (request instanceof HttpEntityEnclosingRequest) {
            // send request may be prematurely terminated by the target server
            HttpResponse response = expectContinue(request);
            if (response != null) {
                return response;
            }
            sendRequestBody((HttpEntityEnclosingRequest)request);
        }
        this.datatransmitter.flush();
        return null;
    }
    
    protected void sendRequestLine(
            final HttpRequest request) throws HttpException, IOException {
        String line = request.getRequestLine().toString();
        this.datatransmitter.writeLine(line);
        if (isWirelogEnabled()) {
            wirelog(">> " + line + "[\\r][\\n]");
        }
    }

    protected void sendRequestHeaders(
            final HttpRequest request) throws HttpException, IOException {
        Header[] headers = request.getAllHeaders();
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

    protected HttpResponse expectContinue(final HttpRequest request) 
            throws HttpException, IOException {
        // See if 'expect-continue' handshake is supported
        HttpVersion ver = request.getRequestLine().getHttpVersion();
        if (ver.greaterEquals(HttpVersion.HTTP_1_1)) {
            // ... and activated
            Header expect = request.getFirstHeader(EXPECT_DIRECTIVE);
            if (expect != null && EXPECT_CONTINUE.equalsIgnoreCase(expect.getValue())) {
                // flush the headers
                this.datatransmitter.flush();
                if (this.datareceiver.isDataAvailable(WAIT_FOR_CONTINUE_MS)) {
                    HttpResponse response = readResponse(request.getParams());
                    int status = response.getStatusLine().getStatusCode();
                    if (status < 200) {
                        if (status != HttpStatus.SC_CONTINUE) {
                            throw new ProtocolException("Unexpected response: " + 
                                    response.getStatusLine());
                        }
                    } else {
                        return response;
                    }
                }
            }
        }
        return null;
    }
    
    protected void sendRequestBody(final HttpEntityEnclosingRequest request) 
            throws HttpException, IOException {
        if (request.getEntity() == null) {
            return;
        }
        EntityWriter entitywriter = new DefaultEntityWriter();
        entitywriter.write(
                request.getEntity(),
                request.getRequestLine().getHttpVersion(),
                this.datatransmitter);
    }
    
    public HttpResponse receiveResponse(final HttpRequest request) 
                                                    throws HttpException, IOException {
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        assertOpen();

        HttpParams params = request.getParams();
        // reset the data receiver
        this.datareceiver.reset(params);

        for (;;) {
            HttpResponse response = readResponse(params);
            int statuscode = response.getStatusLine().getStatusCode();
            if (statuscode >= 200) {
                return response;
            }
            if (isWarnEnabled()) {
                warn("Unexpected provisional response: " + response.getStatusLine());
            }
        }
    }

    protected HttpResponse readResponse(final HttpParams params)
            throws HttpException, IOException {
        this.datareceiver.reset(params);
        HttpMutableResponse response = readResponseStatusLine(params);
        readResponseHeaders(response);
        if (canResponseHaveBody(response)) {
            readResponseBody(response);
        }
        return response;
    }

    protected HttpMutableResponse readResponseStatusLine(final HttpParams params) 
                throws HttpException, IOException {
        //read out the HTTP status string
        int maxGarbageLines = params.getIntParameter(
                HttpProtocolParams.STATUS_LINE_GARBAGE_LIMIT, Integer.MAX_VALUE);
        int count = 0;
        String s;
        do {
            s = this.datareceiver.readLine();
            if (s == null && count == 0) {
                // The server just dropped connection on us
                throw new NoHttpResponseException("The server " + 
                        this.targethost.getHostName() + " failed to respond");
            }
            if (s != null && StatusLine.startsWithHTTP(s)) {
                // Got one
                break;
            } else if (s == null || count >= maxGarbageLines) {
                // Giving up
                throw new ProtocolException("The server " + this.targethost.getHostName() + 
                        " failed to respond with a valid HTTP response");
            }
            count++;
            if (isWirelogEnabled()) {
                wirelog("<< " + s + "[\\r][\\n]");
            }
        } while(true);
        //create the status line from the status string
        StatusLine statusline = StatusLine.parse(s);
        if (isWirelogEnabled()) {
            wirelog("<< " + s + "[\\r][\\n]");
        }
        return HttpResponseFactory.newHttpResponse(statusline);
    }

    protected void readResponseHeaders(
            final HttpMutableResponse response) throws HttpException, IOException {
        Header[] headers = HeadersParser.processHeaders(this.datareceiver);
        for (int i = 0; i < headers.length; i++) {
            response.addHeader(headers[i]);
            if (isWirelogEnabled()) {
                wirelog("<< " + headers[i].toString() + "[\\r][\\n]");
            }
        }
        wirelog("<< [\\r][\\n]");
    }
    
    protected boolean canResponseHaveBody(final HttpResponse response) {
        int status = response.getStatusLine().getStatusCode(); 
        return status >= HttpStatus.SC_OK 
            && status != HttpStatus.SC_NO_CONTENT 
            && status != HttpStatus.SC_NOT_MODIFIED; 
    }
        
    protected void readResponseBody(
            final HttpMutableResponse response) throws HttpException, IOException {
        EntityGenerator entitygen = new DefaultEntityGenerator();
        HttpMutableEntity entity = entitygen.generate(this.datareceiver, response);
        // if there is a result - ALWAYS wrap it in an observer which will
        // close the underlying stream as soon as it is consumed, and notify
        // the watcher that the stream has been consumed.
        InputStream instream = entity.getContent();
        instream = new AutoCloseInputStream(
                instream, new DefaultResponseConsumedWatcher(this, response));
        entity.setContent(instream);
        response.setEntity(entity);
    }
    
}
