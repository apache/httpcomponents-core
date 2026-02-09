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

package org.apache.hc.core5.http;

import org.apache.hc.core5.util.Args;

/**
 * Generic message consisting of a message head and a message body.
 *
 * @param <H> message head type.
 * @param <B> message body type.
 *
 * @since 5.0
 */
public final class Message<H extends MessageHeaders, B> {

    private final H head;
    private final B body;
    private final Object error;

    /**
     * Create a new message with the given head and body, and no error.
     *
     * @param head The message head.
     * @param body The message body.
     *
     * @since 5.5
     */
    public static <H extends MessageHeaders, B> Message<H, B> of(final H head, final B body) {
        return new Message<>(head, body, null);
    }

    /**
     * Create a new message with the given head and error.
     *
     * @param head The message head.
     * @param error The untyped object containing error details.
     *
     * @since 5.5
     */
    public static <H extends MessageHeaders, B> Message<H, B> error(final H head, final Object error) {
        return new Message<>(head, null, error);
    }

    /**
     * Constructs a new instance.
     *
     * @param head The message head.
     * @since 5.3
     */
    public Message(final H head) {
        this(head, null, null);
    }

    /**
     * Constructs a new instance.
     *
     * @param head The message head.
     * @param body The message body.
     */
    public Message(final H head, final B body) {
        this(head, body, null);
    }

    private Message(final H head, final B body, final Object error) {
        this.head = Args.notNull(head, "Message head");
        this.body = body;
        this.error = error;
    }

    /**
     * Gets the message head.
     *
     * @return the message head.
     *
     * @since 5.5
     */
    public H head() {
        return head;
    }

    /**
     * Gets the message head.
     *
     * @return the message head.
     */
    public H getHead() {
        return head;
    }

    /**
     * Gets the message body.
     *
     * @return the message body.
     *
     * @since 5.5
     */
    public B body() {
        return body;
    }

    /**
     * Gets the message body.
     *
     * @return the message body.
     */
    public B getBody() {
        return body;
    }

    /**
     * @since 5.5
     */
    public Object error() {
        return error;
    }

    @Override
    public String toString() {
        return "[head=" + head + ", body=" + body + ", error=" + error + ']';
    }

}
