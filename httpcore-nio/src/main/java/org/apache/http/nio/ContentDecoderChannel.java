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

package org.apache.http.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * A {@link ReadableByteChannel} that delegates to a {@link ContentDecoder}.
 * Attempts to close this channel are ignored, and {@link #isOpen} always
 * returns <code>true</code>.
 *
 * @since 4.0
 */
public class ContentDecoderChannel implements ReadableByteChannel {

    private final ContentDecoder decoder;

    public ContentDecoderChannel(ContentDecoder decoder) {
        this.decoder = decoder;
    }

    public int read(ByteBuffer dst) throws IOException {
        return decoder.read(dst);
    }

    public void close() {}

    public boolean isOpen() {
        return true;
    }

}
