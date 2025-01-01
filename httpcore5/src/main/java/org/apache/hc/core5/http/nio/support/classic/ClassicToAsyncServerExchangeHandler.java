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
package org.apache.hc.core5.http.nio.support.classic;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestMapper;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.impl.io.support.IncomingHttpEntity;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.io.support.BasicHttpServerRequestHandler;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.nio.AsyncResponseProducer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.nio.support.BasicResponseProducer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.support.BasicResponseBuilder;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;

/**
 * {@link AsyncServerExchangeHandler} implementation that acts as a compatibility
 * layer for classic {@link InputStream} / {@link OutputStream} based interfaces.
 * Blocking input / output processing is executed through an {@link Executor}.
 *
 * @since 5.4
 */
@Experimental
public class ClassicToAsyncServerExchangeHandler implements AsyncServerExchangeHandler {

    private final int initialBufferSize;
    private final Executor executor;
    private final HttpServerRequestHandler requestHandler;
    private final Callback<Exception> exceptionCallback;
    private final AtomicBoolean responseCommitted;
    private final AtomicReference<AsyncResponseProducer> responseProducerRef;
    private final AtomicReference<SharedInputBuffer> inputBufferRef;
    private final AtomicReference<SharedOutputBuffer> outputBufferRef;
    private final AtomicReference<Exception> exceptionRef;

    public ClassicToAsyncServerExchangeHandler(
            final int initialBufferSize,
            final Executor executor,
            final HttpServerRequestHandler requestHandler,
            final Callback<Exception> exceptionCallback) {
        this.initialBufferSize = Args.positive(initialBufferSize, "Initial buffer size");
        this.executor = Args.notNull(executor, "Executor");
        this.requestHandler = Args.notNull(requestHandler, "Request handler");
        this.exceptionCallback = exceptionCallback;
        this.responseCommitted = new AtomicBoolean();
        this.responseProducerRef = new AtomicReference<>();
        this.inputBufferRef = new AtomicReference<>();
        this.outputBufferRef = new AtomicReference<>();
        this.exceptionRef = new AtomicReference<>();
    }

    public ClassicToAsyncServerExchangeHandler(
            final Executor executor,
            final HttpServerRequestHandler requestHandler,
            final Callback<Exception> exceptionCallback) {
        this(ClassicToAsyncSupport.INITIAL_BUF_SIZE, executor, requestHandler, exceptionCallback);
    }

    public ClassicToAsyncServerExchangeHandler(
            final Executor executor,
            final HttpRequestMapper<HttpRequestHandler> handlerMapper,
            final Callback<Exception> exceptionCallback) {
        this(ClassicToAsyncSupport.INITIAL_BUF_SIZE, executor,
                new BasicHttpServerRequestHandler(handlerMapper),
                exceptionCallback);
    }

    public ClassicToAsyncServerExchangeHandler(
            final Executor executor,
            final HttpRequestHandler handler,
            final Callback<Exception> exceptionCallback) {
        this(ClassicToAsyncSupport.INITIAL_BUF_SIZE, executor,
                new BasicHttpServerRequestHandler((request, context) -> handler),
                exceptionCallback);
    }

    void propagateException() throws IOException {
        final Exception ex = exceptionRef.getAndSet(null);
        if (ex != null) {
            ClassicToAsyncSupport.rethrow(ex);
        }
    }

    SharedInputBuffer inputBuffer() {
        final SharedInputBuffer inputBuffer = inputBufferRef.get();
        Asserts.notNull(inputBuffer, "Input buffer");
        return inputBuffer;
    }

    SharedOutputBuffer outputBuffer() {
        final SharedOutputBuffer outputBuffer = outputBufferRef.get();
        Asserts.notNull(outputBuffer, "Output buffer");
        return outputBuffer;
    }

    void abortInput() {
        final SharedInputBuffer inputBuffer = inputBufferRef.get();
        if (inputBuffer != null) {
            inputBuffer.abort();
        }
    }

    void abortOutput() {
        final SharedOutputBuffer outputBuffer = outputBufferRef.get();
        if (outputBuffer != null) {
            outputBuffer.abort();
        }
    }

    @Override
    public final void handleRequest(
            final HttpRequest request,
            final EntityDetails entityDetails,
            final ResponseChannel responseChannel,
            final HttpContext context) throws HttpException, IOException {
        if (entityDetails != null) {
            final SharedInputBuffer inputBuffer = new SharedInputBuffer(initialBufferSize);
            inputBufferRef.set(inputBuffer);
        }
        executor.execute(() -> {
            try {
                final ClassicHttpRequest cr = ClassicRequestBuilder.copy(request).build();
                if (entityDetails != null) {
                    cr.setEntity(new IncomingHttpEntity(
                            new InternalInputStream(inputBufferRef.get()),
                            entityDetails.getContentLength(),
                            request));
                }

                final HttpServerRequestHandler.ResponseTrigger trigger = new HttpServerRequestHandler.ResponseTrigger() {

                    @Override
                    public void sendInformation(final ClassicHttpResponse response) throws HttpException, IOException {
                        responseChannel.sendInformation(response, context);
                    }

                    @Override
                    public void submitResponse(final ClassicHttpResponse response) throws HttpException, IOException {
                        if (responseCommitted.compareAndSet(false, true)) {
                            final HttpEntity responseEntity = response.getEntity();
                            final String method = request.getMethod();
                            final boolean contentExpected = responseEntity != null && !Method.HEAD.isSame(method);
                            if (contentExpected) {
                                final SharedOutputBuffer outputBuffer = new SharedOutputBuffer(initialBufferSize);
                                outputBufferRef.set(outputBuffer);
                            }
                            responseChannel.sendResponse(response, responseEntity, null);
                            if (contentExpected) {
                                responseEntity.writeTo(new InternalOutputStream(outputBufferRef.get()));
                            }
                        } else {
                            throw new IllegalStateException("Response has already been committed");
                        }
                    }

                };
                try {
                    requestHandler.handle(cr, trigger, context);
                } catch (HttpException | RuntimeException ex) {
                    if (responseCommitted.compareAndSet(false, true)) {
                        final AsyncResponseProducer responseProducer = handleError(ex);
                        responseProducerRef.set(responseProducer);
                        responseProducer.sendResponse(responseChannel, context);
                    } else {
                        throw ex;
                    }
                }
            } catch (final Exception ex) {
                if (exceptionCallback != null) {
                    exceptionCallback.execute(ex);
                }
                responseChannel.terminateExchange();
            }
        });
    }

    protected AsyncResponseProducer handleError(final Exception ex) {
        final int status = (ex instanceof ProtocolException) ? HttpStatus.SC_BAD_REQUEST : HttpStatus.SC_INTERNAL_SERVER_ERROR;
        return new BasicResponseProducer(
                BasicResponseBuilder.create(status).build(),
                ex.getMessage(),
                ContentType.TEXT_PLAIN);
    }

    @Override
    public final void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        inputBuffer().updateCapacity(capacityChannel);
    }

    @Override
    public final void consume(final ByteBuffer src) throws IOException {
        inputBuffer().fill(src);
    }

    @Override
    public final void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        inputBuffer().markEndStream();
    }

    @Override
    public final int available() {
        final AsyncResponseProducer responseProducer = responseProducerRef.get();
        if (responseProducer != null) {
            return responseProducer.available();
        } else {
            return outputBuffer().length();
        }
    }

    @Override
    public final void produce(final DataStreamChannel channel) throws IOException {
        final AsyncResponseProducer responseProducer = responseProducerRef.get();
        if (responseProducer != null) {
            responseProducer.produce(channel);
        } else {
            outputBuffer().flush(channel);
        }
    }

    @Override
    public final void failed(final Exception cause) {
        responseCommitted.set(true);
        exceptionRef.compareAndSet(null, cause);
        abortInput();
        abortOutput();
    }

    @Override
    public void releaseResources() {
    }

    class InternalInputStream extends InputStream {

        private final ContentInputBuffer buffer;

        InternalInputStream(final ContentInputBuffer buffer) {
            super();
            Args.notNull(buffer, "Input buffer");
            this.buffer = buffer;
        }

        @Override
        public int available() throws IOException {
            propagateException();
            return buffer.length();
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            propagateException();
            if (len == 0) {
                return 0;
            }
            return buffer.read(b, off, len);
        }

        @Override
        public int read(final byte[] b) throws IOException {
            propagateException();
            if (b == null) {
                return 0;
            }
            return buffer.read(b, 0, b.length);
        }

        @Override
        public int read() throws IOException {
            propagateException();
            return buffer.read();
        }

        @Override
        public void close() throws IOException {
            propagateException();
            // read and discard the remainder of the message
            final byte[] tmp = new byte[1024];
            do {
                /* empty */
            } while (read(tmp) >= 0);
            super.close();
        }

    }

    class InternalOutputStream extends OutputStream {

        private final SharedOutputBuffer buffer;

        public InternalOutputStream(final SharedOutputBuffer buffer) {
            Asserts.notNull(buffer, "Shared buffer");
            this.buffer = buffer;
        }

        @Override
        public void close() throws IOException {
            propagateException();
            buffer.writeCompleted();
        }

        @Override
        public void flush() throws IOException {
            propagateException();
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            propagateException();
            buffer.write(b, off, len);
        }

        @Override
        public void write(final byte[] b) throws IOException {
            propagateException();
            if (b == null) {
                return;
            }
            buffer.write(b, 0, b.length);
        }

        @Override
        public void write(final int b) throws IOException {
            propagateException();
            buffer.write(b);
        }

    }

}
