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
package org.apache.hc.core5.jackson3;

import java.io.IOException;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonTokenId;
import tools.jackson.core.exc.InputCoercionException;

/**
 * {@link JsonTokenConsumer} implementation that converts JSON tokens into
 * event signals for {@link JsonTokenEventHandler}.
 *
 * @since 5.5
 */
public final class JsonTokenEventHandlerAdaptor implements JsonTokenConsumer {

    private final JsonTokenEventHandler eventHandler;

    public JsonTokenEventHandlerAdaptor(final JsonTokenEventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    public void accept(final int tokenId, final JsonParser jsonParser) throws IOException {
        switch (tokenId) {
            case JsonTokenId.ID_START_OBJECT:
                eventHandler.objectStart();
                break;
            case JsonTokenId.ID_END_OBJECT:
                eventHandler.objectEnd();
                break;
            case JsonTokenId.ID_START_ARRAY:
                eventHandler.arrayStart();
                break;
            case JsonTokenId.ID_END_ARRAY:
                eventHandler.arrayEnd();
                break;
            case JsonTokenId.ID_PROPERTY_NAME:
                eventHandler.field(jsonParser.currentName());
                break;
            case JsonTokenId.ID_STRING:
                eventHandler.value(jsonParser.getString());
                break;
            case JsonTokenId.ID_NUMBER_INT:
                final JsonParser.NumberType numberType = jsonParser.getNumberType();
                if (numberType == JsonParser.NumberType.LONG) {
                    eventHandler.value(jsonParser.getLongValue());
                } else if (numberType == JsonParser.NumberType.BIG_INTEGER) {
                    try {
                        eventHandler.value(jsonParser.getLongValue());
                    } catch (final InputCoercionException ex) {
                        throw new IOException("Integer value is out of range for 64-bit signed long: " + jsonParser.getString(), ex);
                    }
                } else {
                    eventHandler.value(jsonParser.getIntValue());
                }
                break;
            case JsonTokenId.ID_NUMBER_FLOAT:
                eventHandler.value(jsonParser.getDoubleValue());
                break;
            case JsonTokenId.ID_TRUE:
                eventHandler.value(true);
                break;
            case JsonTokenId.ID_FALSE:
                eventHandler.value(false);
                break;
            case JsonTokenId.ID_NULL:
                eventHandler.valueNull();
                break;
            case JsonTokenId.ID_EMBEDDED_OBJECT:
                eventHandler.embeddedObject(jsonParser.getEmbeddedObject());
                break;
            case JsonTokenId.ID_NO_TOKEN:
                eventHandler.endOfStream();
                break;
            default:
                throw new IllegalStateException("Unexpected JSON token id: " + tokenId);
        }

    }

}
