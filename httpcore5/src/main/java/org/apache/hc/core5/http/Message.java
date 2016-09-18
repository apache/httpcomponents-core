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

    public Message(final H head, final B body) {
        this.head = Args.notNull(head, "Message head");
        this.body = body;
    }

    public H getHead() {
        return head;
    }

    public B getBody() {
        return body;
    }

    @Override
    public String toString() {
        return "[" +
                "head=" + head +
                ", body=" + body +
                ']';
    }

}
