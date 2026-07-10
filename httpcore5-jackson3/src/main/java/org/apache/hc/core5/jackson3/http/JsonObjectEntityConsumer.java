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
package org.apache.hc.core5.jackson3.http;

import java.io.IOException;
import java.util.function.Consumer;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import org.apache.hc.core5.jackson3.JsonTokenConsumer;
import org.apache.hc.core5.jackson3.TokenBufferAssembler;
import org.apache.hc.core5.jackson3.JacksonSupport;
import org.apache.hc.core5.util.Args;

/**
 * {@link org.apache.hc.core5.http.nio.AsyncEntityConsumer} implementation that
 * de-serializes incoming HTTP message entity into an instance of the given class.
 *
 * @param <T> type of objects produced by this class.
 * @since 5.5
 */
public class JsonObjectEntityConsumer<T> extends AbstractJsonEntityConsumer<T> {

    private final ReadJsonValue<T> readJsonValue;

    public JsonObjectEntityConsumer(final ObjectMapper objectMapper, final JavaType javaType) {
        super(Args.notNull(objectMapper, "Object mapper").tokenStreamFactory());
        this.readJsonValue = jsonParser -> objectMapper.readValue(jsonParser, javaType);
    }

    public JsonObjectEntityConsumer(final ObjectMapper objectMapper, final Class<T> objectClazz) {
        this(Args.notNull(objectMapper, "Object mapper"), objectMapper.getTypeFactory().constructType(objectClazz));
    }

    public JsonObjectEntityConsumer(final ObjectMapper objectMapper, final TypeReference<T> typeReference) {
        this(Args.notNull(objectMapper, "Object mapper"), objectMapper.getTypeFactory().constructType(typeReference));
    }

    @Override
    protected JsonTokenConsumer createJsonTokenConsumer(final Consumer<T> resultConsumer) {
        return new TokenBufferAssembler(tokenBuffer -> {
            try {
                final JsonParser jsonParser = tokenBuffer != null ? tokenBuffer.asParserOnFirstToken(ObjectReadContext.empty()) : null;
                final T result = jsonParser != null ? readJsonValue.readValue(jsonParser) : null;
                resultConsumer.accept(result);
            } catch (final IOException ex) {
                failed(ex);
            } catch (final JacksonException ex) {
                failed(JacksonSupport.asIOException(ex));
            }
        });
    }

    @FunctionalInterface
    private interface ReadJsonValue<T> {
        T readValue(JsonParser jsonParser) throws IOException;
    }
}
