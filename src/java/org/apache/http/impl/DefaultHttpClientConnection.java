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
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.NoHttpResponseException;
import org.apache.http.ProtocolException;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.impl.entity.DefaultClientEntityWriter;
import org.apache.http.impl.entity.DefaultEntityGenerator;
import org.apache.http.impl.entity.EntityGenerator;
import org.apache.http.impl.entity.EntityWriter;
import org.apache.http.io.CharArrayBuffer;
import org.apache.http.io.SocketFactory;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

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
    
    private HttpHost targethost = null;
    private InetAddress localAddress = null;

    private final CharArrayBuffer buffer; 
    
    /*
     * Dependent interfaces
     */
    private HttpResponseFactory responsefactory = null; 
    
    public DefaultHttpClientConnection(final HttpHost targethost, final InetAddress localAddress) {
        super();
        this.targethost = targethost;
        this.localAddress = localAddress;
        this.buffer = new CharArrayBuffer(64);
        this.responsefactory = new DefaultHttpResponseFactory();
    }
    
    public DefaultHttpClientConnection(final HttpHost targethost) {
        this(targethost, null);
    }
    
    public DefaultHttpClientConnection() {
        this(null, null);
    }
    
    public void setResponseFactory(final HttpResponseFactory responsefactory) {
        if (responsefactory == null) {
            throw new IllegalArgumentException("Factory may not be null");
        }
        this.responsefactory = responsefactory;
    }

    public void open(final HttpParams params) throws IOException {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        assertNotOpen();
        if (this.targethost == null) {
        	throw new IllegalStateException("Target host not specified");
        }
        SocketFactory socketfactory = this.targethost.getScheme().getSocketFactory();
        Socket socket = socketfactory.createSocket(
                this.targethost.getHostName(), this.targethost.getPort(), 
                this.localAddress, 0, 
                params);
        bind(socket, params);
    }
    
    public HttpHost getTargetHost() {
        return this.targethost;
    }
    
    public InetAddress getLocalAddress() {
        return this.localAddress;
    }
    
    public void setTargetHost(final HttpHost targethost) {
    	if (targethost == null) {
    		throw new IllegalArgumentException("Target host may not be null");
    	}
    	assertNotOpen();
    	this.targethost = targethost;
    }
    
    public void setLocalAddress(final InetAddress localAddress) {
    	assertNotOpen();
    	this.localAddress = localAddress;
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
        this.buffer.clear();
        RequestLine.format(this.buffer, request.getRequestLine());
        this.datatransmitter.writeLine(this.buffer);
        if (isWirelogEnabled()) {
            wirelog(">> " + this.buffer.toString() + "[\\r][\\n]");
        }
    }

    protected void sendRequestHeaders(
            final HttpRequest request) throws HttpException, IOException {
        Header[] headers = request.getAllHeaders();
        for (int i = 0; i < headers.length; i++) {
            this.buffer.clear();
            Header.format(this.buffer, headers[i]);
            this.datatransmitter.writeLine(this.buffer);
            if (isWirelogEnabled()) {
                wirelog(">> " + this.buffer.toString() + "[\\r][\\n]");
            }
        }
        this.buffer.clear();
        this.datatransmitter.writeLine(this.buffer);
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
                    HttpResponse response = readResponse(request);
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
        EntityWriter entitywriter = new DefaultClientEntityWriter();
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

        // reset the data receiver
        this.datareceiver.reset(request.getParams());

        for (;;) {
            HttpResponse response = readResponse(request);
            int statuscode = response.getStatusLine().getStatusCode();
            if (statuscode >= 200) {
                return response;
            }
            if (isWarnEnabled()) {
                warn("Unexpected provisional response: " + response.getStatusLine());
            }
        }
    }

    protected HttpResponse readResponse(final HttpRequest request)
            throws HttpException, IOException {
        this.datareceiver.reset(request.getParams());
        HttpMutableResponse response = readResponseStatusLine(request.getParams());
        readResponseHeaders(response);
        ResponseStrategy responsestrategy = new DefaultResponseStrategy();
        if (responsestrategy.canHaveEntity(request, response)) {
            readResponseBody(response);
        }
        return response;
    }
    
    /**
     * Tests if the string starts with 'HTTP' signature.
     * @param buffer buffer to test
     * @return <tt>true</tt> if the line starts with 'HTTP' 
     *   signature, <tt>false</tt> otherwise.
     */
    private static boolean startsWithHTTP(final CharArrayBuffer buffer) {
        try {
            int i = 0;
            while (Character.isWhitespace(buffer.charAt(i))) {
                ++i;
            }
            return buffer.charAt(i) == 'H' 
                && buffer.charAt(i + 1) == 'T'
                && buffer.charAt(i + 2) == 'T'
                && buffer.charAt(i + 3) == 'P';
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }
    
    protected HttpMutableResponse readResponseStatusLine(final HttpParams params) 
                throws HttpException, IOException {
        // clear the buffer
        this.buffer.clear();
        //read out the HTTP status string
        int maxGarbageLines = params.getIntParameter(
                HttpProtocolParams.STATUS_LINE_GARBAGE_LIMIT, Integer.MAX_VALUE);
        int count = 0;
        do {
            int i = this.datareceiver.readLine(this.buffer);
            if (i == -1 && count == 0) {
                // The server just dropped connection on us
                throw new NoHttpResponseException("The server " + 
                        this.targethost.getHostName() + " failed to respond");
            }
            if (startsWithHTTP(this.buffer)) {
                // Got one
                break;
            } else if (i == -1 || count >= maxGarbageLines) {
                // Giving up
                throw new ProtocolException("The server " + this.targethost.getHostName() + 
                        " failed to respond with a valid HTTP response");
            }
            count++;
            if (isWirelogEnabled()) {
                wirelog("<< " + this.buffer.toString() + "[\\r][\\n]");
            }
        } while(true);
        //create the status line from the status string
        StatusLine statusline = StatusLine.parse(this.buffer, 0, this.buffer.length());
        if (isWirelogEnabled()) {
            wirelog("<< " + this.buffer.toString() + "[\\r][\\n]");
        }
        HttpMutableResponse response = this.responsefactory.newHttpResponse(statusline);
        response.getParams().setDefaults(params);
        return response;
    }

    protected void readResponseHeaders(
            final HttpMutableResponse response) throws HttpException, IOException {
        Header[] headers = Header.parseAll(this.datareceiver);
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
