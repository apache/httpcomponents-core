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
import org.apache.http.HttpResponse;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.entity.EntityAsyncContentProducer;
import org.apache.http.nio.entity.HttpAsyncContentProducer;
import org.apache.http.protocol.HttpContext;

/**
 * @since 4.2
 */
@ThreadSafe
public class BasicAsyncResponseProducer implements HttpAsyncResponseProducer {

    private final HttpResponse response;
    private final HttpAsyncContentProducer producer;

    protected BasicAsyncResponseProducer(
            final HttpResponse response,
            final HttpAsyncContentProducer producer) {
        super();
        if (response == null) {
            throw new IllegalArgumentException("HTTP response may not be null");
        }
        if (producer == null) {
            throw new IllegalArgumentException("HTTP content producer may not be null");
        }
        this.response = response;
        this.producer = producer;
    }

    public BasicAsyncResponseProducer(final HttpResponse response) {
        super();
        if (response == null) {
            throw new IllegalArgumentException("HTTP response may not be null");
        }
        this.response = response;
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            if (entity instanceof HttpAsyncContentProducer) {
                this.producer = (HttpAsyncContentProducer) entity;
            } else {
                this.producer = new EntityAsyncContentProducer(entity);
            }
        } else {
            this.producer = null;
        }
    }

    public synchronized HttpResponse generateResponse() {
        return this.response;
    }

    public synchronized void produceContent(
            final ContentEncoder encoder, final IOControl ioctrl) throws IOException {
        if (this.producer != null) {
            this.producer.produceContent(encoder, ioctrl);
            if (encoder.isCompleted()) {
                this.producer.close();
            }
        }
    }

    public void responseCompleted(final HttpContext context) {
    }

    public synchronized void close() throws IOException {
        if (this.producer != null) {
            this.producer.close();
        }
    }

}
