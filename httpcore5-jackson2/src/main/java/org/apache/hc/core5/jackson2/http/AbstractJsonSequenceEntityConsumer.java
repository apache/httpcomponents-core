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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import org.apache.hc.core5.jackson2.JsonResultSink;
import org.apache.hc.core5.jackson2.JsonTokenConsumer;
import org.apache.hc.core5.jackson2.TokenBufferAssembler;
import org.apache.hc.core5.util.Args;

class AbstractJsonSequenceEntityConsumer<T> extends AbstractJsonEntityConsumer<Long> {

    private final ReadJsonValue<T> readJsonValue;
    private final JsonResultSink<T> resultSink;
    private final AtomicLong count;

    AbstractJsonSequenceEntityConsumer(final JsonFactory jsonFactory, final ReadJsonValue<T> readJsonValue, final JsonResultSink<T> resultSink) {
        super(jsonFactory);
        this.readJsonValue = Args.notNull(readJsonValue, "Json value reader");
        this.resultSink = Args.notNull(resultSink, "Result sink");
        this.count = new AtomicLong(0);
    }

    @Override
    protected JsonTokenConsumer createJsonTokenConsumer(final Consumer<Long> resultConsumer) {
        return new TokenBufferAssembler(new JsonResultSink<TokenBuffer>() {


            @Override
            public void begin(final int sizeHint) {
                resultSink.begin(sizeHint);
            }

            @Override
            public void accept(final TokenBuffer tokenBuffer) {
                try {
                    final JsonParser jsonParser = tokenBuffer != null ? tokenBuffer.asParserOnFirstToken() : null;
                    final T result = jsonParser != null ? readJsonValue.readValue(jsonParser) : null;
                    if (result != null) {
                        count.incrementAndGet();
                        resultSink.accept(result);
                    }
                } catch (final IOException ex) {
                    failed(ex);
                }
            }

            @Override
            public void end() {
                resultSink.end();
                resultConsumer.accept(count.get());
            }

        });
    }

    @FunctionalInterface
    interface ReadJsonValue<T> {
        T readValue(JsonParser jsonParser) throws IOException;
    }

}
