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

package org.apache.http.impl.nio.codecs;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.nio.reactor.SessionOutputBufferImpl;
import org.apache.http.nio.reactor.SessionOutputBuffer;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EncodingUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * Simple tests for {@link IdentityEncoder}.
 */
public class TestIdentityEncoder {

    private static ByteBuffer wrap(final String s) {
        return ByteBuffer.wrap(EncodingUtils.getAsciiBytes(s));
    }

    private static WritableByteChannel newChannel(final ByteArrayOutputStream baos) {
        return Channels.newChannel(baos);
    }

    @Test
    public void testBasicCoding() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        WritableByteChannel channel = newChannel(baos);
        HttpParams params = new BasicHttpParams();
        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();

        IdentityEncoder encoder = new IdentityEncoder(channel, outbuf, metrics);
        encoder.write(wrap("stuff"));
        encoder.complete();

        String s = baos.toString("US-ASCII");

        Assert.assertTrue(encoder.isCompleted());
        Assert.assertEquals("stuff", s);
    }

    @Test
    public void testCodingEmptyBuffer() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        WritableByteChannel channel = newChannel(baos);
        HttpParams params = new BasicHttpParams();
        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();

        IdentityEncoder encoder = new IdentityEncoder(channel, outbuf, metrics);
        encoder.write(wrap("stuff"));

        ByteBuffer empty = ByteBuffer.allocate(100);
        empty.flip();
        encoder.write(empty);
        encoder.write(null);

        encoder.complete();

        String s = baos.toString("US-ASCII");

        Assert.assertTrue(encoder.isCompleted());
        Assert.assertEquals("stuff", s);
    }

    @Test
    public void testCodingCompleted() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        WritableByteChannel channel = newChannel(baos);
        HttpParams params = new BasicHttpParams();
        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128, params);
        HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();

        IdentityEncoder encoder = new IdentityEncoder(channel, outbuf, metrics);
        encoder.write(wrap("stuff"));
        encoder.complete();

        try {
            encoder.write(wrap("more stuff"));
            Assert.fail("IllegalStateException should have been thrown");
        } catch (IllegalStateException ex) {
            // ignore
        }
    }

    @Test
    public void testInvalidConstructor() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        WritableByteChannel channel = newChannel(baos);
        HttpParams params = new BasicHttpParams();
        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128, params);

        try {
            new IdentityEncoder(null, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        try {
            new IdentityEncoder(channel, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        try {
            new IdentityEncoder(channel, outbuf, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore
        }
    }

}
