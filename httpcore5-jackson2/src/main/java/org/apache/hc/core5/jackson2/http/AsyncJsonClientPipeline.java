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

import java.net.URI;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HandlerResolver;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.support.AbstractClientExchangeHandler;
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.apache.hc.core5.jackson2.JsonConsumer;
import org.apache.hc.core5.jackson2.JsonResultSink;
import org.apache.hc.core5.jackson2.JsonTokenEventHandler;
import org.apache.hc.core5.util.Args;

/**
 * Client side execution pipeline assembler that creates {@link AsyncClientExchangeHandler} instances
 * with the defined message exchange pipeline optimized for JSON message exchanges that triggers
 * the given {@link FutureCallback} or {@link CompletableFuture} upon completion.
 * <p>
 * Please note that {@link AsyncClientExchangeHandler} are stateful and may not be used concurrently
 * by multiple message exchanges or re-used for subsequent message exchanges.
 *
 * @since 5.5
 */
public final class AsyncJsonClientPipeline {

    private static final String JSON_CONTENT_TYPE_TEXT = ContentType.APPLICATION_JSON.toString();
    private final ObjectMapper objectMapper;

    private AsyncJsonClientPipeline(final ObjectMapper objectMapper) {
        this.objectMapper = Args.notNull(objectMapper, "Object mapper");
    }

    public static AsyncJsonClientPipeline assemble(final ObjectMapper objectMapper) {
        return new AsyncJsonClientPipeline(objectMapper);
    }

    public RequestStage request() {
        return new RequestStage();
    }

    /**
     * Configures the pipeline to produce an outgoing message stream with the given
     * request head.
     */
    public RequestContentStage request(final HttpRequest request) {
        return new RequestContentStage(request);
    }

    /**
     * Request message stage.
     */
    public class RequestStage {

        private RequestStage() {
        }

        /**
         * Configures {@link AsyncRequestProducer} to be used by the pipeline to generate the outgoing
         * request message stream.
         */
        public ResponseStage produce(final AsyncRequestProducer requestProducer) {
            return new ResponseStage(requestProducer);
        }

        /**
         * Configures the pipeline to produce an outgoing GET message stream.
         */
        public ResponseStage get(final URI requestUri) {
            return new ResponseStage(AsyncRequestBuilder.get(requestUri)
                    .addHeader(HttpHeaders.ACCEPT, JSON_CONTENT_TYPE_TEXT)
                    .build());
        }

        /**
         * Configures the pipeline to produce an outgoing GET message stream.
         */
        public ResponseStage get(final HttpHost target, final String path) {
            return new ResponseStage(AsyncRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath(path)
                    .addHeader(HttpHeaders.ACCEPT, JSON_CONTENT_TYPE_TEXT)
                    .build());
        }

        /**
         * Configures the pipeline to produce an outgoing POST message stream.
         */
        public RequestContentStage post(final URI requestUri) {
            return new RequestContentStage(BasicRequestBuilder.post(requestUri)
                    .addHeader(HttpHeaders.ACCEPT, JSON_CONTENT_TYPE_TEXT)
                    .build());
        }

        /**
         * Configures the pipeline to produce an outgoing POST message stream.
         */
        public RequestContentStage post(final HttpHost target, final String path) {
            return new RequestContentStage(BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath(path)
                    .addHeader(HttpHeaders.ACCEPT, JSON_CONTENT_TYPE_TEXT)
                    .build());
        }

        /**
         * Configures the pipeline to produce an outgoing PUT message stream.
         */
        public RequestContentStage put(final URI requestUri) {
            return new RequestContentStage(BasicRequestBuilder.put(requestUri)
                    .addHeader(HttpHeaders.ACCEPT, JSON_CONTENT_TYPE_TEXT)
                    .build());
        }

        /**
         * Configures the pipeline to produce an outgoing PUT message stream.
         */
        public RequestContentStage put(final HttpHost target, final String path) {
            return new RequestContentStage(BasicRequestBuilder.put()
                    .setHttpHost(target)
                    .setPath(path)
                    .addHeader(HttpHeaders.ACCEPT, JSON_CONTENT_TYPE_TEXT)
                    .build());
        }

        /**
         * Configures the pipeline to produce an outgoing PATCH message stream.
         */
        public RequestContentStage patch(final URI requestUri) {
            return new RequestContentStage(BasicRequestBuilder.patch(requestUri)
                    .addHeader(HttpHeaders.ACCEPT, JSON_CONTENT_TYPE_TEXT)
                    .build());
        }

        /**
         * Configures the pipeline to produce an outgoing PATCH message stream.
         */
        public RequestContentStage patch(final HttpHost target, final String path) {
            return new RequestContentStage(BasicRequestBuilder.patch()
                    .setHttpHost(target)
                    .setPath(path)
                    .addHeader(HttpHeaders.ACCEPT, JSON_CONTENT_TYPE_TEXT)
                    .build());
        }

    }

    /**
     * Request content stage.
     */
    public class RequestContentStage {

        private final HttpRequest request;

        private RequestContentStage(final HttpRequest request) {
            this.request = request;
        }

        /**
         * Configures {@link AsyncEntityProducer} to be used by the pipeline to generate the outgoing
         * request content stream.
         */
        public ResponseStage produceContent(final AsyncEntityProducer dataProducer) {
            return new ResponseStage(new BasicRequestProducer(request, dataProducer));
        }

        /**
         * Configures the pipeline to represent the outgoing message content as a byte array.
         */
        public <T> ResponseStage asObject(final T content) {
            return new ResponseStage(JsonRequestProducers.create(request, content, objectMapper));
        }

        /**
         * Configures the pipeline to represent the outgoing message content as a byte array.
         */
        public ResponseStage asJsonNode(final JsonNode content) {
            return new ResponseStage(JsonRequestProducers.create(request, content, objectMapper));
        }

        /**
         * Configures the pipeline to represent the outgoing message content as a sequence
         * of objects.
         */
        public <T> ResponseStage asSequence(final ObjectProducer<T> objectProducer) {
            return new ResponseStage(JsonRequestProducers.create(request, objectMapper, objectProducer));
        }

        /**
         * Configures the pipeline to represent the outgoing message without a content body.
         */
        public ResponseStage noContent() {
            return produceContent(null);
        }

    }

    /**
     * Response message stage.
     */
    public class ResponseStage {

        private final AsyncRequestProducer requestProducer;

        private ResponseStage(final AsyncRequestProducer requestProducer) {
            this.requestProducer = requestProducer;
        }

        /**
         * Configures the pipeline to processes the incoming response message stream.
         */
        public ResponseContentStage response() {
            return new ResponseContentStage(requestProducer);
        }

    }

    /**
     * Response content generation stage.
     */
    public class ResponseContentStage {

        private final AsyncRequestProducer requestProducer;

        private ResponseContentStage(final AsyncRequestProducer requestProducer) {
            this.requestProducer = requestProducer;
        }

        /**
         * Configures {@link AsyncResponseConsumer} to be used by the pipeline to process
         * the incoming response message stream.
         *
         * @param <T> response content representation.
         */
        public <T> ResultStage<T> consume(final HandlerResolver<HttpResponse, AsyncResponseConsumer<T>> responseConsumerResolver) {
            return new ResultStage<>(requestProducer, responseConsumerResolver);
        }

        /**
         * Configures the pipeline to process the incoming response content as an object with
         * the given {@link JavaType}.
         */
        public <T> ResultStage<Message<HttpResponse, T>> asObject(final JavaType javaType) {
            return consume((response, entityDetails, context) ->
                    JsonResponseConsumers.create(objectMapper, javaType));
        }

        /**
         * Configures the pipeline to process the incoming response content as an object with
         * the given {@link Class}.
         */
        public <T> ResultStage<Message<HttpResponse, T>> asObject(final Class<T> clazz) {
            return consume((response, entityDetails, context) ->
                    JsonResponseConsumers.create(objectMapper, clazz));
        }

        /**
         * Configures the pipeline to process the incoming response content as an object with
         * the given {@link TypeReference}.
         */
        public <T> ResultStage<Message<HttpResponse, T>> asObject(final TypeReference<T> typeReference) {
            return consume((response, entityDetails, context) ->
                    JsonResponseConsumers.create(objectMapper, typeReference));
        }

        /**
         * Configures the pipeline to process the incoming response content as a {@link JsonNode} instance.
         */
        public ResultStage<Message<HttpResponse, JsonNode>> asJsonNode() {
            return consume((response, entityDetails, context) ->
                    JsonResponseConsumers.create(objectMapper.getFactory()));
        }

        /**
         * Configures the pipeline to process the incoming response content as a sequence of objects
         * with the given {@link JavaType}.
         */
        public <T> ResultStage<Long> asSequence(
                final JavaType javaType,
                final JsonConsumer<HttpResponse> responseValidator,
                final Callback<JsonNode> errorCallback,
                final JsonResultSink<T> resultSink) {
            return consume((response, entityDetails, context) ->
                    JsonResponseConsumers.create(objectMapper, javaType, responseValidator, errorCallback, resultSink));
        }

        /**
         * Configures the pipeline to process the incoming response content as a sequence of objects
         * with the given {@link Class}.
         */
        public <T> ResultStage<Long> asSequence(
                final Class<T> clazz,
                final JsonConsumer<HttpResponse> responseValidator,
                final Callback<JsonNode> errorCallback,
                final JsonResultSink<T> resultSink) {
            return consume((response, entityDetails, context) ->
                    JsonResponseConsumers.create(objectMapper, clazz, responseValidator, errorCallback, resultSink));
        }

        /**
         * Configures the pipeline to process the incoming response content as a sequence of objects
         * with the given {@link TypeReference}.
         */
        public <T> ResultStage<Long> asSequence(
                final TypeReference<T> typeReference,
                final JsonConsumer<HttpResponse> responseValidator,
                final Callback<JsonNode> errorCallback,
                final JsonResultSink<T> resultSink) {
            return consume((response, entityDetails, context) ->
                    JsonResponseConsumers.create(objectMapper, typeReference, responseValidator, errorCallback, resultSink));
        }

        /**
         * Configures the pipeline to process the incoming response content as a sequence of events.
         */
        public ResultStage<Void> asEvents(
                final JsonConsumer<HttpResponse> responseValidator,
                final Callback<JsonNode> errorCallback,
                final JsonTokenEventHandler eventHandler) {
            return consume((response, entityDetails, context) ->
                    JsonResponseConsumers.create(objectMapper.getFactory(), responseValidator, errorCallback, eventHandler));
        }

    }

    /**
     * Exchange result signal stage.
     */
    public static class ResultStage<T> {

        private final AsyncRequestProducer requestProducer;
        private final HandlerResolver<HttpResponse, AsyncResponseConsumer<T>> responseConsumerResolver;

        private ResultStage(
                final AsyncRequestProducer requestProducer,
                final HandlerResolver<HttpResponse, AsyncResponseConsumer<T>> responseConsumerResolver) {
            this.requestProducer = requestProducer;
            this.responseConsumerResolver = responseConsumerResolver;
        }

        /**
         * Configures the pipeline to signal completion of the message exchange by calling the given
         * {@link FutureCallback} upon completion.
         */
        public CompletionStage<T> result(final FutureCallback<T> resultCallback) {
            return new CompletionStage<>(requestProducer, responseConsumerResolver, resultCallback);
        }

        /**
         * Configures the pipeline to signal completion of the message exchange by triggering the given
         * {@link CompletableFuture} upon completion.
         */
        public CompletionStage<T> result(final CompletableFuture<T> future) {
            Args.notNull(future, "Future");
            return result(new FutureCallback<T>() {

                @Override
                public void completed(final T result) {
                    future.complete(result);
                }

                @Override
                public void failed(final Exception ex) {
                    future.completeExceptionally(ex);
                }

                @Override
                public void cancelled() {
                    future.cancel(true);
                }

            });
        }

    }

    /**
     * Exchange completion stage.
     */
    public static class CompletionStage<T> {

        private final AsyncRequestProducer requestProducer;
        private final HandlerResolver<HttpResponse, AsyncResponseConsumer<T>> responseConsumerResolver;
        private final FutureCallback<T> resultCallback;

        private CompletionStage(
                final AsyncRequestProducer requestProducer,
                final HandlerResolver<HttpResponse, AsyncResponseConsumer<T>> responseConsumerResolver,
                final FutureCallback<T> resultCallback) {
            this.requestProducer = requestProducer;
            this.responseConsumerResolver = responseConsumerResolver;
            this.resultCallback = resultCallback;
        }

        /**
         * Creates {@link AsyncClientExchangeHandler} implementing the defined message exchange pipeline.
         */
        public AsyncClientExchangeHandler create() {

            return new AbstractClientExchangeHandler<T>(requestProducer, resultCallback) {

                @Override
                protected AsyncResponseConsumer<T> supplyConsumer(
                        final HttpResponse response,
                        final EntityDetails entityDetails,
                        final HttpContext context) throws HttpException {
                    final AsyncResponseConsumer<T> requestConsumer = responseConsumerResolver.resolve(response, entityDetails, context);
                    if (requestConsumer == null) {
                        throw new HttpException("Unable to process response");
                    }
                    return requestConsumer;
                }

            };

        }

    }

}
