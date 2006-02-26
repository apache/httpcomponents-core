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

package org.apache.http.protocol;

import java.io.IOException;
import java.net.ProtocolException;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.params.HttpConnectionParams;
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
public class HttpRequestExecutor extends AbstractHttpProcessor {

    protected static final int WAIT_FOR_CONTINUE_MS = 10000;

    /** The context holding the default context information. */    
    protected final HttpContext defaultContext;
    
    private HttpParams params = null;
    private HttpRequestRetryHandler retryhandler = null;

    /**
     * Create a new request executor with default context information.
     * The attributes in the argument context will be made available
     * in the context used for executing a request.
     *
     * @param parentContext     the default context information,
     *                          or <code>null</code>
     */    
    public HttpRequestExecutor(final HttpContext parentContext) {
        super();
        this.defaultContext = new HttpExecutionContext(parentContext);
    }

    /**
     * Create a new request executor.
     */
    public HttpRequestExecutor() {
        this(null);
    }

    /**
     * Obtain the default context information.
     * This is not necessarily the same object passed to the constructor,
     * but the default context information will be available here.
     *
     * @return  the context holding the default context information
     */
    public final HttpContext getContext() {
        return this.defaultContext;
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
     * Obtain the retry handler.
     *
     * @return  the handler deciding whether a request should be retried
     */
    public final HttpRequestRetryHandler getRetryHandler() {
        return this.retryhandler;
    }

    /**
     * Set the retry handler.
     *
     * @param retryhandler      the handler to decide whether a request
     *                          should be retried
     */
    public final void setRetryHandler(final HttpRequestRetryHandler retryhandler) {
        this.retryhandler = retryhandler;
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
     * @param conn      the connection over which to send.
     *                  The {@link HttpClientConnection#setTargetHost target}
     *                  host has to be set before calling this method.
     *
     * @return  the response to the request, postprocessed
     *
     * @throws HttpException      in case of a protocol or processing problem
     * @throws IOException        in case of an I/O problem
     */    
    public HttpResponse execute(
            final HttpRequest request,
            final HttpClientConnection conn) 
                throws IOException, HttpException {
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        if (conn == null) {
            throw new IllegalArgumentException("Client connection may not be null");
        }

        //@@@ behavior if proxying - set real target or proxy, or both?
        this.defaultContext.setAttribute(HttpExecutionContext.HTTP_TARGET_HOST,
                conn.getTargetHost());
        this.defaultContext.setAttribute(HttpExecutionContext.HTTP_CONNECTION, 
                conn);
        
        doPrepareRequest(request, this.defaultContext);

        this.defaultContext.setAttribute(HttpExecutionContext.HTTP_REQUEST, 
                request);
        
        HttpResponse response = null;
        // loop until the method is successfully processed, the retryHandler 
        // returns false or a non-recoverable exception is thrown
        for (int execCount = 0; ; execCount++) {
            try {
                doEstablishConnection(conn, conn.getTargetHost(),
                                      request.getParams());
                response = doSendRequest(request, conn, this.defaultContext);
                if (response == null) {
                    response = doReceiveResponse(request, conn,
                                                 this.defaultContext);
                }
                // exit retry loop
                break;
            } catch (IOException ex) {
                conn.close();
                if (this.retryhandler == null) {
                    throw ex;
                }
                if (!this.retryhandler.retryRequest(ex, execCount, null)) {
                    throw ex;
                }
            } catch (HttpException ex) {
                conn.close();
                throw ex;
            } catch (RuntimeException ex) {
                conn.close();
                throw ex;
            }
        }

        doFinishResponse(response, this.defaultContext);

        return response;

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
        preprocessRequest(request, context);
    }

    /**
     * Establish a connection with the target host.
     *
     * @param conn      the HTTP connection
     * @param target    the target host for the request, or
     *                  <code>null</code> to send to the host already
     *                  set as the connection target
     *
     * @throws HttpException      in case of a problem
     * @throws IOException        in case of an IO problem
     */
    protected void doEstablishConnection(
            final HttpClientConnection conn,
            final HttpHost target,
            final HttpParams params)
                throws HttpException, IOException {
        if (conn == null) {
            throw new IllegalArgumentException("HTTP connection may not be null");
        }
        if (target == null) {
            throw new IllegalArgumentException("Target host may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        // make sure the connection is open and points to the target host
        if ((target == null) || target.equals(conn.getTargetHost())) {
            // host and port ok, check whether connection needs to be opened
            if (HttpConnectionParams.isStaleCheckingEnabled(params)) {
                if (conn.isOpen() && conn.isStale()) {
                    conn.close();
                }
            }
            if (!conn.isOpen()) {
                conn.open(params);
                //TODO: Implement secure tunnelling (@@@ HttpRequestExecutor) 
            }

        } else {
            // wrong target, point connection to target
            if (conn.isOpen()) {
                conn.close();
            }
            conn.setTargetHost(target);
            conn.open(params);

        } // if connection points to target else        
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
     * @param conn      the connection over which to send the request, already
     *                  {@link #doEstablishConnection established}
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
        postprocessResponse(response, context);
    }

} // class HttpRequestExecutor
