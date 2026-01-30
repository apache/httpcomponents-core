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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.hc.core5.jackson2.JsonResultSink;
import org.apache.hc.core5.util.Args;

/**
 * {@link org.apache.hc.core5.http.nio.AsyncEntityConsumer} implementation that
 * converts incoming HTTP message entity into a sequence of instances
 * of the given class and passes those objects to the {@link JsonResultSink}.
 *
 * @param <T> type of objects produced by this class.
 * @since 5.5
 */
public class JsonSequenceEntityConsumer<T> extends AbstractJsonSequenceEntityConsumer<T> {

    public JsonSequenceEntityConsumer(final ObjectMapper objectMapper, final JavaType javaType, final JsonResultSink<T> resultSink) {
        super(Args.notNull(objectMapper, "Object mapper").getFactory(),
                jsonParser -> objectMapper.readValue(jsonParser, javaType),
                resultSink);
    }

    public JsonSequenceEntityConsumer(final ObjectMapper objectMapper, final Class<T> objectClazz, final JsonResultSink<T> resultSink) {
        this(Args.notNull(objectMapper, "Object mapper"),
                objectMapper.getTypeFactory().constructType(objectClazz),
                resultSink);
    }

    public JsonSequenceEntityConsumer(final ObjectMapper objectMapper, final TypeReference<T> typeReference, final JsonResultSink<T> resultSink) {
        this(Args.notNull(objectMapper, "Object mapper"),
                objectMapper.getTypeFactory().constructType(typeReference),
                resultSink);
    }

}
