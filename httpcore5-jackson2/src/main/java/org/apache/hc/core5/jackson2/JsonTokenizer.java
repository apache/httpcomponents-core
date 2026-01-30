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
import java.io.InputStream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonTokenId;

import org.apache.hc.core5.util.Args;

/**
 * Classic (blocking) JSON tokenizer that consumes data from {@link InputStream}
 * and emits events to {@link JsonTokenConsumer}.
 *
 * @since 5.5
 */
public final class JsonTokenizer {

    private final JsonFactory jsonFactory;

    public JsonTokenizer(final JsonFactory jsonFactory) {
        this.jsonFactory = jsonFactory;
    }

    public void tokenize(final InputStream inputStream,
                         final JsonTokenConsumer consumer) throws IOException {
        Args.notNull(consumer, "Consumer");
        final JsonParser parser = jsonFactory.createParser(inputStream);
        while (!parser.isClosed()) {
            final JsonToken jsonToken = parser.nextToken();
            if (jsonToken != null) {
                consumer.accept(jsonToken.id(), parser);
            }
        }
        consumer.accept(JsonTokenId.ID_NO_TOKEN, parser);
    }

}
