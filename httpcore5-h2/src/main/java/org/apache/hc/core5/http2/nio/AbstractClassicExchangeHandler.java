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
package org.apache.hc.core5.http2.nio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.entity.ContentInputStream;
import org.apache.hc.core5.http.nio.entity.ContentOutputStream;
import org.apache.hc.core5.http2.nio.entity.SharedInputBuffer;
import org.apache.hc.core5.http2.nio.entity.SharedOutputBuffer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;

/**
 * @since 5.0
 */
public abstract class AbstractClassicExchangeHandler implements AsyncExchangeHandler {

    private enum State { IDLE, ACTIVE, COMPLETED }

    private final int initialBufferSize;
    private final Executor executor;
    private final AtomicReference<State> state;
    private final AtomicReference<Exception> exception;

    private volatile SharedInputBuffer inputBuffer;
    private volatile SharedOutputBuffer outputBuffer;

    public AbstractClassicExchangeHandler(final int initialBufferSize, final Executor executor) {
        this.initialBufferSize = Args.positive(initialBufferSize, "Initial buffer size");
        this.executor = Args.notNull(executor, "Executor");
        this.exception = new AtomicReference<>(null);
        this.state = new AtomicReference<>(State.IDLE);
    }

    public Exception getException() {
        return exception.get();
    }

    protected abstract void handle(
            HttpRequest request, InputStream requestStream,
            HttpResponse response, OutputStream responseStream) throws IOException, HttpException;

    @Override
    public final void handleRequest(
            final HttpRequest request,
            final boolean endStream,
            final ResponseChannel responseChannel) throws HttpException, IOException {

        final AtomicBoolean responseCommitted = new AtomicBoolean(false);

        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK) {

            private void ensureNotCommitted() {
                Asserts.check(!responseCommitted.get(), "Response already committed");
            }

            @Override
            public void addHeader(final String name, final Object value) {
                ensureNotCommitted();
                super.addHeader(name, value);
            }

            @Override
            public void setHeader(final String name, final Object value) {
                ensureNotCommitted();
                super.setHeader(name, value);
            }

            @Override
            public void setVersion(final ProtocolVersion version) {
                ensureNotCommitted();
                super.setVersion(version);
            }

            @Override
            public void setCode(final int code) {
                ensureNotCommitted();
                super.setCode(code);
            }

            @Override
            public void setReasonPhrase(final String reason) {
                ensureNotCommitted();
                super.setReasonPhrase(reason);
            }

            @Override
            public void setLocale(final Locale locale) {
                ensureNotCommitted();
                super.setLocale(locale);
            }

        };

        final InputStream inputStream;
        if (!endStream) {
            inputBuffer = new SharedInputBuffer(initialBufferSize);
            inputStream = new ContentInputStream(inputBuffer);
        } else {
            inputStream = null;
        }
        outputBuffer = new SharedOutputBuffer(initialBufferSize);

        final OutputStream outputStream = new ContentOutputStream(outputBuffer) {

            private void triggerResponse() throws IOException {
                try {
                    if (responseCommitted.compareAndSet(false, true)) {
                        responseChannel.sendResponse(response, false);
                    }
                } catch (HttpException ex) {
                    throw new IOException(ex.getMessage(), ex);
                }
            }

            @Override
            public void close() throws IOException {
                triggerResponse();
                super.close();
            }

            @Override
            public void write(final byte[] b, final int off, final int len) throws IOException {
                triggerResponse();
                super.write(b, off, len);
            }

            @Override
            public void write(final byte[] b) throws IOException {
                triggerResponse();
                super.write(b);
            }

            @Override
            public void write(final int b) throws IOException {
                triggerResponse();
                super.write(b);
            }

        };

        if (state.compareAndSet(State.IDLE, State.ACTIVE)) {
            executor.execute(new Runnable() {

                @Override
                public void run() {
                    try {
                        handle(request, inputStream, response, outputStream);
                        if (inputStream != null) {
                            inputStream.close();
                        }
                        outputStream.close();
                    } catch (Exception ex) {
                        if (inputBuffer != null) {
                            inputBuffer.abort();
                        }
                        outputBuffer.abort();
                    } finally {
                        state.set(State.COMPLETED);
                    }
                }

            });
        }
    }

    @Override
    public int capacity() {
        return inputBuffer != null ? inputBuffer.available() : 0;
    }

    @Override
    public final void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        if (inputBuffer != null) {
            inputBuffer.updateCapacity(capacityChannel);
        }
    }

    @Override
    public final void consume(final ByteBuffer src) throws IOException {
        Asserts.notNull(inputBuffer, "Input buffer");
        inputBuffer.fill(src);
    }

    @Override
    public final void streamEnd(final List<Header> trailers) throws HttpException, IOException {
        Asserts.notNull(inputBuffer, "Input buffer");
        inputBuffer.markEndStream();
    }

    @Override
    public int available() {
        Asserts.notNull(outputBuffer, "Output buffer");
        return outputBuffer.length();
    }

    @Override
    public final void produce(final DataStreamChannel channel) throws IOException {
        Asserts.notNull(outputBuffer, "Output buffer");
        outputBuffer.flush(channel);
    }

    @Override
    public void failed(final Exception cause) {
        exception.set(cause);
        releaseResources();
    }

    @Override
    public void releaseResources() {
    }

}
