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
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncResponseProducer;
import org.apache.hc.core5.jackson2.JsonConsumer;

/**
 * Factory class for JSON {@link AsyncResponseProducer}s.
 */
public final class JsonResponseProducers {

    /**
     * Creates {@link AsyncResponseProducer} that generates an HTTP response
     * enclosing a serialized JSON object as a message body.
     *
     * @param response     the response message head.
     * @param jsonObject   teh JSON object to be enclosed as a message body.
     * @param objectMapper the object mapper to be used to serialize JSON content.
     * @param <T>          the type of objects used by the producer.
     * @return the response producer.
     */
    public static <T> AsyncResponseProducer create(final HttpResponse response,
                                                   final T jsonObject,
                                                   final ObjectMapper objectMapper) {
        return new JsonResponseObjectProducer(response, new JsonObjectEntityProducer<>(jsonObject, objectMapper));
    }

    /**
     * Creates {@link AsyncResponseProducer} that generates an HTTP response
     * enclosing a serialized JSON object as a message body.
     *
     * @param response     the response message head.
     * @param jsonObject   the JSON object to be enclosed as a message body.
     * @param objectMapper the object mapper to be used to serialize JSON content.
     * @return the response producer.
     */
    public static AsyncResponseProducer create(final HttpResponse response,
                                               final JsonNode jsonObject,
                                               final ObjectMapper objectMapper) {
        return new JsonResponseObjectProducer(response, new JsonNodeEntityProducer(jsonObject, objectMapper));
    }

    /**
     * Creates {@link AsyncResponseProducer} that generates an HTTP response
     * enclosing a sequence of serialized JSON object as a message body.
     *
     * @param response       the response message head.
     * @param objectMapper   the object mapper to be used to serialize JSON content.
     * @param objectProducer the JSON object producer.
     * @return the response producer.
     */
    public static <T> AsyncResponseProducer create(final HttpResponse response,
                                                   final ObjectMapper objectMapper,
                                                   final ObjectProducer<T> objectProducer) {
        return new JsonResponseObjectProducer(response, new JsonSequenceEntityProducer<>(objectMapper, objectProducer));
    }

    /**
     * Creates {@link AsyncResponseProducer} that generates an HTTP response
     * enclosing JSON content generated using the provided {@link JsonGenerator}.
     *
     * @param response    the response message head.
     * @param jsonFactory JSON factory.
     * @param consumer    the recipient of JSON content generator.
     * @return the response producer.
     */
    public static AsyncResponseProducer create(final HttpResponse response,
                                               final JsonFactory jsonFactory,
                                               final JsonConsumer<JsonGenerator> consumer) {
        return new JsonResponseObjectProducer(response, new JsonTokenEntityProducer(jsonFactory, consumer));
    }

}
