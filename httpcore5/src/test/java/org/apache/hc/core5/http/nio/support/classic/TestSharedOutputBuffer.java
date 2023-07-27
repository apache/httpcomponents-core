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

package org.apache.hc.core5.http.nio.support.classic;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.WritableByteChannelMock;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class TestSharedOutputBuffer {

    private static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    static class DataStreamChannelMock implements DataStreamChannel {

        private final WritableByteChannelMock channel;

        private final ReentrantLock lock;
        private final Condition condition;

        DataStreamChannelMock(final WritableByteChannelMock channel) {
            this.channel = channel;
            this.lock = new ReentrantLock();
            this.condition = lock.newCondition();
        }

        @Override
        public int write(final ByteBuffer src) throws IOException {
            lock.lock();
            try {
                return channel.write(src);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void requestOutput() {
            lock.lock();
            try {
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void endStream(final List<? extends Header> trailers) throws IOException {
            lock.lock();
            try {
                channel.close();
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void endStream() throws IOException {
            endStream(null);
        }

        public void awaitOutputRequest() throws InterruptedException {
            lock.lock();
            try {
                condition.await();
            } finally {
                lock.unlock();
            }
        }

    }

    @Test
    public void testBasis() throws Exception {

        final Charset charset = StandardCharsets.US_ASCII;
        final SharedOutputBuffer outputBuffer = new SharedOutputBuffer(30);

        final WritableByteChannelMock channel = new WritableByteChannelMock(1024);
        final DataStreamChannel dataStreamChannel = Mockito.spy(new DataStreamChannelMock(channel));
        outputBuffer.flush(dataStreamChannel);

        Mockito.verifyNoInteractions(dataStreamChannel);

        Assertions.assertEquals(0, outputBuffer.length());
        Assertions.assertEquals(30, outputBuffer.capacity());

        final byte[] tmp = "1234567890".getBytes(charset);
        outputBuffer.write(tmp, 0, tmp.length);
        outputBuffer.write(tmp, 0, tmp.length);
        outputBuffer.write('1');
        outputBuffer.write('2');

        Assertions.assertEquals(22, outputBuffer.length());
        Assertions.assertEquals(8, outputBuffer.capacity());

        Mockito.verifyNoInteractions(dataStreamChannel);
    }

    @Test
    public void testFlush() throws Exception {

        final Charset charset = StandardCharsets.US_ASCII;
        final SharedOutputBuffer outputBuffer = new SharedOutputBuffer(30);

        final WritableByteChannelMock channel = new WritableByteChannelMock(1024);
        final DataStreamChannel dataStreamChannel = new DataStreamChannelMock(channel);
        outputBuffer.flush(dataStreamChannel);

        Assertions.assertEquals(0, outputBuffer.length());
        Assertions.assertEquals(30, outputBuffer.capacity());

        final byte[] tmp = "1234567890".getBytes(charset);
        outputBuffer.write(tmp, 0, tmp.length);
        outputBuffer.write(tmp, 0, tmp.length);
        outputBuffer.write('1');
        outputBuffer.write('2');

        outputBuffer.flush(dataStreamChannel);

        Assertions.assertEquals(0, outputBuffer.length());
        Assertions.assertEquals(30, outputBuffer.capacity());
    }

    @Test
    public void testMultithreadingWriteStream() throws Exception {

        final Charset charset = StandardCharsets.US_ASCII;
        final SharedOutputBuffer outputBuffer = new SharedOutputBuffer(20);

        final WritableByteChannelMock channel = new WritableByteChannelMock(1024);
        final DataStreamChannelMock dataStreamChannel = new DataStreamChannelMock(channel);

        final ExecutorService executorService = Executors.newFixedThreadPool(2);
        final Future<Boolean> task1 = executorService.submit(() -> {
            final byte[] tmp = "1234567890".getBytes(charset);
            outputBuffer.write(tmp, 0, tmp.length);
            outputBuffer.write(tmp, 0, tmp.length);
            outputBuffer.write('1');
            outputBuffer.write('2');
            outputBuffer.write(tmp, 0, tmp.length);
            outputBuffer.write(tmp, 0, tmp.length);
            outputBuffer.write(tmp, 0, tmp.length);
            outputBuffer.writeCompleted();
            outputBuffer.writeCompleted();
            return Boolean.TRUE;
        });
        final Future<Boolean> task2 = executorService.submit(() -> {
            for (;;) {
                outputBuffer.flush(dataStreamChannel);
                if (outputBuffer.isEndStream()) {
                    break;
                }
                if (!outputBuffer.hasData()) {
                    dataStreamChannel.awaitOutputRequest();
                }
            }
            return Boolean.TRUE;
        });

        Assertions.assertEquals(Boolean.TRUE, task1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        Assertions.assertEquals(Boolean.TRUE, task2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));

        Assertions.assertEquals("1234567890123456789012123456789012345678901234567890", new String(channel.toByteArray(), charset));
    }

    @Test
    public void testMultithreadingWriteStreamAbort() throws Exception {

        final Charset charset = StandardCharsets.US_ASCII;
        final SharedOutputBuffer outputBuffer = new SharedOutputBuffer(20);

        final ExecutorService executorService = Executors.newFixedThreadPool(2);
        final Future<Boolean> task1 = executorService.submit(() -> {
            final byte[] tmp = "1234567890".getBytes(charset);
            for (int i = 0; i < 20; i++) {
                outputBuffer.write(tmp, 0, tmp.length);
            }
            outputBuffer.writeCompleted();
            return Boolean.TRUE;
        });
        final Future<Boolean> task2 = executorService.submit(() -> {
            Thread.sleep(200);
            outputBuffer.abort();
            return Boolean.TRUE;
        });

        Assertions.assertEquals(Boolean.TRUE, task2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        try {
            task1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        } catch (final ExecutionException ex) {
            Assertions.assertTrue(ex.getCause() instanceof InterruptedIOException);
        }
    }

    @Test
    public void testEndStreamOnlyCalledOnce() throws IOException {

        final DataStreamChannel channel = Mockito.mock(DataStreamChannel.class);
        final SharedOutputBuffer outputBuffer = new SharedOutputBuffer(20);

        outputBuffer.flush(channel);

        outputBuffer.writeCompleted();
        outputBuffer.flush(channel);

        Mockito.verify(channel, Mockito.times(1)).endStream();
    }

}

