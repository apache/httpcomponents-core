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

package org.apache.http.protocol;

import java.io.IOException;
import java.net.ProtocolException;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.params.HttpParams;

/**
 * Sends HTTP requests and receives the responses.
 * Takes care of request preprocessing and response postprocessing
 * by the respective interceptors.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class HttpRequestExecutor {

    //TODO make this value customizable, just use this as a default
    protected static final int WAIT_FOR_CONTINUE_MS = 10000;

    private HttpParams params;
    private final HttpProcessor processor;

    /**
     * Create a new request executor.
     *
     * @param proc      the processor to use on requests and responses
     */
    public HttpRequestExecutor(HttpProcessor proc) {
        if (proc == null)
            throw new IllegalArgumentException
                ("HTTP processor must not be null.");

        this.processor = proc;
    }

    /**
     * Obtain the parameters for executing requests.
     *
     * @return  the currently installed parameters
     */
    public final HttpParams getParams() {
        return this.params;
    }

    /**
     * Set new parameters for executing requests.
     *
     * @param params    the new parameters to use from now on
     */
    public final void setParams(final HttpParams params) {
        this.params = params;
    }

    /**
     * Decide whether a response comes with an entity.
     * The implementation in this class is based on RFC 2616.
     * Unknown methods and response codes are supposed to
     * indicate responses with an entity.
     * <br/>
     * Derived executors can override this method to handle
     * methods and response codes not specified in RFC 2616.
     *
     * @param request   the request, to obtain the executed method
     * @param response  the response, to obtain the status code
     */
    protected boolean canResponseHaveBody(final HttpRequest request,
                                          final HttpResponse response) {

        if ("HEAD".equalsIgnoreCase(request.getRequestLine().getMethod())) {
            return false;
        }
        int status = response.getStatusLine().getStatusCode(); 
        return status >= HttpStatus.SC_OK 
            && status != HttpStatus.SC_NO_CONTENT 
            && status != HttpStatus.SC_NOT_MODIFIED
            && status != HttpStatus.SC_RESET_CONTENT; 
    }

    /**
     * Synchronously send a request and obtain the response.
     *
     * @param request   the request to send. It will be preprocessed.
     * @param conn      the open connection over which to send
     *
     * @return  the response to the request, postprocessed
     *
     * @throws HttpException      in case of a protocol or processing problem
     * @throws IOException        in case of an I/O problem
     */    
    public HttpResponse execute(
            final HttpRequest request,
            final HttpClientConnection conn,
            final HttpContext context) 
                throws IOException, HttpException {
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        if (conn == null) {
            throw new IllegalArgumentException("Client connection may not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("HTTP context may not be null");
        }

        context.setAttribute(HttpExecutionContext.HTTP_CONNECTION, conn);
        context.setAttribute(HttpExecutionContext.HTTP_REQUEST, request);
        
        try {
            doPrepareRequest(request, context);
            HttpResponse response = doSendRequest(request, conn, context);
            if (response == null) {
                response = doReceiveResponse(request, conn, context);
            }
            doFinishResponse(response, context);
            return response;
        } catch (IOException ex) {
            conn.close();
            throw ex;
        } catch (HttpException ex) {
            conn.close();
            throw ex;
        } catch (RuntimeException ex) {
            conn.close();
            throw ex;
        }
    }

    /**
     * Prepare a request for sending.
     *
     * @param request   the request to prepare
     * @param context   the context for sending the request
     *
     * @throws HttpException      in case of a protocol or processing problem
     * @throws IOException        in case of an I/O problem
     */
    protected void doPrepareRequest(
            final HttpRequest          request,
            final HttpContext          context)
                throws HttpException, IOException {
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("HTTP context may not be null");
        }
        // link default parameters
        request.getParams().setDefaults(this.params);
        processor.process(request, context);
    }

    /**
     * Send a request over a connection.
     * This method also handles the expect-continue handshake if necessary.
     * If it does not have to handle an expect-continue handshake, it will
     * not use the connection for reading or anything else that depends on
     * data coming in over the connection.
     *
     * @param request   the request to send, already
     *                  {@link #doPrepareRequest prepared}
     * @param conn      the connection over which to send the request,
     *                  already established
     * @param context   the context for sending the request
     *
     * @return  a terminal response received as part of an expect-continue
     *          handshake, or
     *          <code>null</code> if the expect-continue handshake is not used
     *
     * @throws HttpException      in case of a protocol or processing problem
     * @throws IOException        in case of an I/O problem
     */
    protected HttpResponse doSendRequest(
            final HttpRequest request,
            final HttpClientConnection conn,
            final HttpContext context)
                throws IOException, HttpException {
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        if (conn == null) {
            throw new IllegalArgumentException("HTTP connection may not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("HTTP context may not be null");
        }

        HttpResponse response = null;
        context.setAttribute(HttpExecutionContext.HTTP_REQ_SENT, Boolean.FALSE);

        conn.sendRequestHeader(request);
        if (request instanceof HttpEntityEnclosingRequest) {
            HttpEntityEnclosingRequest entityEnclRequest =
                (HttpEntityEnclosingRequest) request;

            // Check for expect-continue handshake. We have to flush the
            // headers and wait for an 100-continue response to handle it.
            // If we get a different response, we must not send the entity.
            boolean sendentity = true;
            final HttpVersion ver = request.getRequestLine().getHttpVersion();
            if (entityEnclRequest.expectContinue() &&
                ver.greaterEquals(HttpVersion.HTTP_1_1)) {

                conn.flush();
                // As suggested by RFC 2616 section 8.2.3, we don't wait for a
                // 100-continue response forever. On timeout, send the entity.
                if (conn.isResponseAvailable(WAIT_FOR_CONTINUE_MS)) {
                    response = conn.receiveResponseHeader(request.getParams());
                    if (canResponseHaveBody(request, response)) {
                        conn.receiveResponseEntity(response);
                    }
                    int status = response.getStatusLine().getStatusCode();
                    if (status < 200) {
                        //@@@ TODO: is this in line with RFC 2616, 10.1?
                        if (status != HttpStatus.SC_CONTINUE) {
                            throw new ProtocolException(
                                    "Unexpected response: " + response.getStatusLine());
                        }
                        // discard 100-continue
                        response = null;
                    } else {
                        sendentity = false;
                    }
                }
            }
            if (sendentity) {
                conn.sendRequestEntity(entityEnclRequest);
            }
        }
        conn.flush();
        context.setAttribute(HttpExecutionContext.HTTP_REQ_SENT, Boolean.TRUE);
        return response;
    } 

    /**
     * Wait for and receive a response.
     * This method will automatically ignore intermediate responses
     * with status code 1xx.
     *
     * @param request   the request for which to obtain the response
     * @param conn      the connection over which the request was sent
     * @param context   the context for receiving the response
     *
     * @return  the final response, not yet post-processed
     *
     * @throws HttpException      in case of a protocol or processing problem
     * @throws IOException        in case of an I/O problem
     */
    protected HttpResponse doReceiveResponse(
            final HttpRequest          request,
            final HttpClientConnection conn,
            final HttpContext          context)
                throws HttpException, IOException {
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        if (conn == null) {
            throw new IllegalArgumentException("HTTP connection may not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("HTTP context may not be null");
        }

        HttpResponse response = null;
        int statuscode = 0;

        while (response == null || statuscode < HttpStatus.SC_OK) {

            response = conn.receiveResponseHeader(request.getParams());
            if (canResponseHaveBody(request, response)) {
                conn.receiveResponseEntity(response);
            }
            statuscode = response.getStatusLine().getStatusCode();

        } // while intermediate response

        return response;

    }

    /**
     * Finish a response.
     * This includes post-processing of the response object.
     * It does <i>not</i> read the response entity, if any.
     * It does <i>not</i> allow for immediate re-use of the
     * connection over which the response is coming in.
     *
     * @param response  the response object to finish
     * @param context   the context for post-processing the response
     *
     * @throws HttpException      in case of a protocol or processing problem
     * @throws IOException        in case of an I/O problem
     */
    protected void doFinishResponse(
            final HttpResponse response,
            final HttpContext context)
                throws HttpException, IOException {
        if (response == null) {
            throw new IllegalArgumentException("HTTP response may not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("HTTP context may not be null");
        }
        processor.process(response, context);
    }

} // class HttpRequestExecutor
