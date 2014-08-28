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
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.concurrent.Cancellable;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;

/**
 * {@code HttpAsyncClientExchangeHandler} represents a callback interface whose
 * methods get invoked when executing one or multiple HTTP message exchanges
 * on the client side.
 * <p>
 * Individual {@code HttpAsyncClientExchangeHandler} are expected to make use of
 * a {@link org.apache.http.protocol.HttpProcessor} to generate mandatory protocol
 * headers for all outgoing messages and apply common, cross-cutting message
 * transformations to all incoming and outgoing messages.
 * {@code HttpAsyncClientExchangeHandler}s can delegate implementation
 * of application specific content generation and processing to
 * a {@link HttpAsyncRequestProducer} and a {@link HttpAsyncResponseConsumer}.
 *
 * @since 4.3
 */
public interface HttpAsyncClientExchangeHandler extends Closeable, Cancellable {

    /**
     * Invoked to generate a HTTP request message head. The message is expected
     * to implement the {@link org.apache.http.HttpEntityEnclosingRequest} interface if it is
     * to enclose a content entity. The {@link #produceContent(ContentEncoder,
     * IOControl)} method will not be invoked if
     * {@link org.apache.http.HttpEntityEnclosingRequest#getEntity()} returns
     * {@code null}.
     *
     * @return HTTP request message.
     * @throws HttpException in case of HTTP protocol violation
     * @throws IOException in case of an I/O error
     */
    HttpRequest generateRequest() throws IOException, HttpException;

    /**
     * Invoked to write out a chunk of content to the {@link ContentEncoder}.
     * The {@link IOControl} interface can be used to suspend output event
     * notifications if the producer is temporarily unable to produce more content.
     * <p>
     * When all content is finished, the producer <b>MUST</b> call
     * {@link ContentEncoder#complete()}. Failure to do so may cause the entity
     * to be incorrectly delimited.
     * <p>
     * Please note that the {@link ContentEncoder} object is not thread-safe and
     * should only be used within the context of this method call.
     * The {@link IOControl} object can be shared and used on other thread
     * to resume output event notifications when more content is made available.
     *
     * @param encoder content encoder.
     * @param ioctrl I/O control of the underlying connection.
     * @throws IOException in case of an I/O error
     */
    void produceContent(ContentEncoder encoder, IOControl ioctrl) throws IOException;

    /**
     * Invoked to signal that the request has been fully written out.
     */
    void requestCompleted();

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
     */
    void responseCompleted() throws IOException, HttpException;

    /**
     * Invoked to signal that the connection has been terminated prematurely
     * by the opposite end.
     */
    void inputTerminated();

    /**
     * Invoked to signal that the response processing terminated abnormally.
     *
     * @param ex exception
     */
    void failed(Exception ex);

    /**
     * Determines whether or not the response processing completed.
     */
    boolean isDone();

}
