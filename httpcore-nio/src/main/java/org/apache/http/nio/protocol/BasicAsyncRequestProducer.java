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

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.entity.ProducingNHttpEntity;
import org.apache.http.protocol.HttpContext;

/**
 * @since 4.2
 */
@ThreadSafe
public class BasicAsyncRequestProducer implements HttpAsyncRequestProducer {

    private final HttpHost target;
    private final HttpRequest request;
    private final ProducingNHttpEntity producer;

    protected BasicAsyncRequestProducer(
            final HttpHost target,
            final HttpEntityEnclosingRequest request,
            final ProducingNHttpEntity producer) {
        super();
        if (target == null) {
            throw new IllegalArgumentException("HTTP host may not be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        request.setEntity(producer);
        this.target = target;
        this.request = request;
        this.producer = producer;
    }

    public BasicAsyncRequestProducer(final HttpHost target, final HttpRequest request) {
        if (target == null) {
            throw new IllegalArgumentException("HTTP host may not be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        this.target = target;
        this.request = request;
        if (request instanceof HttpEntityEnclosingRequest) {
            HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
            if (entity instanceof ProducingNHttpEntity) {
                this.producer = (ProducingNHttpEntity) entity;
            } else {
                this.producer = new NHttpEntityWrapper(entity);
            }
        } else {
            this.producer = null;
        }
    }

    public synchronized HttpRequest generateRequest() {
        return this.request;
    }

    public HttpHost getTarget() {
        return this.target;
    }

    public synchronized void produceContent(
            final ContentEncoder encoder, final IOControl ioctrl) throws IOException {
        if (this.producer != null) {
            this.producer.produceContent(encoder, ioctrl);
            if (encoder.isCompleted()) {
                this.producer.finish();
            }
        }
    }

    public void requestCompleted(final HttpContext context) {
    }

    public synchronized boolean isRepeatable() {
        return this.producer == null || this.producer.isRepeatable();
    }

    public synchronized void resetRequest() {
        if (this.producer != null) {
            try {
                this.producer.finish();
            } catch (IOException ignore) {
            }
        }
    }

    public synchronized void close() throws IOException {
        if (this.producer != null) {
            this.producer.finish();
        }
    }

}
