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
package org.apache.http.nio.protocol;

import java.io.Closeable;
import java.io.IOException;

import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.protocol.HttpContext;

/**
 * <tt>HttpAsyncRequestConsumer</tt> is a callback interface whose methods
 * get invoked to process an HTTP request message and to stream message
 * content from a non-blocking HTTP connection through a {@link ContentDecoder}.
 *
 * @since 4.2
 */
public interface HttpAsyncRequestConsumer<T> extends Closeable {

    /**
     * Invoked when a HTTP request message is received. Please note
     * that the {@link #consumeContent(ContentDecoder, IOControl)} method
     * will be invoked only for if the request message implements
     * {@link HttpEntityEnclosingRequest} interface and has a content
     * entity enclosed.
     *
     * @return HTTP request message.
     * @throws HttpException in case of HTTP protocol violation
     * @throws IOException in case of an I/O error
     */
    void requestReceived(HttpRequest request) throws HttpException, IOException;

    /**
     * Invoked to process a chunk of content from the {@link ContentDecoder}.
     * The {@link IOControl} interface can be used to suspend input events
     * if the consumer is temporarily unable to consume more content.
     * <p/>
     * The consumer can use the {@link ContentDecoder#isCompleted()} method
     * to find out whether or not the message content has been fully consumed.
     *
     * @param decoder content decoder.
     * @param ioctrl I/O control of the underlying connection.
     * @throws IOException in case of an I/O error
     */
    void consumeContent(ContentDecoder decoder, IOControl ioctrl) throws IOException;

    /**
     * Invoked to signal that the request has been fully processed.
     *
     * @param context HTTP context
     */
    void requestCompleted(HttpContext context);

    /**
     * Returns an exception in case of an abnormal termination. This method
     * returns <code>null</code> if the request execution is still ongoing
     * or if it completed successfully.
     *
     * @see #isDone()
     */
    Exception getException();

    /**
     * Returns a result of the request execution, when available. This method
     * returns <code>null</code> if the request execution is still ongoing.
     *
     * @see #isDone()
     */
    T getResult();

    /**
     * Determines whether or not the request execution completed. If the
     * request processing terminated normally {@link #getResult()}
     * can be used to obtain the result. If the request processing terminated
     * abnormally {@link #getException()} can be used to obtain the cause.
     */
    boolean isDone();

}
