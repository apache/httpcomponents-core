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
package org.apache.hc.core5.jackson2;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;

import org.apache.hc.core5.util.Args;

/**
 * Event-driven, reactive JSON tokenizer that consumes data from a sequence
 * {@link ByteBuffer} objects and emits events to {@link JsonTokenConsumer}.
 *
 * @since 5.5
 */
public final class JsonAsyncTokenizer {

    private final JsonFactory jsonFactory;

    private JsonTokenConsumer consumer;
    private JsonParser parser;
    private ByteArrayFeeder inputFeeder;

    public JsonAsyncTokenizer(final JsonFactory jsonFactory) {
        this.jsonFactory = jsonFactory;
    }

    /**
     * Triggered to initialize the tokenizer and pass the token event consumer.
     *
     * @param consumer the token event consumer.
     */
    public void initialize(final JsonTokenConsumer consumer) throws IOException {
        Args.notNull(consumer, "Consumer");
        this.parser = jsonFactory.createNonBlockingByteArrayParser();
        this.inputFeeder = (ByteArrayFeeder) parser.getNonBlockingInputFeeder();
        this.consumer = consumer;
    }

    private void processData() throws IOException {
        for (; ; ) {
            final JsonToken jsonToken = parser.nextToken();
            if (jsonToken == null || jsonToken == JsonToken.NOT_AVAILABLE) {
                break;
            } else {
                consumer.accept(jsonToken.id(), parser);
            }
        }
    }

    /**
     * Triggered to feed a chunk of data. As a result of this method call the tokenizer
     * may emit events to the token event consumer.
     *
     * @param data the chunk of data
     */
    public void consume(final ByteBuffer data) throws IOException {
        if (data == null) {
            return;
        }
        if (consumer == null) {
            return;
        }
        if (data.hasArray()) {
            final int off = data.arrayOffset();
            inputFeeder.feedInput(data.array(), off + data.position(), off + data.limit());
            data.position(data.limit());
        } else {
            final byte[] tmp = new byte[data.remaining()];
            data.get(tmp);
            inputFeeder.feedInput(tmp, 0, tmp.length);
        }
        processData();
    }

    /**
     * Triggered to signal the end of data stream. As a result of this method call the tokenizer
     * may emit events to the token event consumer.
     */
    public void streamEnd() throws IOException {
        if (consumer == null) {
            return;
        }
        inputFeeder.endOfInput();
        processData();
        consumer.accept(JsonTokenId.ID_NO_TOKEN, parser);
        inputFeeder = null;
        parser = null;
        consumer = null;
    }

}
