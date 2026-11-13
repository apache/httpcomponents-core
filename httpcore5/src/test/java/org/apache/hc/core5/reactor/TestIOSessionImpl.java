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
package org.apache.hc.core5.reactor;

import java.io.ByteArrayOutputStream;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.io.CloseMode;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestIOSessionImpl {

    private static class TestCommand implements Command {
        final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public boolean cancel() {
            cancelled.set(true);
            return true;
        }
    }

    private static class WriteRecordingChannel extends SocketChannel {

        final List<Integer> writeSizes = new ArrayList<>();
        private final ByteArrayOutputStream data = new ByteArrayOutputStream();
        private int maxBytesPerWrite = Integer.MAX_VALUE;

        WriteRecordingChannel() {
            super(null);
        }

        @Override
        public int write(final ByteBuffer src) {
            writeSizes.add(src.remaining());
            final int chunk = Math.min(maxBytesPerWrite, src.remaining());
            final byte[] bytes = new byte[chunk];
            src.get(bytes);
            data.write(bytes, 0, chunk);
            return chunk;
        }

        @Override
        public long write(final ByteBuffer[] srcs, final int offset, final int length) {
            long total = 0;
            for (int i = offset; i < offset + length; i++) {
                total += write(srcs[i]);
            }
            return total;
        }

        byte[] toByteArray() {
            return data.toByteArray();
        }

        @Override
        public int read(final ByteBuffer dst) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long read(final ByteBuffer[] dsts, final int offset, final int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.net.Socket socket() {
            throw new UnsupportedOperationException();
        }

        @Override
        public SocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public SocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public boolean isConnectionPending() {
            return false;
        }

        @Override
        public boolean connect(final SocketAddress remote) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean finishConnect() {
            throw new UnsupportedOperationException();
        }

        @Override
        public SocketChannel shutdownInput() {
            throw new UnsupportedOperationException();
        }

        @Override
        public SocketChannel shutdownOutput() {
            throw new UnsupportedOperationException();
        }

        @Override
        public SocketChannel bind(final SocketAddress local) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> SocketChannel setOption(final SocketOption<T> name, final T value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T getOption(final SocketOption<T> name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<SocketOption<?>> supportedOptions() {
            return Collections.emptySet();
        }

        @Override
        protected void implCloseSelectableChannel() {
        }

        @Override
        protected void implConfigureBlocking(final boolean block) {
        }
    }

    @Test
    @SuppressWarnings("resource")
    void enqueueUpdatesInterestOpsAndOrders() throws Exception {
        try (Selector selector = Selector.open();
             SocketChannel channel = SocketChannel.open()) {
            channel.configureBlocking(false);
            final SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
            final AtomicReference<IOSession> closedRef = new AtomicReference<>();
            final IOSessionImpl session = new IOSessionImpl("t", key, channel, closedRef::set);
            try {
                final TestCommand normal = new TestCommand();
                final TestCommand immediate = new TestCommand();

                session.enqueue(normal, Command.Priority.NORMAL);
                session.enqueue(immediate, Command.Priority.IMMEDIATE);

                Assertions.assertThat(session.hasCommands()).isTrue();
                Assertions.assertThat(session.getPendingCommandCount()).isEqualTo(2);
                Assertions.assertThat(session.poll()).isSameAs(immediate);
                Assertions.assertThat(session.poll()).isSameAs(normal);

                Assertions.assertThat(key.interestOps()).isEqualTo(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            } finally {
                session.close(CloseMode.IMMEDIATE);
                Assertions.assertThat(closedRef.get()).isSameAs(session);
            }
        }
    }

    @Test
    @SuppressWarnings("resource")
    void enqueueOnClosedCancels() throws Exception {
        try (Selector selector = Selector.open();
             SocketChannel channel = SocketChannel.open()) {
            channel.configureBlocking(false);
            final SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
            final IOSessionImpl session = new IOSessionImpl("t", key, channel, null);
            try {
                session.close(CloseMode.IMMEDIATE);

                final TestCommand command = new TestCommand();
                session.enqueue(command, Command.Priority.NORMAL);

                Assertions.assertThat(command.cancelled.get()).isTrue();
            } finally {
                session.close(CloseMode.IMMEDIATE);
            }
        }
    }

    @Test
    @SuppressWarnings("resource")
    void eventMaskHelpersWork() throws Exception {
        try (Selector selector = Selector.open();
             SocketChannel channel = SocketChannel.open()) {
            channel.configureBlocking(false);
            final SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
            final IOSessionImpl session = new IOSessionImpl("t", key, channel, null);
            try {
                session.setEvent(SelectionKey.OP_WRITE);
                Assertions.assertThat(session.getEventMask()).isEqualTo(SelectionKey.OP_READ | SelectionKey.OP_WRITE);

                session.clearEvent(SelectionKey.OP_READ);
                Assertions.assertThat(session.getEventMask()).isEqualTo(SelectionKey.OP_WRITE);

                session.setEventMask(SelectionKey.OP_READ);
                Assertions.assertThat(session.getEventMask()).isEqualTo(SelectionKey.OP_READ);
            } finally {
                session.close(CloseMode.IMMEDIATE);
            }
        }
    }

    @Test
    @SuppressWarnings("resource")
    void eventMaskIsEmptyOnceClosed() throws Exception {
        try (Selector selector = Selector.open();
             SocketChannel channel = SocketChannel.open()) {
            channel.configureBlocking(false);
            final SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
            final IOSessionImpl session = new IOSessionImpl("t", key, channel, null);
            session.close(CloseMode.IMMEDIATE);

            Assertions.assertThat(session.getEventMask()).isEqualTo(0);
        }
    }

    @Test
    @SuppressWarnings("resource")
    void toStringIncludesStatus() throws Exception {
        try (Selector selector = Selector.open();
             SocketChannel channel = SocketChannel.open()) {
            channel.configureBlocking(false);
            final SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
            final IOSessionImpl session = new IOSessionImpl("t", key, channel, null);
            try {
                final String text = session.toString();
                Assertions.assertThat(text.contains("ACTIVE")).isTrue();
            } finally {
                session.close(CloseMode.IMMEDIATE);
            }
        }
    }

    @Test
    @SuppressWarnings("resource")
    void largeHeapBufferWriteIsChunked() throws Exception {
        try (Selector selector = Selector.open();
             SocketChannel channel = SocketChannel.open()) {
            channel.configureBlocking(false);
            final SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
            final WriteRecordingChannel mockChannel = new WriteRecordingChannel();
            final IOSessionImpl session = new IOSessionImpl("t", key, mockChannel, null);

            final byte[] content = new byte[16 * 1024 * 2 + 16];
            for (int i = 0; i < content.length; i++) {
                content[i] = (byte) i;
            }
            final ByteBuffer src = ByteBuffer.wrap(content);

            final int bytesWritten = session.write(src);

            Assertions.assertThat(bytesWritten).isEqualTo(content.length);
            Assertions.assertThat(src.hasRemaining()).isFalse();
            Assertions.assertThat(mockChannel.writeSizes.size()).isEqualTo(3);
            for (final int size : mockChannel.writeSizes) {
                Assertions.assertThat(size <= 16 * 1024).isTrue();
            }
            Assertions.assertThat(mockChannel.toByteArray()).containsExactly(content);
        }
    }

    @Test
    @SuppressWarnings("resource")
    void smallHeapBufferWriteIsNotChunked() throws Exception {
        try (Selector selector = Selector.open();
             SocketChannel channel = SocketChannel.open()) {
            channel.configureBlocking(false);
            final SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
            final WriteRecordingChannel mockChannel = new WriteRecordingChannel();
            final IOSessionImpl session = new IOSessionImpl("t", key, mockChannel, null);

            final byte[] content = new byte[100];
            final ByteBuffer src = ByteBuffer.wrap(content);

            final int bytesWritten = session.write(src);

            Assertions.assertThat(bytesWritten).isEqualTo(content.length);
            Assertions.assertThat(mockChannel.writeSizes.size()).isEqualTo(1);
            Assertions.assertThat(mockChannel.writeSizes.get(0)).isEqualTo(content.length);
            Assertions.assertThat(mockChannel.toByteArray()).containsExactly(content);
        }
    }

    @Test
    @SuppressWarnings("resource")
    void directBufferWriteIsNotChunked() throws Exception {
        try (Selector selector = Selector.open();
             SocketChannel channel = SocketChannel.open()) {
            channel.configureBlocking(false);
            final SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
            final WriteRecordingChannel mockChannel = new WriteRecordingChannel();
            final IOSessionImpl session = new IOSessionImpl("t", key, mockChannel, null);

            final int contentSize = 16 * 1024 * 3;
            final ByteBuffer src = ByteBuffer.allocateDirect(contentSize);
            for (int i = 0; i < contentSize; i++) {
                src.put((byte) i);
            }
            src.flip();

            final int bytesWritten = session.write(src);

            Assertions.assertThat(bytesWritten).isEqualTo(contentSize);
            Assertions.assertThat(mockChannel.writeSizes.size()).isEqualTo(1);
            Assertions.assertThat(mockChannel.writeSizes.get(0)).isEqualTo(contentSize);
        }
    }

    @Test
    @SuppressWarnings("resource")
    void partialWriteDrainsAll() throws Exception {
        try (Selector selector = Selector.open();
             SocketChannel channel = SocketChannel.open()) {
            channel.configureBlocking(false);
            final SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
            final WriteRecordingChannel mockChannel = new WriteRecordingChannel();
            mockChannel.maxBytesPerWrite = 8 * 1024;
            final IOSessionImpl session = new IOSessionImpl("t", key, mockChannel, null);

            final byte[] content = new byte[16 * 1024 * 2 + 16];
            for (int i = 0; i < content.length; i++) {
                content[i] = (byte) i;
            }
            final ByteBuffer src = ByteBuffer.wrap(content);

            final int bytesWritten = session.write(src);

            Assertions.assertThat(bytesWritten).isEqualTo(content.length);
            Assertions.assertThat(src.hasRemaining()).isFalse();
            Assertions.assertThat(mockChannel.toByteArray()).containsExactly(content);
        }
    }

    @Test
    @SuppressWarnings("resource")
    void saturatedChannelWriteReturnsZero() throws Exception {
        try (Selector selector = Selector.open();
             SocketChannel channel = SocketChannel.open()) {
            channel.configureBlocking(false);
            final SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
            final WriteRecordingChannel mockChannel = new WriteRecordingChannel();
            mockChannel.maxBytesPerWrite = 0;
            final IOSessionImpl session = new IOSessionImpl("t", key, mockChannel, null);

            final byte[] content = new byte[16 * 1024 * 2];
            final ByteBuffer src = ByteBuffer.wrap(content);

            final int bytesWritten = session.write(src);

            Assertions.assertThat(bytesWritten).isEqualTo(0);
            Assertions.assertThat(src.hasRemaining()).isTrue();
        }
    }
}
