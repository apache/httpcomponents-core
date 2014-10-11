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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.protocol.HttpContext;

/**
 * Abstract {@link HttpAsyncRequestConsumer} implementation that relieves its
 * subclasses from having to manage internal state and provides a number of protected
 * event methods that they need to implement.
 *
 * @since 4.2
 */
public abstract class AbstractAsyncRequestConsumer<T> implements HttpAsyncRequestConsumer<T> {

    private final AtomicBoolean completed;

    private volatile T result;
    private volatile Exception ex;

    public AbstractAsyncRequestConsumer() {
        super();
        this.completed = new AtomicBoolean(false);
    }

    /**
     * Invoked when a HTTP request message is received. Please note
     * that the {@link #onContentReceived(ContentDecoder, IOControl)} method
     * will be invoked only for if the request message implements
     * {@link HttpEntityEnclosingRequest} interface and has a content
     * entity enclosed.
     *
     * @param request HTTP request message.
     * @throws HttpException in case of HTTP protocol violation
     * @throws IOException in case of an I/O error
     */
    protected abstract void onRequestReceived(
            HttpRequest request) throws HttpException, IOException;

    /**
     * Invoked if the request message encloses a content entity.
     *
     * @param entity HTTP entity
     * @param contentType expected content type.
     * @throws IOException in case of an I/O error
     */
    protected abstract void onEntityEnclosed(
            HttpEntity entity, ContentType contentType) throws IOException;

    /**
     * Invoked to process a chunk of content from the {@link ContentDecoder}.
     * The {@link IOControl} interface can be used to suspend input events
     * if the consumer is temporarily unable to consume more content.
     * <p>
     * The consumer can use the {@link ContentDecoder#isCompleted()} method
     * to find out whether or not the message content has been fully consumed.
     *
     * @param decoder content decoder.
     * @param ioctrl I/O control of the underlying connection.
     * @throws IOException in case of an I/O error
     */
    protected abstract void onContentReceived(
            ContentDecoder decoder, IOControl ioctrl) throws IOException;

    /**
     * Invoked to generate a result object from the received HTTP request
     * message.
     *
     * @param context HTTP context.
     * @return result of the request processing.
     * @throws Exception in case of an abnormal termination.
     */
    protected abstract T buildResult(HttpContext context) throws Exception;

    /**
     * Invoked to release all system resources currently allocated.
     */
    protected abstract void releaseResources();

    /**
     * Invoked when the consumer is being closed.
     * @throws IOException may be thrown by subclassses
     *
     * @since 4.3
     */
    protected void onClose() throws IOException {
    }

    /**
     * Use {@link #onRequestReceived(HttpRequest)} instead.
     */
    @Override
    public final void requestReceived(
            final HttpRequest request) throws HttpException, IOException {
        onRequestReceived(request);
        if (request instanceof HttpEntityEnclosingRequest) {
            final HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
            if (entity != null) {
                final ContentType contentType = ContentType.getOrDefault(entity);
                onEntityEnclosed(entity, contentType);
            }
        }
    }

    /**
     * Use {@link #onContentReceived(ContentDecoder, IOControl)} instead.
     */
    @Override
    public final void consumeContent(
            final ContentDecoder decoder, final IOControl ioctrl) throws IOException {
        onContentReceived(decoder, ioctrl);
    }

    /**
     * Use {@link #buildResult(HttpContext)} instead.
     */
    @Override
    public final void requestCompleted(final HttpContext context) {
        if (this.completed.compareAndSet(false, true)) {
            try {
                this.result = buildResult(context);
            } catch (final Exception ex) {
                this.ex = ex;
            } finally {
                releaseResources();
            }
        }
    }

    @Override
    public final void failed(final Exception ex) {
        if (this.completed.compareAndSet(false, true)) {
            this.ex = ex;
            releaseResources();
        }
    }

    @Override
    public final void close() throws IOException {
        if (this.completed.compareAndSet(false, true)) {
            releaseResources();
            onClose();
        }
    }

    @Override
    public Exception getException() {
        return this.ex;
    }

    @Override
    public T getResult() {
        return this.result;
    }

    @Override
    public boolean isDone() {
        return this.completed.get();
    }

}
