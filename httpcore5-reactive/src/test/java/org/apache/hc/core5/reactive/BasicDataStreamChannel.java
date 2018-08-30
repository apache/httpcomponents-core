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

package org.apache.hc.core5.reactive;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.nio.DataStreamChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;

public class BasicDataStreamChannel implements DataStreamChannel {

    private final WritableByteChannel byteChannel;
    private List<Header> trailers;

    public BasicDataStreamChannel(final WritableByteChannel byteChannel) {
        this.byteChannel = byteChannel;
    }

    @Override
    public void requestOutput() {
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        return byteChannel.write(src);
    }

    @Override
    public void endStream() throws IOException {
        if (byteChannel.isOpen()) {
            byteChannel.close();
        }
    }

    @Override
    public void endStream(final List<? extends Header> trailers) throws IOException {
        endStream();
        if (trailers != null) {
            this.trailers = new ArrayList<>();
            this.trailers.addAll(trailers);
        }
    }

    public List<Header> getTrailers() {
        return trailers;
    }

}
