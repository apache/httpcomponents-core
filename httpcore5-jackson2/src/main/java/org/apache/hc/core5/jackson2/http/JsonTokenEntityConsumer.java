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

import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonTokenId;

import org.apache.hc.core5.jackson2.JsonTokenConsumer;
import org.apache.hc.core5.jackson2.JsonTokenEventHandler;
import org.apache.hc.core5.jackson2.JsonTokenEventHandlerAdaptor;
import org.apache.hc.core5.util.Args;

/**
 * {@link org.apache.hc.core5.http.nio.AsyncEntityConsumer} implementation that
 * converts incoming HTTP message entity into a sequence of JSON tokens passed as
 * events to the given {@link JsonTokenEventHandler}.
 *
 * @since 5.5
 */
public class JsonTokenEntityConsumer extends AbstractJsonEntityConsumer<Void> {

    private final JsonTokenConsumer jsonTokenConsumer;

    public JsonTokenEntityConsumer(final JsonFactory jsonFactory, final JsonTokenEventHandler eventHandler) {
        super(jsonFactory);
        this.jsonTokenConsumer = new JsonTokenEventHandlerAdaptor(Args.notNull(eventHandler, "JSON event handler"));
    }

    public JsonTokenEntityConsumer(final JsonFactory jsonFactory, final JsonTokenConsumer tokenConsumer) {
        super(jsonFactory);
        this.jsonTokenConsumer = Args.notNull(tokenConsumer, "JSON token consumer");
    }


    @Override
    protected JsonTokenConsumer createJsonTokenConsumer(final Consumer<Void> resultConsumer) {
        return (tokenId, jsonParser) -> {
            jsonTokenConsumer.accept(tokenId, jsonParser);
            if (tokenId == JsonTokenId.ID_NO_TOKEN) {
                resultConsumer.accept(null);
            }
        };
    }

}
