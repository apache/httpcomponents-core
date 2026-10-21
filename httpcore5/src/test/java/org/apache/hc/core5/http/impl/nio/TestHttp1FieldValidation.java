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
package org.apache.hc.core5.http.impl.nio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.message.BufferedHeader;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestHttp1FieldValidation {

    private static CharArrayBuffer line(final String text) {
        final CharArrayBuffer buffer = new CharArrayBuffer(text.length());
        buffer.append(text);
        return buffer;
    }

    @Test
    void testRejectsRawCRLFWithUtf8Encoder() {
        final CharArrayBuffer original = line("X-Test: ok\r\nX-Injected: true");
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(BufferedHeader.create(original));
        final SessionOutputBufferImpl output = new SessionOutputBufferImpl(
                256, 256, StandardCharsets.UTF_8.newEncoder());
        Assertions.assertThrows(ProtocolException.class,
                () -> new DefaultHttpRequestWriter<>().write(request, output));
        Assertions.assertEquals("X-Test: ok\r\nX-Injected: true", original.toString());
    }

    @Test
    void testRejectsInvalidNameAndNul() {
        final BasicHttpRequest badName = new BasicHttpRequest("GET", "/");
        badName.addHeader(new BasicHeader("X-Test\r\nX-Injected", "true"));
        Assertions.assertThrows(ProtocolException.class,
                () -> new DefaultHttpRequestWriter<>().write(badName,
                        new SessionOutputBufferImpl(256, 256, StandardCharsets.UTF_8.newEncoder())));

        final BasicHttpRequest nul = new BasicHttpRequest("GET", "/");
        nul.addHeader(new BasicHeader("X-Test", "a\0b"));
        Assertions.assertThrows(ProtocolException.class,
                () -> new DefaultHttpRequestWriter<>().write(nul,
                        new SessionOutputBufferImpl(256, 256, StandardCharsets.UTF_8.newEncoder())));
    }

    @Test
    void testRejectsResponseHeader() {
        final BasicHttpResponse response = new BasicHttpResponse(200);
        response.addHeader(BufferedHeader.create(line("Set-Cookie: a=b\r\nX-Injected: true")));
        Assertions.assertThrows(ProtocolException.class,
                () -> new DefaultHttpResponseWriter<>().write(response,
                        new SessionOutputBufferImpl(256, 256, StandardCharsets.UTF_8.newEncoder())));
    }

    @Test
    void testRejectsTrailerBeforeLastChunk() {
        final SessionOutputBufferImpl output = new SessionOutputBufferImpl(
                256, 256, StandardCharsets.UTF_8.newEncoder());
        final ChunkEncoder encoder = new ChunkEncoder(
                Channels.newChannel(new ByteArrayOutputStream()), output, new BasicHttpTransportMetrics());
        final IOException ex = Assertions.assertThrows(IOException.class,
                () -> encoder.complete(Collections.singletonList(BufferedHeader.create(
                        line("X-Trailer: ok\r\nX-Injected: true")))));
        Assertions.assertInstanceOf(ProtocolException.class, ex.getCause());
        Assertions.assertEquals(0, output.length(), "No closing chunk must be buffered");
    }

    @Test
    void testValidFieldsPreserveWireFormat() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(BufferedHeader.create(line("X-Raw:   original   formatting")));
        request.addHeader(new BasicHeader("X-Normal", "abc\r\ndef"));
        final SessionOutputBufferImpl output = new SessionOutputBufferImpl(
                256, 256, StandardCharsets.UTF_8.newEncoder());
        new DefaultHttpRequestWriter<>().write(request, output);
        final ByteArrayOutputStream wire = new ByteArrayOutputStream();
        while (output.hasData()) {
            output.flush(Channels.newChannel(wire));
        }
        final String serialized = new String(wire.toByteArray(), StandardCharsets.ISO_8859_1);
        Assertions.assertTrue(serialized.contains("\r\nX-Raw:   original   formatting\r\n"), serialized);
        Assertions.assertTrue(serialized.contains("\r\nX-Normal: abc  def\r\n"), serialized);
    }
}
