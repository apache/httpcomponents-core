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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.jackson2.JsonConsumer;
import org.apache.hc.core5.jackson2.JsonResultSink;
import org.apache.hc.core5.jackson2.JsonTokenEventHandler;

/**
 * Factory class for JSON {@link AsyncRequestConsumer}s.
 *
 * @since 5.5
 */
public final class JsonRequestConsumers {

    /**
     * Creates {@link AsyncRequestConsumer} that produces a {@link Message} object
     * consisting of the {@link HttpRequest} head and the de-serialized JSON body.
     *
     * @param objectMapper the object mapper to be used to de-serialize JSON content.
     * @param javaType     the java type of the de-serialized object.
     * @param <T>          the type of result objects produced by the consumer.
     * @return the request consumer.
     */
    public static <T> AsyncRequestConsumer<Message<HttpRequest, T>> create(
            final ObjectMapper objectMapper,
            final JavaType javaType) {
        return new JsonRequestConsumer<>(() -> new JsonObjectEntityConsumer<>(objectMapper, javaType));
    }

    /**
     * Creates {@link AsyncRequestConsumer} that produces a {@link Message} object
     * consisting of the {@link HttpRequest} head and the de-serialized JSON body.
     *
     * @param objectMapper the object mapper to be used to de-serialize JSON content.
     * @param objectClazz  the class of the de-serialized object.
     * @param <T>          the type of result objects produced by the consumer.
     * @return the request consumer.
     */
    public static <T> AsyncRequestConsumer<Message<HttpRequest, T>> create(
            final ObjectMapper objectMapper,
            final Class<T> objectClazz) {
        return new JsonRequestConsumer<>(() -> new JsonObjectEntityConsumer<>(objectMapper, objectClazz));
    }

    /**
     * Creates {@link AsyncRequestConsumer} that produces a {@link Message} object
     * consisting of the {@link HttpRequest} head and the de-serialized JSON body.
     *
     * @param objectMapper  the object mapper to be used to de-serialize JSON content.
     * @param typeReference the type reference of the de-serialized object.
     * @param <T>           the type of result objects produced by the consumer.
     * @return the request consumer.
     */
    public static <T> AsyncRequestConsumer<Message<HttpRequest, T>> create(
            final ObjectMapper objectMapper,
            final TypeReference<T> typeReference) {
        return new JsonRequestConsumer<>(() -> new JsonObjectEntityConsumer<>(objectMapper, typeReference));
    }

    /**
     * Creates {@link AsyncRequestConsumer} that produces a {@link Message} object
     * consisting of the {@link HttpRequest} head and the {@link JsonNode} body.
     *
     * @param jsonFactory JSON factory.
     * @return the request consumer.
     */
    public static AsyncRequestConsumer<Message<HttpRequest, JsonNode>> create(final JsonFactory jsonFactory) {
        return new JsonRequestConsumer<>(() -> new JsonNodeEntityConsumer(jsonFactory));
    }

    /**
     * Creates {@link AsyncRequestConsumer} that converts incoming HTTP request
     * into a sequence of instances of the given class and passes those objects
     * to the given {@link JsonResultSink}.
     *
     * @param objectMapper     the object mapper to be used to de-serialize JSON content.
     * @param javaType         the java type of the de-serialized object.
     * @param requestValidator optional operation that accepts the message head as input.
     * @param resultSink       the recipient of result objects.
     * @param <T>              type of result objects produced by the consumer.
     * @return the request consumer.
     */
    public static <T> AsyncRequestConsumer<Long> create(
            final ObjectMapper objectMapper,
            final JavaType javaType,
            final JsonConsumer<HttpRequest> requestValidator,
            final JsonResultSink<T> resultSink) {
        return new JsonSequenceRequestConsumer<>(
                () -> new JsonSequenceEntityConsumer<>(objectMapper, javaType, resultSink),
                requestValidator);
    }

    /**
     * Creates {@link AsyncRequestConsumer} that converts incoming HTTP request
     * into a sequence of instances of the given class and passes those objects
     * to the given {@link JsonResultSink}.
     *
     * @param objectMapper     the object mapper to be used to de-serialize JSON content.
     * @param objectClazz      the class of the de-serialized object.
     * @param requestValidator optional operation that accepts the message head as input.
     * @param resultSink       the recipient of result objects.
     * @param <T>              type of result objects produced by the consumer.
     * @return the request consumer.
     */
    public static <T> AsyncRequestConsumer<Long> create(
            final ObjectMapper objectMapper,
            final Class<T> objectClazz,
            final JsonConsumer<HttpRequest> requestValidator,
            final JsonResultSink<T> resultSink) {
        return new JsonSequenceRequestConsumer<>(
                () -> new JsonSequenceEntityConsumer<>(objectMapper, objectClazz, resultSink),
                requestValidator);
    }

    /**
     * Creates {@link AsyncRequestConsumer} that converts incoming HTTP request
     * into a sequence of instances of the given class and passes those objects
     * to the given {@link JsonResultSink}.
     *
     * @param objectMapper     the object mapper to be used to de-serialize JSON content.
     * @param typeReference    the type reference of the de-serialized object.
     * @param requestValidator optional operation that accepts the message head as input.
     * @param resultSink       the recipient of result objects.
     * @param <T>              type of result objects produced by the consumer.
     * @return the request consumer.
     */
    public static <T> AsyncRequestConsumer<Long> create(
            final ObjectMapper objectMapper,
            final TypeReference<T> typeReference,
            final JsonConsumer<HttpRequest> requestValidator,
            final JsonResultSink<T> resultSink) {
        return new JsonSequenceRequestConsumer<>(
                () -> new JsonSequenceEntityConsumer<>(objectMapper, typeReference, resultSink),
                requestValidator);
    }

    /**
     * Creates {@link AsyncRequestConsumer} that converts incoming HTTP request
     * into a sequence of {@link JsonNode} instances and passes those objects
     * to the given {@link JsonResultSink}.
     *
     * @param objectMapper     the object mapper to be used to de-serialize JSON content.
     * @param requestValidator optional operation that accepts the message head as input.
     * @param resultSink       the recipient of result objects.
     * @return the request consumer.
     */
    public static AsyncRequestConsumer<Long> create(
            final ObjectMapper objectMapper,
            final JsonConsumer<HttpRequest> requestValidator,
            final JsonResultSink<JsonNode> resultSink) {
        return new JsonSequenceRequestConsumer<>(
                () -> new JsonNodeSequenceEntityConsumer(objectMapper, resultSink),
                requestValidator);
    }

    /**
     * Creates {@link AsyncRequestConsumer} that converts incoming HTTP request
     * into a sequence of JSON tokens passed as events to the given {@link JsonTokenEventHandler}.
     *
     * @param jsonFactory      JSON factory.
     * @param requestValidator optional operation that accepts the message head as input.
     * @param eventHandler     JSON event handler
     * @return the request consumer.
     */
    public static AsyncRequestConsumer<Void> create(
            final JsonFactory jsonFactory,
            final JsonConsumer<HttpRequest> requestValidator,
            final JsonTokenEventHandler eventHandler) {
        return new JsonSequenceRequestConsumer<>(
                () -> new JsonTokenEntityConsumer(jsonFactory, eventHandler),
                requestValidator);
    }

}
