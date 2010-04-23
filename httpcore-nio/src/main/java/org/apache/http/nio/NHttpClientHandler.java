/*
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

package org.apache.http.nio;

import java.io.IOException;

import org.apache.http.HttpException;

/**
 * Abstract client-side HTTP protocol handler.
 *
 * @since 4.0
 */
public interface NHttpClientHandler {

    /**
     * Triggered when a new outgoing connection is created.
     *
     * @param conn new outgoing HTTP connection.
     * @param attachment an object that was attached to the session request
     */
    void connected(NHttpClientConnection conn, Object attachment);

    /**
     * Triggered when the connection is ready to accept a new HTTP request.
     * The protocol handler does not have to submit a request if it is not
     * ready.
     *
     * @see NHttpClientConnection
     *
     * @param conn HTTP connection that is ready to accept a new HTTP request.
     */
    void requestReady(NHttpClientConnection conn);

    /**
     * Triggered when an HTTP response is received. The connection
     * passed as a parameter to this method is guaranteed to return
     * a valid HTTP response object.
     * <p/>
     * If the response received encloses a response entity this method will
     * be followed by a series of
     * {@link #inputReady(NHttpClientConnection, ContentDecoder)} calls
     * to transfer the response content.
     *
     * @see NHttpClientConnection
     *
     * @param conn HTTP connection that contains an HTTP response
     */
    void responseReceived(NHttpClientConnection conn);

    /**
     * Triggered when the underlying channel is ready for reading a
     * new portion of the response entity through the corresponding
     * content decoder.
     * <p/>
     * If the content consumer is unable to process the incoming content,
     * input event notifications can be temporarily suspended using
     * {@link IOControl} interface.
     *
     * @see NHttpClientConnection
     * @see ContentDecoder
     * @see IOControl
     *
     * @param conn HTTP connection that can produce a new portion of the
     * incoming response content.
     * @param decoder The content decoder to use to read content.
     */
    void inputReady(NHttpClientConnection conn, ContentDecoder decoder);

    /**
     * Triggered when the underlying channel is ready for writing a next portion
     * of the request entity through the corresponding content encoder.
     * <p>
     * If the content producer is unable to generate the outgoing content,
     * output event notifications can be temporarily suspended using
     * {@link IOControl} interface.
     *
     * @see NHttpClientConnection
     * @see ContentEncoder
     * @see IOControl
     *
     * @param conn HTTP connection that can accommodate a new portion
     * of the outgoing request content.
     * @param encoder The content encoder to use to write content.
     */
    void outputReady(NHttpClientConnection conn, ContentEncoder encoder);

    /**
     * Triggered when an I/O error occurs while reading from or writing
     * to the underlying channel.
     *
     * @param conn HTTP connection that caused an I/O error
     * @param ex I/O exception
     */
    void exception(NHttpClientConnection conn, IOException ex);

    /**
     * Triggered when an HTTP protocol violation occurs while receiving
     * an HTTP response.
     *
     * @param conn HTTP connection that caused an HTTP protocol violation
     * @param ex HTTP protocol violation exception
     */
    void exception(NHttpClientConnection conn, HttpException ex);

    /**
     * Triggered when no input is detected on this connection over the
     * maximum period of inactivity.
     *
     * @param conn HTTP connection that caused timeout condition.
     */
    void timeout(NHttpClientConnection conn);

    /**
     * Triggered when the connection is closed.
     *
     * @param conn closed HTTP connection.
     */
    void closed(NHttpClientConnection conn);

}
