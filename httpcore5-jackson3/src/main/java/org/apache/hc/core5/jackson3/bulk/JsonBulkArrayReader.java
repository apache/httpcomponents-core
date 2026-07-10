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
package org.apache.hc.core5.jackson3.bulk;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.util.TokenBuffer;

import org.apache.hc.core5.jackson3.JsonAsyncTokenizer;
import org.apache.hc.core5.jackson3.JsonResultSink;
import org.apache.hc.core5.jackson3.TokenBufferAssembler;
import org.apache.hc.core5.jackson3.TopLevelArrayTokenFilter;
import org.apache.hc.core5.jackson3.JacksonSupport;

/**
 * Event-driven bulk JSON reader that can read arrays of objects while buffering only a single
 * array element in memory.
 *
 * @since 5.5
 */
public final class JsonBulkArrayReader {

    private final ObjectMapper objectMapper;
    private final JsonAsyncTokenizer jsonTokenizer;

    public JsonBulkArrayReader(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.jsonTokenizer = new JsonAsyncTokenizer(objectMapper.tokenStreamFactory());
    }

    public <T> void initialize(final TypeReference<T> typeReference, final JsonResultSink<T> resultSink) throws IOException {
        this.jsonTokenizer.initialize(new TopLevelArrayTokenFilter(new TokenBufferAssembler(new JsonResultSink<TokenBuffer>() {

            @Override
            public void begin(final int sizeHint) {
                resultSink.begin(sizeHint);
            }

            @Override
            public void accept(final TokenBuffer tokenBuffer) {
                try {
                    final JsonParser jsonParser = tokenBuffer != null ? tokenBuffer.asParserOnFirstToken(ObjectReadContext.empty()) : null;
                    final T result = jsonParser != null ? objectMapper.readValue(jsonParser, typeReference) : null;
                    if (result != null) {
                        resultSink.accept(result);
                    }
                } catch (final JacksonException ex) {
                    throw new UncheckedIOException(JacksonSupport.asIOException(ex));
                }
            }

            @Override
            public void end() {
                resultSink.end();
            }

        })));
    }

    public void consume(final ByteBuffer data) throws IOException {
        try {
            jsonTokenizer.consume(data);
        } catch (final UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    public void streamEnd() throws IOException {
        jsonTokenizer.streamEnd();
    }

}
