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

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.jackson2.JsonConsumer;
import org.apache.hc.core5.jackson2.JsonResultSink;
import org.apache.hc.core5.jackson2.JsonTokenConsumer;
import org.apache.hc.core5.jackson2.JsonTokenEventHandler;

/**
 * Factory class for JSON {@link AsyncResponseConsumer}s.
 */
public final class JsonResponseConsumers {

    /**
     * Creates {@link AsyncResponseConsumer} that produces a {@link Message} object
     * consisting of the {@link HttpResponse} head and the de-serialized JSON body.
     *
     * @param objectMapper          the object mapper to be used to de-serialize JSON content.
     * @param javaType              the java type of the de-serialized object.
     * @param errorConsumerSupplier supplier of the custom error consumer
     * @param <T>                   the type of result objects produced by the consumer.
     * @param <E>                   the type of error object.
     * @return the response consumer.
     */
    public static <T, E> AsyncResponseConsumer<Message<HttpResponse, T>> create(
            final ObjectMapper objectMapper,
            final JavaType javaType,
            final Supplier<AsyncEntityConsumer<E>> errorConsumerSupplier) {
        return new JsonResponseConsumer<>(
                () -> new JsonObjectEntityConsumer<>(objectMapper, javaType),
                errorConsumerSupplier);
    }

    /**
     * Creates {@link AsyncResponseConsumer} that produces a {@link Message} object
     * consisting of the {@link HttpResponse} head and the de-serialized JSON body.
     *
     * @param objectMapper the object mapper to be used to de-serialize JSON content.
     * @param javaType     the java type of the de-serialized object.
     * @param <T>          the type of result objects produced by the consumer.
     * @return the response consumer.
     */
    public static <T> AsyncResponseConsumer<Message<HttpResponse, T>> create(
            final ObjectMapper objectMapper,
            final JavaType javaType) {
        return create(objectMapper, javaType, StringAsyncEntityConsumer::new);
    }

    /**
     * Creates {@link AsyncResponseConsumer} that produces a {@link Message} object
     * consisting of the {@link HttpResponse} head and the de-serialized JSON body.
     *
     * @param objectMapper          the object mapper to be used to de-serialize JSON content.
     * @param objectClazz           the class of the de-serialized object.
     * @param errorConsumerSupplier supplier of the custom error consumer
     * @param <T>                   the type of result objects produced by the consumer.
     * @param <E>                   the type of error object.
     * @return the response consumer.
     */
    public static <T, E> AsyncResponseConsumer<Message<HttpResponse, T>> create(
            final ObjectMapper objectMapper,
            final Class<T> objectClazz,
            final Supplier<AsyncEntityConsumer<E>> errorConsumerSupplier) {
        return new JsonResponseConsumer<>(
                () -> new JsonObjectEntityConsumer<>(objectMapper, objectClazz),
                errorConsumerSupplier);
    }

    /**
     * Creates {@link AsyncResponseConsumer} that produces a {@link Message} object
     * consisting of the {@link HttpResponse} head and the de-serialized JSON body.
     *
     * @param objectMapper the object mapper to be used to de-serialize JSON content.
     * @param objectClazz  the class of the de-serialized object.
     * @param <T>          the type of result objects produced by the consumer.
     * @return the response consumer.
     */
    public static <T> AsyncResponseConsumer<Message<HttpResponse, T>> create(
            final ObjectMapper objectMapper,
            final Class<T> objectClazz) {
        return create(objectMapper, objectClazz, StringAsyncEntityConsumer::new);
    }

    /**
     * Creates {@link AsyncResponseConsumer} that produces a {@link Message} object
     * consisting of the {@link HttpResponse} head and the de-serialized JSON body.
     *
     * @param objectMapper          the object mapper to be used to de-serialize JSON content.
     * @param typeReference         the type reference of the de-serialized object.
     * @param errorConsumerSupplier supplier of the custom error consumer
     * @param <T>                   the type of result objects produced by the consumer.
     * @param <E>                   the type of error object.
     * @return the response consumer.
     */
    public static <T, E> AsyncResponseConsumer<Message<HttpResponse, T>> create(
            final ObjectMapper objectMapper,
            final TypeReference<T> typeReference,
            final Supplier<AsyncEntityConsumer<E>> errorConsumerSupplier) {
        return new JsonResponseConsumer<>(
                () -> new JsonObjectEntityConsumer<>(objectMapper, typeReference),
                errorConsumerSupplier);
    }

    /**
     * Creates {@link AsyncResponseConsumer} that produces a {@link Message} object
     * consisting of the {@link HttpResponse} head and the de-serialized JSON body.
     *
     * @param objectMapper  the object mapper to be used to de-serialize JSON content.
     * @param typeReference the type reference of the de-serialized object.
     * @param <T>           the type of result objects produced by the consumer.
     * @return the response consumer.
     */
    public static <T> AsyncResponseConsumer<Message<HttpResponse, T>> create(
            final ObjectMapper objectMapper,
            final TypeReference<T> typeReference) {
        return create(objectMapper, typeReference, StringAsyncEntityConsumer::new);
    }

    /**
     * Creates {@link AsyncResponseConsumer} that produces a {@link Message} object
     * consisting of the {@link HttpResponse} head and the {@link JsonNode} body.
     *
     * @param jsonFactory           JSON factory.
     * @param errorConsumerSupplier supplier of the custom error consumer
     * @param <E>                   the type of error object.
     * @return the response consumer.
     */
    public static <E> AsyncResponseConsumer<Message<HttpResponse, JsonNode>> create(
            final JsonFactory jsonFactory,
            final Supplier<AsyncEntityConsumer<E>> errorConsumerSupplier) {
        return new JsonResponseConsumer<>(
                () -> new JsonNodeEntityConsumer(jsonFactory),
                errorConsumerSupplier);
    }

    /**
     * Creates {@link AsyncResponseConsumer} that produces a {@link Message} object
     * consisting of the {@link HttpResponse} head and the {@link JsonNode} body.
     *
     * @param jsonFactory JSON factory.
     * @return the response consumer.
     */
    public static AsyncResponseConsumer<Message<HttpResponse, JsonNode>> create(final JsonFactory jsonFactory) {
        return create(jsonFactory, StringAsyncEntityConsumer::new);
    }

    /**
     * Creates {@link AsyncResponseConsumer} that converts incoming HTTP response
     * into a sequence of instances of the given class and passes those objects
     * to the given {@link JsonResultSink}.
     *
     * @param objectMapper          the object mapper to be used to de-serialize JSON content.
     * @param javaType              the java type of the de-serialized object.
     * @param errorConsumerSupplier supplier of the custom error consumer
     * @param responseValidator     optional operation that accepts the message head as input.
     * @param errorCallback         optional operation that accepts the error message as input in case of an error.
     * @param resultSink            the recipient of result objects.
     * @param <T>                   type of result objects produced by the consumer.
     * @param <E>                   the type of error object.
     * @return the response consumer.
     */
    public static <T, E> AsyncResponseConsumer<Long> create(
            final ObjectMapper objectMapper,
            final JavaType javaType,
            final Supplier<AsyncEntityConsumer<E>> errorConsumerSupplier,
            final JsonConsumer<HttpResponse> responseValidator,
            final Callback<E> errorCallback,
            final JsonResultSink<T> resultSink) {
        return new JsonSequenceResponseConsumer<>(
                () -> new JsonSequenceEntityConsumer<T>(objectMapper, javaType, resultSink),
                errorConsumerSupplier,
                responseValidator,
                errorCallback);
    }

    /**
     * Creates {@link AsyncResponseConsumer} that converts incoming HTTP response
     * into a sequence of instances of the given class and passes those objects
     * to the given {@link JsonResultSink}.
     *
     * @param objectMapper      the object mapper to be used to de-serialize JSON content.
     * @param javaType          the java type of the de-serialized object.
     * @param responseValidator optional operation that accepts the message head as input.
     * @param errorCallback     optional operation that accepts the error message as input in case of an error.
     * @param resultSink        the recipient of result objects.
     * @param <T>               type of result objects produced by the consumer.
     * @return the response consumer.
     */
    public static <T> AsyncResponseConsumer<Long> create(
            final ObjectMapper objectMapper,
            final JavaType javaType,
            final JsonConsumer<HttpResponse> responseValidator,
            final Callback<String> errorCallback,
            final JsonResultSink<T> resultSink) {
        return new JsonSequenceResponseConsumer<>(
                () -> new JsonSequenceEntityConsumer<T>(objectMapper, javaType, resultSink),
                StringAsyncEntityConsumer::new,
                responseValidator,
                errorCallback);
    }

    /**
     * Creates {@link AsyncResponseConsumer} that converts incoming HTTP response
     * into a sequence of instances of the given class and passes those objects
     * to the given {@link JsonResultSink}.
     *
     * @param objectMapper          the object mapper to be used to de-serialize JSON content.
     * @param objectClazz           the class of the de-serialized object.
     * @param errorConsumerSupplier supplier of the custom error consumer
     * @param responseValidator     optional operation that accepts the message head as input.
     * @param errorCallback         optional operation that accepts the error message as input in case of an error.
     * @param resultSink            the recipient of result objects.
     * @param <T>                   type of result objects produced by the consumer.
     * @param <E>                   the type of error object.
     * @return the response consumer.
     */
    public static <T, E> AsyncResponseConsumer<Long> create(
            final ObjectMapper objectMapper,
            final Class<T> objectClazz,
            final Supplier<AsyncEntityConsumer<E>> errorConsumerSupplier,
            final JsonConsumer<HttpResponse> responseValidator,
            final Callback<E> errorCallback,
            final JsonResultSink<T> resultSink) {
        return new JsonSequenceResponseConsumer<>(
                () -> new JsonSequenceEntityConsumer<>(objectMapper, objectClazz, resultSink),
                errorConsumerSupplier,
                responseValidator,
                errorCallback);
    }

    /**
     * Creates {@link AsyncResponseConsumer} that converts incoming HTTP response
     * into a sequence of instances of the given class and passes those objects
     * to the given {@link JsonResultSink}.
     *
     * @param objectMapper      the object mapper to be used to de-serialize JSON content.
     * @param objectClazz       the class of the de-serialized object.
     * @param responseValidator optional operation that accepts the message head as input.
     * @param errorCallback     optional operation that accepts the error message as input in case of an error.
     * @param resultSink        the recipient of result objects.
     * @param <T>               type of result objects produced by the consumer.
     * @return the response consumer.
     */
    public static <T> AsyncResponseConsumer<Long> create(
            final ObjectMapper objectMapper,
            final Class<T> objectClazz,
            final JsonConsumer<HttpResponse> responseValidator,
            final Callback<String> errorCallback,
            final JsonResultSink<T> resultSink) {
        return new JsonSequenceResponseConsumer<>(
                () -> new JsonSequenceEntityConsumer<>(objectMapper, objectClazz, resultSink),
                StringAsyncEntityConsumer::new,
                responseValidator,
                errorCallback);
    }

    /**
     * Creates {@link AsyncResponseConsumer} that converts incoming HTTP response
     * into a sequence of instances of the given class and passes those objects
     * to the given {@link JsonResultSink}.
     *
     * @param objectMapper          the object mapper to be used to de-serialize JSON content.
     * @param typeReference         the type reference of the de-serialized object.
     * @param errorConsumerSupplier supplier of the custom error consumer
     * @param responseValidator     optional operation that accepts the message head as input.
     * @param errorCallback         optional operation that accepts the error message as input in case of an error.
     * @param resultSink            the recipient of result objects.
     * @param <T>                   type of result objects produced by the consumer.
     * @param <E>                   the type of error object.
     * @return the response consumer.
     */
    public static <T, E> AsyncResponseConsumer<Long> create(
            final ObjectMapper objectMapper,
            final TypeReference<T> typeReference,
            final Supplier<AsyncEntityConsumer<E>> errorConsumerSupplier,
            final JsonConsumer<HttpResponse> responseValidator,
            final Callback<E> errorCallback,
            final JsonResultSink<T> resultSink) {
        return new JsonSequenceResponseConsumer<>(
                () -> new JsonSequenceEntityConsumer<>(objectMapper, typeReference, resultSink),
                errorConsumerSupplier,
                responseValidator,
                errorCallback);
    }

    /**
     * Creates {@link AsyncResponseConsumer} that converts incoming HTTP response
     * into a sequence of instances of the given class and passes those objects
     * to the given {@link JsonResultSink}.
     *
     * @param objectMapper      the object mapper to be used to de-serialize JSON content.
     * @param typeReference     the type reference of the de-serialized object.
     * @param responseValidator optional operation that accepts the message head as input.
     * @param errorCallback     optional operation that accepts the error message as input in case of an error.
     * @param resultSink        the recipient of result objects.
     * @param <T>               type of result objects produced by the consumer.
     * @return the response consumer.
     */
    public static <T> AsyncResponseConsumer<Long> create(
            final ObjectMapper objectMapper,
            final TypeReference<T> typeReference,
            final JsonConsumer<HttpResponse> responseValidator,
            final Callback<String> errorCallback,
            final JsonResultSink<T> resultSink) {
        return new JsonSequenceResponseConsumer<>(
                () -> new JsonSequenceEntityConsumer<>(objectMapper, typeReference, resultSink),
                StringAsyncEntityConsumer::new,
                responseValidator,
                errorCallback);
    }

    /**
     * Creates {@link AsyncResponseConsumer} that converts incoming HTTP response
     * into a sequence of JSON tokens passed as events to the given {@link JsonTokenEventHandler}.
     *
     * @param jsonFactory           JSON factory.
     * @param errorConsumerSupplier supplier of the custom error consumer
     * @param responseValidator     optional operation that accepts the message head as input.
     * @param errorCallback         optional operation that accepts the error message as input in case of an error.
     * @param eventHandler          JSON event handler
     * @param <E>                   the type of error object.
     * @return the response consumer.
     */
    public static <E> AsyncResponseConsumer<Void> create(
            final JsonFactory jsonFactory,
            final Supplier<AsyncEntityConsumer<E>> errorConsumerSupplier,
            final JsonConsumer<HttpResponse> responseValidator,
            final Callback<E> errorCallback,
            final JsonTokenEventHandler eventHandler) {
        return new JsonSequenceResponseConsumer<>(
                () -> new JsonTokenEntityConsumer(jsonFactory, eventHandler),
                errorConsumerSupplier,
                responseValidator,
                errorCallback);
    }

    /**
     * Creates {@link AsyncResponseConsumer} that converts incoming HTTP response
     * into a sequence of JSON tokens passed as events to the given {@link JsonTokenEventHandler}.
     *
     * @param jsonFactory       JSON factory.
     * @param responseValidator optional operation that accepts the message head as input.
     * @param errorCallback     optional operation that accepts the error message as input in case of an error.
     * @param eventHandler      JSON event handler
     * @return the response consumer.
     */
    public static AsyncResponseConsumer<Void> create(
            final JsonFactory jsonFactory,
            final JsonConsumer<HttpResponse> responseValidator,
            final Callback<String> errorCallback,
            final JsonTokenEventHandler eventHandler) {
        return new JsonSequenceResponseConsumer<>(
                () -> new JsonTokenEntityConsumer(jsonFactory, eventHandler),
                StringAsyncEntityConsumer::new,
                responseValidator,
                errorCallback);
    }

    /**
     * Creates {@link AsyncRequestConsumer} that converts incoming HTTP response
     * into a sequence of {@link JsonNode} instances and passes those objects
     * to the given {@link JsonResultSink}.
     *
     * @param objectMapper          the object mapper to be used to de-serialize JSON content.
     * @param errorConsumerSupplier supplier of the custom error consumer
     * @param responseValidator     optional operation that accepts the message head as input.
     * @param errorCallback         optional operation that accepts the error message as input in case of an error.
     * @param resultSink            the recipient of result objects.
     * @param <E>                   the type of error object.
     * @return the request consumer.
     */
    public static <E> AsyncResponseConsumer<Long> create(
            final ObjectMapper objectMapper,
            final Supplier<AsyncEntityConsumer<E>> errorConsumerSupplier,
            final JsonConsumer<HttpResponse> responseValidator,
            final Callback<E> errorCallback,
            final JsonResultSink<JsonNode> resultSink) {
        return new JsonSequenceResponseConsumer<>(
                () -> new JsonNodeSequenceEntityConsumer(objectMapper, resultSink),
                errorConsumerSupplier,
                responseValidator,
                errorCallback);
    }

    /**
     * Creates {@link AsyncRequestConsumer} that converts incoming HTTP response
     * into a sequence of {@link JsonNode} instances and passes those objects
     * to the given {@link JsonResultSink}.
     *
     * @param objectMapper      the object mapper to be used to de-serialize JSON content.
     * @param responseValidator optional operation that accepts the message head as input.
     * @param errorCallback     optional operation that accepts the error message as input in case of an error.
     * @param resultSink        the recipient of result objects.
     * @return the request consumer.
     */
    public static AsyncResponseConsumer<Long> create(
            final ObjectMapper objectMapper,
            final JsonConsumer<HttpResponse> responseValidator,
            final Callback<String> errorCallback,
            final JsonResultSink<JsonNode> resultSink) {
        return new JsonSequenceResponseConsumer<>(
                () -> new JsonNodeSequenceEntityConsumer(objectMapper, resultSink),
                StringAsyncEntityConsumer::new,
                responseValidator,
                errorCallback);
    }

    /**
     * Creates {@link AsyncResponseConsumer} that converts incoming HTTP response
     * into a sequence of JSON tokens passed as events to the given {@link JsonTokenConsumer}.
     *
     * @param jsonFactory           JSON factory.
     * @param errorConsumerSupplier supplier of the custom error consumer
     * @param responseValidator     optional operation that accepts the message head as input.
     * @param errorCallback         optional operation that accepts the error message as input in case of an error.
     * @param tokenConsumer         JSON token Consumer
     * @param <E>                   the type of error object.
     * @return the response consumer.
     */
    public static <E> AsyncResponseConsumer<Void> create(
            final JsonFactory jsonFactory,
            final Supplier<AsyncEntityConsumer<E>> errorConsumerSupplier,
            final JsonConsumer<HttpResponse> responseValidator,
            final Callback<E> errorCallback,
            final JsonTokenConsumer tokenConsumer) {
        return new JsonSequenceResponseConsumer<>(
                () -> new JsonTokenEntityConsumer(jsonFactory, tokenConsumer),
                errorConsumerSupplier,
                responseValidator,
                errorCallback);
    }

    /**
     * Creates {@link AsyncResponseConsumer} that converts incoming HTTP response
     * into a sequence of JSON tokens passed as events to the given {@link JsonTokenConsumer}.
     *
     * @param jsonFactory       JSON factory.
     * @param responseValidator optional operation that accepts the message head as input.
     * @param errorCallback     optional operation that accepts the error message as input in case of an error.
     * @param tokenConsumer     JSON token Consumer
     * @return the response consumer.
     */
    public static <E> AsyncResponseConsumer<Void> create(
            final JsonFactory jsonFactory,
            final JsonConsumer<HttpResponse> responseValidator,
            final Callback<String> errorCallback,
            final JsonTokenConsumer tokenConsumer) {
        return new JsonSequenceResponseConsumer<>(
                () -> new JsonTokenEntityConsumer(jsonFactory, tokenConsumer),
                StringAsyncEntityConsumer::new,
                responseValidator,
                errorCallback);
    }

}
