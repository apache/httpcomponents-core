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

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.protocol.HttpContext;

/**
 * @since 4.2
 */
@ThreadSafe
public abstract class AbstractAsyncResponseConsumer<T> implements HttpAsyncResponseConsumer<T> {

    private volatile boolean completed;
    private volatile T result;
    private volatile Exception ex;

    public AbstractAsyncResponseConsumer() {
        super();
    }

    protected abstract void onResponseReceived(final HttpResponse response);

    protected abstract void onContentReceived(
            final ContentDecoder decoder, final IOControl ioctrl) throws IOException;

    protected abstract T buildResult(HttpContext context) throws Exception;

    protected abstract void releaseResources();

    public synchronized void responseReceived(final HttpResponse response) throws IOException, HttpException {
        onResponseReceived(response);
    }

    public synchronized void consumeContent(
            final ContentDecoder decoder, final IOControl ioctrl) throws IOException {
        onContentReceived(decoder, ioctrl);
    }

    public synchronized void responseCompleted(final HttpContext context) {
        if (this.completed) {
            return;
        }
        this.completed = true;
        try {
            this.result = buildResult(context);
        } catch (Exception ex) {
            this.ex = ex;
        } finally {
            releaseResources();
        }
    }

    public synchronized boolean cancel() {
        if (this.completed) {
            return false;
        }
        this.completed = true;
        releaseResources();
        return true;
    }

    public synchronized void failed(final Exception ex) {
        if (this.completed) {
            return;
        }
        this.completed = true;
        this.ex = ex;
        releaseResources();
    }

    public synchronized void close() {
        if (this.completed) {
            return;
        }
        this.completed = true;
        releaseResources();
    }

    public Exception getException() {
        return this.ex;
    }

    public T getResult() {
        return this.result;
    }

    public boolean isDone() {
        return this.completed;
    }

}
