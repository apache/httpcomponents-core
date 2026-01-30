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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.hc.core5.jackson2.JsonResultSink;
import org.apache.hc.core5.util.Args;

/**
 * {@link org.apache.hc.core5.http.nio.AsyncEntityConsumer} implementation that
 * converts incoming HTTP message entity into a sequence of {@link JsonNode} instance
 * and passes those objects to the {@link JsonResultSink}.
 *
 * @since 5.5
 */
public class JsonNodeSequenceEntityConsumer extends AbstractJsonSequenceEntityConsumer<JsonNode> {

    public JsonNodeSequenceEntityConsumer(final ObjectMapper objectMapper, final JsonResultSink<JsonNode> resultSink) {
        super(Args.notNull(objectMapper, "Object mapper").getFactory(), objectMapper::readTree, resultSink);
    }

}
