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

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.concurrent.Cancellable;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.protocol.HttpContext;

/**
 * {@code HttpAsyncResponseConsumer} is a callback interface whose methods
 * get invoked to process an HTTP response message and to stream message
 * content from a non-blocking HTTP connection on the client side.
 *
 * @param <T> the result type of response processing.
 * @since 4.2
 */
public interface HttpAsyncResponseConsumer<T> extends Closeable, Cancellable {

    /**
     * Invoked when a HTTP response message is received. Please note
     * that the {@link #consumeContent(ContentDecoder, IOControl)} method
     * will be invoked only if the response messages has a content entity
     * enclosed.
     *
     * @param response HTTP response message.
     * @throws HttpException in case of HTTP protocol violation
     * @throws IOException in case of an I/O error
     */
    void responseReceived(HttpResponse response) throws IOException, HttpException;

    /**
     * Invoked to process a chunk of content from the {@link ContentDecoder}.
     * The {@link IOControl} interface can be used to suspend input event
     * notifications if the consumer is temporarily unable to process content.
     * <p>
     * The consumer can use the {@link ContentDecoder#isCompleted()} method
     * to find out whether or not the message content has been fully consumed.
     * <p>
     * Please note that the {@link ContentDecoder} object is not thread-safe and
     * should only be used within the context of this method call.
     * The {@link IOControl} object can be shared and used on other thread
     * to resume input event notifications when the consumer is capable of
     * processing more content.
     *
     * @param decoder content decoder.
     * @param ioctrl I/O control of the underlying connection.
     * @throws IOException in case of an I/O error
     */
    void consumeContent(ContentDecoder decoder, IOControl ioctrl) throws IOException;

    /**
     * Invoked to signal that the response has been fully processed.
     *
     * @param context HTTP context
     */
    void responseCompleted(HttpContext context);

    /**
     * Invoked to signal that the response processing terminated abnormally.
     *
     * @param ex exception
     */
    void failed(Exception ex);

    /**
     * Returns an exception in case of an abnormal termination. This method
     * returns {@code null} if the response processing is still ongoing
     * or if it completed successfully.
     *
     * @see #isDone()
     */
    Exception getException();

    /**
     * Returns a result of the response processing, when available. This method
     * returns {@code null} if the response processing is still ongoing.
     *
     * @see #isDone()
     */
    T getResult();

    /**
     * Determines whether or not the response processing completed. If the
     * response processing terminated normally {@link #getResult()}
     * can be used to obtain the result. If the response processing terminated
     * abnormally {@link #getException()} can be used to obtain the cause.
     */
    boolean isDone();

}
