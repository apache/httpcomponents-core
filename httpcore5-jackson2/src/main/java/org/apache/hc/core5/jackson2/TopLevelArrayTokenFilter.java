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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonTokenId;

import org.apache.hc.core5.util.Args;

/**
 * {@link JsonTokenConsumer} decorator that filters out top level opening and closing array tokens.
 *
 * @since 5.5
 */
public final class TopLevelArrayTokenFilter implements JsonTokenConsumer {

    private final JsonTokenConsumer tokenConsumer;
    private int depth;

    public TopLevelArrayTokenFilter(final JsonTokenConsumer tokenConsumer) {
        Args.notNull(tokenConsumer, "Consumer");
        this.tokenConsumer = tokenConsumer;
    }

    public void accept(final int tokenId, final JsonParser jsonParser) throws IOException {
        if (depth == 0 &&
                (tokenId == JsonTokenId.ID_START_ARRAY || tokenId == JsonTokenId.ID_END_ARRAY)) {
            return;
        }
        switch (tokenId) {
            case JsonTokenId.ID_START_OBJECT:
            case JsonTokenId.ID_START_ARRAY:
                depth++;
                break;
            case JsonTokenId.ID_END_OBJECT:
            case JsonTokenId.ID_END_ARRAY:
                depth--;
                break;
        }
        tokenConsumer.accept(tokenId, jsonParser);
    }

}
