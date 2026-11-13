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

import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.util.Timeout;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class TestSharedInputBuffer {

    private static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @Test
    void testBasis() throws Exception {

        final Charset charset = StandardCharsets.US_ASCII;
        final SharedInputBuffer inputBuffer = new SharedInputBuffer(10);
        inputBuffer.fill(charset.encode("1234567890"));
        Assertions.assertThat(inputBuffer.length()).isEqualTo(10);

        final CapacityChannel capacityChannel = Mockito.mock(CapacityChannel.class);

        inputBuffer.updateCapacity(capacityChannel);
        Mockito.verifyNoInteractions(capacityChannel);

        inputBuffer.fill(charset.encode("1234567890"));
        inputBuffer.fill(charset.encode("1234567890"));
        Assertions.assertThat(inputBuffer.length()).isEqualTo(30);

        Mockito.verifyNoInteractions(capacityChannel);

        final byte[] tmp = new byte[20];
        final int bytesRead1 = inputBuffer.read(tmp, 0, tmp.length);
        Assertions.assertThat(bytesRead1).isEqualTo(20);
        Mockito.verifyNoInteractions(capacityChannel);

        inputBuffer.markEndStream();

        Assertions.assertThat(inputBuffer.read()).isEqualTo('1');
        Assertions.assertThat(inputBuffer.read()).isEqualTo('2');
        final int bytesRead2 = inputBuffer.read(tmp, 0, tmp.length);
        Assertions.assertThat(bytesRead2).isEqualTo(8);
        Mockito.verifyNoInteractions(capacityChannel);
        Assertions.assertThat(inputBuffer.read(tmp, 0, tmp.length)).isEqualTo(-1);
        Assertions.assertThat(inputBuffer.read(tmp, 0, tmp.length)).isEqualTo(-1);
        Assertions.assertThat(inputBuffer.read()).isEqualTo(-1);
        Assertions.assertThat(inputBuffer.read()).isEqualTo(-1);
    }

    @Test
    void testMultithreadingRead() throws Exception {

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

        Assertions.assertThat(task1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit())).isEqualTo(Boolean.TRUE);
        Assertions.assertThat(task2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit())).isEqualTo(Integer.valueOf(10));
        Mockito.verify(capacityChannel).update(10);
    }

    @Test
    void testMultithreadingSingleRead() throws Exception {

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

        Assertions.assertThat(task1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit())).isEqualTo(Boolean.TRUE);
        Assertions.assertThat(task2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit())).isEqualTo(Integer.valueOf('a'));
        Mockito.verify(capacityChannel).update(1);
    }

    @Test
    void testMultithreadingReadStream() throws Exception {

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

        Assertions.assertThat(task1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit())).isEqualTo(Boolean.TRUE);
        Assertions.assertThat(task2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit())).isEqualTo("12345678901234567890123456789012345678901234567890");
        Mockito.verify(capacityChannel, Mockito.atLeast(1)).update(ArgumentMatchers.anyInt());
    }

    @Test
    void testMultithreadingReadStreamAbort() throws Exception {

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

        Assertions.assertThat(task1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit())).isEqualTo(Boolean.TRUE);
        final ExecutionException ex = Assertions.assertThatThrownBy(() ->
                        task2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()))
                .isInstanceOf(ExecutionException.class).asInstanceOf(InstanceOfAssertFactories.type(ExecutionException.class)).actual();
        Assertions.assertThat(ex.getCause()).isInstanceOf(InterruptedIOException.class);
        Mockito.verify(capacityChannel, Mockito.never()).update(10);
    }

}

