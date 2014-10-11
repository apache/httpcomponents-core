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
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.entity.EntityAsyncContentProducer;
import org.apache.http.nio.entity.HttpAsyncContentProducer;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Args;

/**
 * Basic implementation of {@link HttpAsyncResponseProducer}. The producer
 * can make use of the {@link HttpAsyncContentProducer} interface to
 * efficiently stream out message content to the underlying non-blocking HTTP
 * connection, if it is implemented by the {@link HttpEntity} inclosed in
 * the response.
 *
 * @see HttpAsyncContentProducer
 *
 * @since 4.2
 */
public class BasicAsyncResponseProducer implements HttpAsyncResponseProducer {

    private final HttpResponse response;
    private final HttpAsyncContentProducer producer;

    /**
     * Creates a producer that can be used to transmit the given response
     * message. The given content producer will be used to stream out message
     * content. Please note that the response message is expected to enclose
     * an {@link HttpEntity} whose properties are consistent with the behavior
     * of the content producer.
     *
     * @param response response message.
     * @param producer response content producer.
     */
    protected BasicAsyncResponseProducer(
            final HttpResponse response,
            final HttpAsyncContentProducer producer) {
        super();
        Args.notNull(response, "HTTP response");
        Args.notNull(producer, "HTTP content producer");
        this.response = response;
        this.producer = producer;
    }

    /**
     * Creates a producer that can be used to transmit the given response
     * message. If the response message encloses an {@link HttpEntity}
     * it is also expected to implement {@link HttpAsyncContentProducer}.
     *
     * @param response response message.
     */
    public BasicAsyncResponseProducer(final HttpResponse response) {
        super();
        Args.notNull(response, "HTTP response");
        this.response = response;
        final HttpEntity entity = response.getEntity();
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

    @Override
    public HttpResponse generateResponse() {
        return this.response;
    }

    @Override
    public void produceContent(
            final ContentEncoder encoder, final IOControl ioctrl) throws IOException {
        if (this.producer != null) {
            this.producer.produceContent(encoder, ioctrl);
            if (encoder.isCompleted()) {
                this.producer.close();
            }
        }
    }

    @Override
    public void responseCompleted(final HttpContext context) {
    }

    @Override
    public void failed(final Exception ex) {
    }

    @Override
    public void close() throws IOException {
        if (this.producer != null) {
            this.producer.close();
        }
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append(this.response);
        if (this.producer != null) {
            buf.append(" ").append(this.producer);
        }
        return buf.toString();
    }

}
