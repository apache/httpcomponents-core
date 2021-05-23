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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.util.Timeout;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class TestSharedInputBuffer {

    private static final Timeout TIMEOUT = Timeout.ofSeconds(30);

    @Test
    public void testBasis() throws Exception {

        final Charset charset = StandardCharsets.US_ASCII;
        final SharedInputBuffer inputBuffer = new SharedInputBuffer(10);
        inputBuffer.fill(charset.encode("1234567890"));
        Assert.assertEquals(10, inputBuffer.length());

        final CapacityChannel capacityChannel = Mockito.mock(CapacityChannel.class);

        inputBuffer.updateCapacity(capacityChannel);
        Mockito.verifyNoInteractions(capacityChannel);

        inputBuffer.fill(charset.encode("1234567890"));
        inputBuffer.fill(charset.encode("1234567890"));
        Assert.assertEquals(30, inputBuffer.length());

        Mockito.verifyNoInteractions(capacityChannel);

        final byte[] tmp = new byte[20];
        final int bytesRead1 = inputBuffer.read(tmp, 0, tmp.length);
        Assert.assertEquals(20, bytesRead1);
        Mockito.verifyNoInteractions(capacityChannel);

        inputBuffer.markEndStream();

        Assert.assertEquals('1', inputBuffer.read());
        Assert.assertEquals('2', inputBuffer.read());
        final int bytesRead2 = inputBuffer.read(tmp, 0, tmp.length);
        Assert.assertEquals(8, bytesRead2);
        Mockito.verifyNoInteractions(capacityChannel);
        Assert.assertEquals(-1, inputBuffer.read(tmp, 0, tmp.length));
        Assert.assertEquals(-1, inputBuffer.read(tmp, 0, tmp.length));
        Assert.assertEquals(-1, inputBuffer.read());
        Assert.assertEquals(-1, inputBuffer.read());
    }

    @Test
    public void testMultithreadingRead() throws Exception {

        final SharedInputBuffer inputBuffer = new SharedInputBuffer(10);

        final CapacityChannel capacityChannel = Mockito.mock(CapacityChannel.class);

        inputBuffer.updateCapacity(capacityChannel);
        Mockito.verify(capacityChannel).update(10);
        Mockito.reset(capacityChannel);

        final ExecutorService executorService = Executors.newFixedThreadPool(2);
        final Future<Boolean> task1 = executorService.submit(() -> {
            final Charset charset = StandardCharsets.US_ASCII;
            inputBuffer.fill(charset.encode("1234567890"));
            return Boolean.TRUE;
        });
        final Future<Integer> task2 = executorService.submit(() -> {
            final byte[] tmp = new byte[20];
            return inputBuffer.read(tmp, 0, tmp.length);
        });

        Assert.assertEquals(Boolean.TRUE, task1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        Assert.assertEquals(Integer.valueOf(10), task2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        Mockito.verify(capacityChannel).update(10);
    }

    @Test
    public void testMultithreadingSingleRead() throws Exception {

        final SharedInputBuffer inputBuffer = new SharedInputBuffer(10);

        final CapacityChannel capacityChannel = Mockito.mock(CapacityChannel.class);

        inputBuffer.updateCapacity(capacityChannel);
        Mockito.verify(capacityChannel).update(10);
        Mockito.reset(capacityChannel);

        final ExecutorService executorService = Executors.newFixedThreadPool(2);
        final Future<Boolean> task1 = executorService.submit(() -> {
            final Charset charset = StandardCharsets.US_ASCII;
            inputBuffer.fill(charset.encode("a"));
            return Boolean.TRUE;
        });
        final Future<Integer> task2 = executorService.submit((Callable<Integer>) inputBuffer::read);

        Assert.assertEquals(Boolean.TRUE, task1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        Assert.assertEquals(Integer.valueOf('a'), task2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        Mockito.verify(capacityChannel).update(10);
    }

    @Test
    public void testMultithreadingReadStream() throws Exception {

        final SharedInputBuffer inputBuffer = new SharedInputBuffer(10);

        final CapacityChannel capacityChannel = Mockito.mock(CapacityChannel.class);

        inputBuffer.updateCapacity(capacityChannel);
        Mockito.verify(capacityChannel).update(10);
        Mockito.reset(capacityChannel);

        final ExecutorService executorService = Executors.newFixedThreadPool(2);
        final Future<Boolean> task1 = executorService.submit(() -> {
            final Charset charset = StandardCharsets.US_ASCII;
            final Random rnd = new Random(System.currentTimeMillis());
            for (int i = 0; i < 5; i++) {
                inputBuffer.fill(charset.encode("1234567890"));
                Thread.sleep(rnd.nextInt(250));
            }
            inputBuffer.markEndStream();
            return Boolean.TRUE;
        });
        final Future<String> task2 = executorService.submit(() -> {
            final Charset charset = StandardCharsets.US_ASCII;
            final StringBuilder buf = new StringBuilder();
            final byte[] tmp = new byte[10];
            int l;
            while ((l = inputBuffer.read(tmp, 0, tmp.length)) != -1) {
                buf.append(charset.decode(ByteBuffer.wrap(tmp, 0, l)));
            }
            return buf.toString();
        });

        Assert.assertEquals(Boolean.TRUE, task1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        Assert.assertEquals("12345678901234567890123456789012345678901234567890",
                task2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        Mockito.verify(capacityChannel, Mockito.atLeast(1)).update(ArgumentMatchers.anyInt());
    }

    @Test
    public void testMultithreadingReadStreamAbort() throws Exception {

        final SharedInputBuffer inputBuffer = new SharedInputBuffer(10);

        final CapacityChannel capacityChannel = Mockito.mock(CapacityChannel.class);

        inputBuffer.updateCapacity(capacityChannel);
        Mockito.verify(capacityChannel).update(10);
        Mockito.reset(capacityChannel);

        final ExecutorService executorService = Executors.newFixedThreadPool(2);
        final Future<Boolean> task1 = executorService.submit(() -> {
            Thread.sleep(1000);
            inputBuffer.abort();
            return Boolean.TRUE;
        });
        final Future<Integer> task2 = executorService.submit((Callable<Integer>) inputBuffer::read);

        Assert.assertEquals(Boolean.TRUE, task1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        Assert.assertEquals(Integer.valueOf(-1), task2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        Mockito.verify(capacityChannel, Mockito.never()).update(10);
    }

}

