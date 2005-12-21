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
import java.net.InetAddress;
import java.net.Socket;

import org.apache.http.Header;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpMutableResponse;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpStatus;
import org.apache.http.NoHttpResponseException;
import org.apache.http.ProtocolException;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.entity.EntityGenerator;
import org.apache.http.entity.EntityWriter;
import org.apache.http.impl.entity.DefaultClientEntityWriter;
import org.apache.http.impl.entity.DefaultEntityGenerator;
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

    private HttpHost targethost = null;
    private InetAddress localAddress = null;

    private final CharArrayBuffer buffer; 
    
    /*
     * Dependent interfaces
     */
    private HttpResponseFactory responsefactory = null;
    private EntityGenerator entitygen = null;
    private EntityWriter entitywriter = null;
    
    public DefaultHttpClientConnection(final HttpHost targethost, final InetAddress localAddress) {
        super();
        this.targethost = targethost;
        this.localAddress = localAddress;
        this.buffer = new CharArrayBuffer(64);
        this.responsefactory = new DefaultHttpResponseFactory();
        this.entitygen = new DefaultEntityGenerator();
        this.entitywriter = new DefaultClientEntityWriter();
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

    public void setEntityGenerator(final EntityGenerator entitygen) {
        if (entitygen == null) {
            throw new IllegalArgumentException("Entity generator may not be null");
        }
        this.entitygen = entitygen;
    }

    public void setEntityWriter(final EntityWriter entitywriter) {
        if (entitywriter == null) {
            throw new IllegalArgumentException("Entity writer may not be null");
        }
        this.entitywriter = entitywriter;
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

    public void sendRequest(final HttpRequest request) 
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
            sendRequestBody((HttpEntityEnclosingRequest)request);
        }
        this.datatransmitter.flush();
    }
    
    public void sendRequestHeader(final HttpEntityEnclosingRequest request) 
            throws HttpException, IOException {
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        assertOpen();
        // reset the data transmitter
        this.datatransmitter.reset(request.getParams());
        
        sendRequestLine(request);
        sendRequestHeaders(request);
        this.datatransmitter.flush();
    }

    public void sendRequestEntity(final HttpEntityEnclosingRequest request) 
            throws HttpException, IOException {
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        sendRequestBody(request);
        this.datatransmitter.flush();
    }

    
    protected void sendRequestLine(final HttpRequest request) 
            throws HttpException, IOException {
        this.buffer.clear();
        RequestLine.format(this.buffer, request.getRequestLine());
        this.datatransmitter.writeLine(this.buffer);
        if (isWirelogEnabled()) {
            wirelog(">> " + this.buffer.toString() + "[\\r][\\n]");
        }
    }

    protected void sendRequestHeaders(final HttpRequest request) 
            throws HttpException, IOException {
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

    protected void sendRequestBody(final HttpEntityEnclosingRequest request) 
            throws HttpException, IOException {
        if (request.getEntity() == null) {
            return;
        }
        this.entitywriter.write(
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
        return readResponse(request);
    }

    public HttpResponse receiveResponse(final HttpRequest request, int timeout) 
            throws HttpException, IOException {
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        assertOpen();
        // reset the data receiver
        this.datareceiver.reset(request.getParams());
        if (this.datareceiver.isDataAvailable(timeout)) {
            return readResponse(request);
        } else {
            return null;
        }
    }

    protected HttpResponse readResponse(final HttpRequest request)
            throws HttpException, IOException {
        HttpMutableResponse response = readResponseStatusLine(request.getParams());
        readResponseHeaders(response);
        if (canResponseHaveBody(request, response)) {
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
    
    protected boolean canResponseHaveBody(final HttpRequest request, final HttpResponse response) {
        if ("HEAD".equalsIgnoreCase(request.getRequestLine().getMethod())) {
            return false;
        }
        int status = response.getStatusLine().getStatusCode(); 
        return status >= HttpStatus.SC_OK 
            && status != HttpStatus.SC_NO_CONTENT 
            && status != HttpStatus.SC_NOT_MODIFIED
            && status != HttpStatus.SC_RESET_CONTENT; 
    }
        
    protected void readResponseBody(
            final HttpMutableResponse response) throws HttpException, IOException {
        HttpEntity entity = this.entitygen.generate(this.datareceiver, response);
        response.setEntity(entity);
    }
    
}
