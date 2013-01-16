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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

import org.apache.http.Consts;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.nio.reactor.SessionOutputBufferImpl;
import org.apache.http.nio.reactor.SessionOutputBuffer;
import org.apache.http.util.EncodingUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * Simple tests for {@link LengthDelimitedEncoder}.
 */
public class TestLengthDelimitedEncoder {

    private static ByteBuffer wrap(final String s) {
        return ByteBuffer.wrap(EncodingUtils.getAsciiBytes(s));
    }

    private static WritableByteChannel newChannel(final ByteArrayOutputStream baos) {
        return Channels.newChannel(baos);
    }

    @Test
    public void testBasicCoding() throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final WritableByteChannel channel = newChannel(baos);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(
                channel, outbuf, metrics, 16);
        encoder.write(wrap("stuff;"));
        encoder.write(wrap("more stuff"));

        final String s = baos.toString("US-ASCII");

        Assert.assertTrue(encoder.isCompleted());
        Assert.assertEquals("stuff;more stuff", s);
    }

    @Test
    public void testCodingBeyondContentLimit() throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final WritableByteChannel channel = newChannel(baos);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(
                channel, outbuf, metrics, 16);
        encoder.write(wrap("stuff;"));
        encoder.write(wrap("more stuff; and a lot more stuff"));

        final String s = baos.toString("US-ASCII");

        Assert.assertTrue(encoder.isCompleted());
        Assert.assertEquals("stuff;more stuff", s);
    }

    @Test
    public void testCodingEmptyBuffer() throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final WritableByteChannel channel = newChannel(baos);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(
                channel, outbuf, metrics, 16);
        encoder.write(wrap("stuff;"));

        final ByteBuffer empty = ByteBuffer.allocate(100);
        empty.flip();
        encoder.write(empty);
        encoder.write(null);

        encoder.write(wrap("more stuff"));

        final String s = baos.toString("US-ASCII");

        Assert.assertTrue(encoder.isCompleted());
        Assert.assertEquals("stuff;more stuff", s);
    }

    @Test
    public void testCodingCompleted() throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final WritableByteChannel channel = newChannel(baos);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(
                channel, outbuf, metrics, 5);
        encoder.write(wrap("stuff"));

        try {
            encoder.write(wrap("more stuff"));
            Assert.fail("IllegalStateException should have been thrown");
        } catch (final IllegalStateException ex) {
            // ignore
        }
    }

    /* ----------------- FileChannel Part testing --------------------------- */
    @Test
    public void testCodingBeyondContentLimitFromFile() throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final WritableByteChannel channel = newChannel(baos);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(
                channel, outbuf, metrics, 16);

        final File tmpFile = File.createTempFile("testFile", ".txt");
        final FileOutputStream fout = new FileOutputStream(tmpFile);
        final OutputStreamWriter wrtout = new OutputStreamWriter(fout);

        wrtout.write("stuff;");
        wrtout.write("more stuff; and a lot more stuff");

        wrtout.flush();
        wrtout.close();

        final FileChannel fchannel = new FileInputStream(tmpFile).getChannel();

        encoder.transfer(fchannel, 0, 20);

        final String s = baos.toString("US-ASCII");

        Assert.assertTrue(encoder.isCompleted());
        Assert.assertEquals("stuff;more stuff", s);

        fchannel.close();

        deleteWithCheck(tmpFile);
    }

    private void deleteWithCheck(final File handle){
        if (!handle.delete() && handle.exists()){
            System.err.println("Failed to delete: "+handle.getPath());
        }
    }

    @Test
    public void testCodingEmptyFile() throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final WritableByteChannel channel = newChannel(baos);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(
                channel, outbuf, metrics, 16);
        encoder.write(wrap("stuff;"));

        //Create an empty file
        final File tmpFile = File.createTempFile("testFile", ".txt");
        final FileOutputStream fout = new FileOutputStream(tmpFile);
        final OutputStreamWriter wrtout = new OutputStreamWriter(fout);

        wrtout.flush();
        wrtout.close();

        final FileChannel fchannel = new FileInputStream(tmpFile).getChannel();

        encoder.transfer(fchannel, 0, 20);

        encoder.write(wrap("more stuff"));

        final String s = baos.toString("US-ASCII");

        Assert.assertTrue(encoder.isCompleted());
        Assert.assertEquals("stuff;more stuff", s);

        fchannel.close();
        deleteWithCheck(tmpFile);
    }

    @Test
    public void testCodingCompletedFromFile() throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final WritableByteChannel channel = newChannel(baos);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(
                channel, outbuf, metrics, 5);
        encoder.write(wrap("stuff"));

        final File tmpFile = File.createTempFile("testFile", ".txt");
        final FileOutputStream fout = new FileOutputStream(tmpFile);
        final OutputStreamWriter wrtout = new OutputStreamWriter(fout);

        wrtout.write("more stuff");

        wrtout.flush();
        wrtout.close();

        final FileChannel fchannel = new FileInputStream(tmpFile).getChannel();
        try {
            encoder.transfer(fchannel, 0, 10);
            Assert.fail("IllegalStateException should have been thrown");
        } catch (final IllegalStateException ex) {
            // ignore
        } finally {
            fchannel.close();
            deleteWithCheck(tmpFile);
        }
    }

    @Test
    public void testCodingFromFileSmaller() throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final WritableByteChannel channel = newChannel(baos);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();

        final LengthDelimitedEncoder encoder = new LengthDelimitedEncoder(
                channel, outbuf, metrics, 16);

        final File tmpFile = File.createTempFile("testFile", ".txt");
        final FileOutputStream fout = new FileOutputStream(tmpFile);
        final OutputStreamWriter wrtout = new OutputStreamWriter(fout);

        wrtout.write("stuff;");
        wrtout.write("more stuff;");

        wrtout.flush();
        wrtout.close();

        final FileChannel fchannel = new FileInputStream(tmpFile).getChannel();

        encoder.transfer(fchannel, 0, 20);

        final String s = baos.toString("US-ASCII");

        Assert.assertTrue(encoder.isCompleted());
        Assert.assertEquals("stuff;more stuff", s);

        fchannel.close();
        deleteWithCheck(tmpFile);
    }

    @Test
    public void testInvalidConstructor() {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final WritableByteChannel channel = newChannel(baos);
        final SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 128, Consts.ASCII);
        final HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();

        try {
            new LengthDelimitedEncoder(null, null, null, 10);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // ignore
        }
        try {
            new LengthDelimitedEncoder(channel, null, null, 10);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // ignore
        }
        try {
            new LengthDelimitedEncoder(channel, outbuf, null, 10);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // ignore
        }
        try {
            new LengthDelimitedEncoder(channel, outbuf, metrics, -10);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // ignore
        }
    }

}
