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
import com.fasterxml.jackson.databind.util.TokenBuffer;

import org.apache.hc.core5.util.Args;

/**
 * {@link JsonTokenConsumer} implementation that copies Json token events
 * into {@link TokenBuffer} and passes those buffers to the {@link JsonResultSink}.
 *
 * @since 5.5
 */
public final class TokenBufferAssembler implements JsonTokenConsumer {

    private final JsonResultSink<TokenBuffer> sink;

    private boolean started;
    private int depth;
    private TokenBuffer buffer;

    public TokenBufferAssembler(final JsonResultSink<TokenBuffer> sink) {
        Args.notNull(sink, "Result sink");
        this.sink = sink;
        this.buffer = new TokenBuffer(null, false);
    }

    public void accept(final int tokenId, final JsonParser jsonParser) throws IOException {
        if (!started) {
            started = true;
            sink.begin(-1);
        }
        if (tokenId != JsonTokenId.ID_NO_TOKEN) {
            buffer.copyCurrentEvent(jsonParser);
        }
        switch (tokenId) {
            case JsonTokenId.ID_START_OBJECT:
            case JsonTokenId.ID_START_ARRAY:
                depth++;
                break;
            case JsonTokenId.ID_END_OBJECT:
            case JsonTokenId.ID_END_ARRAY:
                depth--;
                if (depth == 0) {
                    buffer.close();
                    sink.accept(buffer);
                    buffer = new TokenBuffer(null, false);
                }
                break;
            case JsonTokenId.ID_NO_TOKEN:
                if (!buffer.isClosed()) {
                    buffer.close();
                }
                sink.end();
                break;
        }
    }

}
