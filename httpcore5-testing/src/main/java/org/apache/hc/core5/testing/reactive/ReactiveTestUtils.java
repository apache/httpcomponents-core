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

package org.apache.hc.core5.testing.reactive;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.util.TextUtils;
import org.reactivestreams.Publisher;

import io.reactivex.Emitter;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;

public class ReactiveTestUtils {
    /** The range from which to generate random data. */
    private final static byte[] RANGE = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            .getBytes(StandardCharsets.US_ASCII);

    /**
     * Produces a deterministic stream of bytes, in randomly sized chunks of up to 128kB.
     *
     * @param length the number of bytes in the stream
     * @return a reactive stream of bytes
     */
    public static Flowable<ByteBuffer> produceStream(final long length) {
        return produceStream(length, null);
    }

    /**
     * Produces a deterministic stream of bytes, in randomly sized chunks of up to 128kB, while computing the hash of
     * the random data.
     *
     * @param length the number of bytes in the stream
     * @param hash an output argument for the hash, set when the end of the stream is reached; if {@code null}, the
     *             hash will not be computed
     * @return a reactive stream of bytes
     */
    public static Flowable<ByteBuffer> produceStream(final long length, final AtomicReference<String> hash) {
        return produceStream(length, 128 * 1024, hash);
    }

    /**
     * Produces a deterministic stream of bytes, in randomly sized chunks, while computing the hash of the random data.
     *
     * @param length the number of bytes in the stream
     * @param maximumBlockSize the maximum size of any {@code ByteBuffer in the stream}
     * @param hash an output argument for the hash, set when the end of the stream is reached; if {@code null}, the
     *             hash will not be computed
     * @return a reactive stream of bytes
     */
    public static Flowable<ByteBuffer> produceStream(
            final long length,
            final int maximumBlockSize,
            final AtomicReference<String> hash
    ) {
        return Flowable.generate(new Consumer<Emitter<ByteBuffer>>() {
            final Random random = new Random(length); // Use the length as the random seed for easy reproducibility
            long bytesEmitted;
            final MessageDigest md = newMessageDigest();

            @Override
            public void accept(final Emitter<ByteBuffer> emitter) {
                final long remainingLength = length - bytesEmitted;
                if (remainingLength == 0) {
                    emitter.onComplete();
                    if (hash != null) {
                        hash.set(TextUtils.toHexString(md.digest()));
                    }
                } else {
                    final int bufferLength = (int) Math.min(remainingLength, 1 + random.nextInt(maximumBlockSize));
                    final byte[] bs = new byte[bufferLength];
                    for (int i = 0; i < bufferLength; i++) {
                        final byte b = RANGE[(int) (random.nextDouble() * RANGE.length)];
                        bs[i] = b;
                    }
                    if (hash != null) {
                        md.update(bs);
                    }
                    emitter.onNext(ByteBuffer.wrap(bs));
                    bytesEmitted += bufferLength;
                }
            }
        });
    }

    /**
     * Computes the hash of the deterministic stream (as produced by {@link #produceStream(long)}).
     */
    public static String getStreamHash(final long length) {
        return TextUtils.toHexString(consumeStream(produceStream(length)).blockingGet().md.digest());
    }

    /**
     * Consumes the given stream and returns a data structure containing its length and digest.
     */
    public static Single<StreamDescription> consumeStream(final Publisher<ByteBuffer> publisher) {
        final StreamDescription seed = new StreamDescription(0, newMessageDigest());
        return Flowable.fromPublisher(publisher)
                .reduce(seed, (desc, byteBuffer) -> {
                    final long length = desc.length + byteBuffer.remaining();
                    desc.md.update(byteBuffer);
                    return new StreamDescription(length, desc.md);
                });
    }

    private static MessageDigest newMessageDigest() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (final NoSuchAlgorithmException ex) {
            throw new AssertionError(ex);
        }
    }

    public static class StreamDescription {
        public final long length;
        public final MessageDigest md;

        public StreamDescription(final long length, final MessageDigest md) {
            this.length = length;
            this.md = md;
        }
    }
}
