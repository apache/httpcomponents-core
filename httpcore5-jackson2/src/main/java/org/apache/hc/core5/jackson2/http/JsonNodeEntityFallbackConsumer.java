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
package org.apache.hc.core5.jackson2.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import org.apache.hc.core5.concurrent.CallbackContribution;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.util.Args;

/**
 * {@link org.apache.hc.core5.http.nio.AsyncEntityConsumer} implementation that
 * de-serializes incoming HTTP message entity into an {@link JsonNode} instance
 * if the message content type is {@link ContentType#APPLICATION_JSON} or as a single text
 * {@link JsonNode} if the message content type is not supported. This entity consumer
 * is primarily intended for handling of response messages that represent a client or a server
 * error (such as a response with 4xx or 5xx status).
 *
 * @since 5.5
 */
public class JsonNodeEntityFallbackConsumer implements AsyncEntityConsumer<JsonNode> {

    private final JsonFactory jsonFactory;
    private final AtomicReference<AsyncEntityConsumer<?>> entityConsumerRef;

    public JsonNodeEntityFallbackConsumer(final JsonFactory jsonFactory) {
        this.jsonFactory = Args.notNull(jsonFactory, "Json factory");
        this.entityConsumerRef = new AtomicReference<>();
    }

    public JsonNodeEntityFallbackConsumer(final ObjectMapper objectMapper) {
        this.jsonFactory = Args.notNull(objectMapper, "Object mapper").getFactory();
        this.entityConsumerRef = new AtomicReference<>();
    }

    @Override
    public void streamStart(final EntityDetails entityDetails,
                            final FutureCallback<JsonNode> resultCallback) throws HttpException, IOException {
        final ContentType contentType = ContentType.parseLenient(entityDetails.getContentType());
        if (contentType == null || ContentType.APPLICATION_JSON.isSameMimeType(contentType)) {
            final AsyncEntityConsumer<JsonNode> entityConsumer = new JsonNodeEntityConsumer(jsonFactory);
            entityConsumerRef.set(entityConsumer);
            entityConsumer.streamStart(entityDetails, new CallbackContribution<JsonNode>(resultCallback) {

                @Override
                public void completed(final JsonNode result) {
                    resultCallback.completed(result);
                }

            });
        } else {
            final AsyncEntityConsumer<String> entityConsumer = new StringAsyncEntityConsumer();
            entityConsumerRef.set(entityConsumer);
            entityConsumer.streamStart(entityDetails, new CallbackContribution<String>(resultCallback) {

                @Override
                public void completed(final String result) {
                    resultCallback.completed(JsonNodeFactory.instance.textNode(result));
                }

            });
        }
    }

    @Override
    public JsonNode getContent() {
        return null;
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        final AsyncEntityConsumer<?> entityConsumer = entityConsumerRef.get();
        if (entityConsumer != null) {
            entityConsumer.updateCapacity(capacityChannel);
        } else {
            capacityChannel.update(Integer.MAX_VALUE);
        }
    }

    @Override
    public void consume(final ByteBuffer data) throws IOException {
        final AsyncEntityConsumer<?> entityConsumer = entityConsumerRef.get();
        if (entityConsumer != null) {
            entityConsumer.consume(data);
        }
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        final AsyncEntityConsumer<?> entityConsumer = entityConsumerRef.get();
        if (entityConsumer != null) {
            entityConsumer.streamEnd(trailers);
        }
    }

    public void failed(final Exception cause) {
        final AsyncEntityConsumer<?> entityConsumer = entityConsumerRef.get();
        if (entityConsumer != null) {
            entityConsumer.failed(cause);
        }
        releaseResources();
    }

    @Override
    public void releaseResources() {
        final AsyncEntityConsumer<?> entityConsumer = entityConsumerRef.getAndSet(null);
        if (entityConsumer != null) {
            entityConsumer.releaseResources();
        }
    }

}
