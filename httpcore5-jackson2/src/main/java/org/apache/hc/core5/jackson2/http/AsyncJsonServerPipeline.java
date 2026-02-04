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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.ExchangeHandler;
import org.apache.hc.core5.http.HandlerResolver;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.MethodNotAllowedException;
import org.apache.hc.core5.http.Validator;
import org.apache.hc.core5.http.impl.ServerSupport;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.AsyncResponseProducer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.entity.DiscardingEntityConsumer;
import org.apache.hc.core5.http.nio.support.AbstractServerExchangeHandler;
import org.apache.hc.core5.http.nio.support.BasicRequestConsumer;
import org.apache.hc.core5.http.nio.support.BasicResponseProducer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.jackson2.JsonConsumer;
import org.apache.hc.core5.jackson2.JsonResultSink;
import org.apache.hc.core5.jackson2.JsonTokenEventHandler;
import org.apache.hc.core5.util.Args;

/**
 * Server side execution pipeline assembler that supplies {@link AsyncServerExchangeHandler} instances
 * with the defined message exchange pipeline optimized for JSON message exchanges.
 * <p>
 * Please note that {@link AsyncServerExchangeHandler} are stateful and may not be used concurrently
 * by multiple message exchanges or re-used for subsequent message exchanges.
 *
 * @since 5.5
 */
public final class AsyncJsonServerPipeline {

    private final ObjectMapper objectMapper;

    private AsyncJsonServerPipeline(final ObjectMapper objectMapper) {
        this.objectMapper = Args.notNull(objectMapper, "Object mapper");
    }

    public static AsyncJsonServerPipeline assemble(final ObjectMapper objectMapper) {
        return new AsyncJsonServerPipeline(objectMapper);
    }

    /**
     * Configures the pipeline to process the incoming request message stream provided the request
     * message passes the validation.
     */
    public RequestContentStage request(final Validator<HttpRequest> requestValidator) {
        return new RequestContentStage(requestValidator);
    }

    /**
     * Configures the pipeline to process the incoming request message stream provided the request
     * method of the incoming message is allowed.
     */
    public RequestContentStage request(final Method... allowedMethods) {
        return new RequestContentStage(request -> {
            final String method = request.getMethod();
            if (!ServerSupport.isMethodAllowed(method, allowedMethods)) {
                throw new MethodNotAllowedException(method + " not allowed");
            }
        });
    }

    /**
     * Configures the pipeline to process the incoming request message stream.
     */
    public RequestContentStage request() {
        return new RequestContentStage(null);
    }

    /**
     * Request content processing stage.
     */
    public class RequestContentStage {

        private final Validator<HttpRequest> requestValidator;

        private RequestContentStage(final Validator<HttpRequest> requestValidator) {
            this.requestValidator = requestValidator;
        }

        /**
         * Resolves {@link AsyncRequestConsumer} to be used by the pipeline to process the incoming
         * request message stream.
         *
         * @param <T> request content representation.
         */
        public <T> ResponseStage<T> consume(
                final HandlerResolver<HttpRequest, AsyncRequestConsumer<T>> requestConsumerResolver) {
            return new ResponseStage<>(requestValidator, requestConsumerResolver);
        }

        /**
         * Configures the pipeline to process the incoming request content as an object
         * with the given {@link JavaType}.
         */
        public <T> ResponseStage<Message<HttpRequest, T>> asObject(final JavaType javaType) {
            return consume((request, entityDetails, context) ->
                    JsonRequestConsumers.create(objectMapper, javaType));
        }

        /**
         * Configures the pipeline to process the incoming request content as an object
         * with the given {@link Class}.
         */
        public <T> ResponseStage<Message<HttpRequest, T>> asObject(final Class<T> clazz) {
            return consume((request, entityDetails, context) ->
                    JsonRequestConsumers.create(objectMapper, clazz));
        }

        /**
         * Configures the pipeline to process the incoming request content as an object
         * with the given {@link TypeReference}.
         */
        public <T> ResponseStage<Message<HttpRequest, T>> asObject(final TypeReference<T> typeReference) {
            return consume((request, entityDetails, context) ->
                    JsonRequestConsumers.create(objectMapper, typeReference));
        }

        /**
         * Configures the pipeline to process the incoming request content as a {@link JsonNode} instance.
         */
        public ResponseStage<Message<HttpRequest, JsonNode>> asJsonNode() {
            return consume((request, entityDetails, context) ->
                    JsonRequestConsumers.create(objectMapper.getFactory()));
        }

        /**
         * Configures the pipeline to process the incoming request content as a sequence of objects
         * with the given {@link JavaType}.
         */
        public <T> ResponseStage<Long> asSequence(
                final JavaType javaType,
                final JsonConsumer<HttpRequest> validator,
                final JsonResultSink<T> resultSink) {
            return consume((request, entityDetails, context) ->
                    JsonRequestConsumers.create(objectMapper, javaType, validator, resultSink));
        }

        /**
         * Configures the pipeline to process the incoming request content as a sequence of objects
         * with the given {@link Class}.
         */
        public <T> ResponseStage<Long> asSequence(
                final Class<T> clazz,
                final JsonConsumer<HttpRequest> validator,
                final JsonResultSink<T> resultSink) {
            return consume((request, entityDetails, context) ->
                    JsonRequestConsumers.create(objectMapper, clazz, validator, resultSink));
        }

        /**
         * Configures the pipeline to process the incoming request content as a sequence of objects
         * with the given {@link TypeReference}.
         */
        public <T> ResponseStage<Long> asSequence(
                final TypeReference<T> typeReference,
                final JsonConsumer<HttpRequest> validator,
                final JsonResultSink<T> resultSink) {
            return consume((request, entityDetails, context) ->
                    JsonRequestConsumers.create(objectMapper, typeReference, validator, resultSink));
        }

        /**
         * Configures the pipeline to process the incoming request content as a sequence of objects
         * with the given {@link TypeReference}.
         */
        public ResponseStage<Void> asEvents(
                final JsonConsumer<HttpRequest> validator,
                final JsonTokenEventHandler eventHandler) {
            return consume((request, entityDetails, context) ->
                    JsonRequestConsumers.create(objectMapper.getFactory(), validator, eventHandler));
        }

        /**
         * Configures the pipeline to ignore and discard the incoming request content.
         */
        public ResponseStage<Message<HttpRequest, Void>> ignoreContent() {
            return consume((request, entityDetails, context) ->
                    new BasicRequestConsumer<>(DiscardingEntityConsumer::new));
        }

    }

    /**
     * Response message stage.
     */
    public class ResponseStage<I> {

        private final Validator<HttpRequest> requestValidator;
        private final HandlerResolver<HttpRequest, AsyncRequestConsumer<I>> requestConsumerResolver;

        private ResponseStage(
                final Validator<HttpRequest> requestValidator,
                final HandlerResolver<HttpRequest, AsyncRequestConsumer<I>> requestConsumerResolver) {
            this.requestValidator = requestValidator;
            this.requestConsumerResolver = requestConsumerResolver;
        }

        /**
         * Configures the pipeline to produces an outgoing response message stream based on
         * the incoming request message and content.
         */
        public ResponseContentStage<I> response() {
            return new ResponseContentStage<>(requestValidator, requestConsumerResolver);
        }

    }

    /**
     * Response content generation stage.
     */
    public class ResponseContentStage<I> {

        private final Validator<HttpRequest> requestValidator;
        private final HandlerResolver<HttpRequest, AsyncRequestConsumer<I>> requestConsumerResolver;

        private ResponseContentStage(
                final Validator<HttpRequest> requestValidator,
                final HandlerResolver<HttpRequest, AsyncRequestConsumer<I>> requestConsumerResolver) {
            this.requestValidator = requestValidator;
            this.requestConsumerResolver = requestConsumerResolver;
        }

        /**
         * Resolves {@link AsyncResponseProducer} to be used by the pipeline to generate the outgoing response
         * message stream based on the given response message object.
         *
         * @param <O> response content representation.
         */
        public <O> RequestHandlingStage<I, O> produce(
                final Resolver<O, AsyncResponseProducer> responseProducerResolver) {
            return new RequestHandlingStage<>(requestValidator, requestConsumerResolver, responseProducerResolver);
        }

        /**
         * Resolves {@link AsyncEntityProducer} to be used by the pipeline to generate the outgoing response
         * content stream based on the given response content object.
         *
         * @param <O> response content representation.
         */
        public <O> RequestHandlingStage<I, Message<HttpResponse, O>> produceContent(
                final Resolver<O, AsyncEntityProducer> dataProducerResolver) {
            return new RequestHandlingStage<>(
                    requestValidator,
                    requestConsumerResolver,
                    m ->
                            new BasicResponseProducer(m.head(), dataProducerResolver.resolve(m.body())));
        }

        /**
         * Configures the pipeline to represent the response message content as an object
         * with the given {@link Class}.
         */
        public <O> RequestHandlingStage<I, Message<HttpResponse, O>> asObject(final Class<O> clazz) {
            return produce(m -> JsonResponseProducers.create(m.head(), m.body(), objectMapper));
        }

        /**
         * Configures the pipeline to represent the response message content as an object.
         */
        public RequestHandlingStage<I, Message<HttpResponse, JsonNode>> asJsonNode() {
            return produce(m -> JsonResponseProducers.create(m.head(), m.body(), objectMapper));
        }

        /**
         * Configures the pipeline to represent the response message content as a sequence
         * of objects.
         */
        public <O> RequestHandlingStage<I, HttpResponse> asSequence(final ObjectProducer<O> objectProducer) {
            return produce(r -> JsonResponseProducers.create(r, objectMapper, objectProducer));
        }

    }

    /**
     * Request handling stage.
     */
    public class RequestHandlingStage<I, O> {

        private final Validator<HttpRequest> requestValidator;
        private final HandlerResolver<HttpRequest, AsyncRequestConsumer<I>> requestConsumerResolver;
        private final Resolver<O, AsyncResponseProducer> responseProducerResolver;

        private RequestHandlingStage(
                final Validator<HttpRequest> requestValidator,
                final HandlerResolver<HttpRequest, AsyncRequestConsumer<I>> requestConsumerResolver,
                final Resolver<O, AsyncResponseProducer> responseProducerResolver) {
            this.requestValidator = requestValidator;
            this.requestConsumerResolver = requestConsumerResolver;
            this.responseProducerResolver = responseProducerResolver;
        }

        /**
         * Configures the pipeline to resolve the exception to a response message stream.
         */
        public ExceptionStage<I, O> exception(
                final Resolver<Exception, AsyncResponseProducer> exceptionMapper) {
            return new ExceptionStage<>(requestValidator, requestConsumerResolver, responseProducerResolver, exceptionMapper);
        }

        /**
         * Configures the pipeline to resolve the exception to a response error message. The response status
         * code will be determined by default based on the exception type.
         */
        public ExceptionStage<I, O> errorMessage(
                final Resolver<Exception, String> messageMapper) {
            return exception(ex ->
                    new BasicResponseProducer(
                            new BasicHttpResponse(ServerSupport.toStatusCode(ex)),
                            messageMapper.resolve(ex),
                            ContentType.TEXT_PLAIN));
        }

        /**
         * Configures the pipeline to handle the message exchange by generating a response object
         * based on the properties of the request object.
         */
        public CompletionStage<I, O> handle(final ExchangeHandler<I, O> exchangeHandler) {
            return errorMessage(ServerSupport::toErrorMessage).handle(exchangeHandler);
        }

    }

    /**
     * Exception handling stage.
     */
    public class ExceptionStage<I, O> {

        private final Validator<HttpRequest> requestValidator;
        private final HandlerResolver<HttpRequest, AsyncRequestConsumer<I>> requestConsumerResolver;
        private final Resolver<O, AsyncResponseProducer> responseProducerResolver;
        private final Resolver<Exception, AsyncResponseProducer> exceptionMapper;

        private ExceptionStage(
                final Validator<HttpRequest> requestValidator,
                final HandlerResolver<HttpRequest, AsyncRequestConsumer<I>> requestConsumerResolver,
                final Resolver<O, AsyncResponseProducer> responseProducerResolver,
                final Resolver<Exception, AsyncResponseProducer> exceptionMapper) {
            this.requestValidator = requestValidator;
            this.requestConsumerResolver = requestConsumerResolver;
            this.responseProducerResolver = responseProducerResolver;
            this.exceptionMapper = exceptionMapper;
        }

        /**
         * Configures the pipeline to handle the message exchange by generating a response object
         * based on the properties of the request object.
         */
        public CompletionStage<I, O> handle(final ExchangeHandler<I, O> exchangeHandler) {
            return new CompletionStage<>(requestValidator, requestConsumerResolver, responseProducerResolver, exchangeHandler, exceptionMapper);
        }

    }

    /**
     * Exchange completion stage.
     */
    public class CompletionStage<I, O> {

        private final Validator<HttpRequest> requestValidator;
        private final HandlerResolver<HttpRequest, AsyncRequestConsumer<I>> requestConsumerResolver;
        private final Resolver<O, AsyncResponseProducer> responseProducerResolver;
        private final ExchangeHandler<I, O> exchangeHandler;
        private final Resolver<Exception, AsyncResponseProducer> exceptionMapper;

        private CompletionStage(
                final Validator<HttpRequest> requestValidator,
                final HandlerResolver<HttpRequest, AsyncRequestConsumer<I>> requestConsumerResolver,
                final Resolver<O, AsyncResponseProducer> responseProducerResolver,
                final ExchangeHandler<I, O> exchangeHandler,
                final Resolver<Exception, AsyncResponseProducer> exceptionMapper) {
            this.requestValidator = requestValidator;
            this.requestConsumerResolver = requestConsumerResolver;
            this.responseProducerResolver = responseProducerResolver;
            this.exchangeHandler = exchangeHandler;
            this.exceptionMapper = exceptionMapper;
        }

        /**
         * Creates {@link Supplier} of {@link AsyncServerExchangeHandler} implementing the defined message
         * exchange pipeline.
         */
        public Supplier<AsyncServerExchangeHandler> supplier() {
            return () -> new AbstractServerExchangeHandler<I>() {

                @Override
                protected AsyncRequestConsumer<I> supplyConsumer(
                        final HttpRequest request,
                        final EntityDetails entityDetails,
                        final HttpContext context) throws HttpException {
                    if (requestValidator != null) {
                        requestValidator.validate(request);
                    }
                    final AsyncRequestConsumer<I> requestConsumer = requestConsumerResolver.resolve(request, entityDetails, context);
                    if (requestConsumer == null) {
                        throw new HttpException("Unable to process request");
                    }
                    return requestConsumer;
                }

                @Override
                protected void handle(
                        final I requestMessage,
                        final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                        final HttpContext context) throws HttpException, IOException {
                    final O responseMessage = exchangeHandler.handle(requestMessage, context);
                    if (responseMessage == null) {
                        throw new HttpException("Unable to handle request");
                    }
                    final AsyncResponseProducer responseProducer = responseProducerResolver.resolve(responseMessage);
                    if (responseProducer == null) {
                        throw new HttpException("Unable to produce response");
                    }
                    responseTrigger.submitResponse(responseProducer, context);
                }

                @Override
                protected AsyncResponseProducer handleError(final Exception ex) {
                    final AsyncResponseProducer responseProducer = exceptionMapper != null ? exceptionMapper.resolve(ex) : null;
                    return responseProducer != null ? responseProducer : super.handleError(ex);
                }

            };
        }

    }

}