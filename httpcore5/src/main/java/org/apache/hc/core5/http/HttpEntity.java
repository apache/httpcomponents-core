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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import org.apache.hc.core5.function.Supplier;

/**
 * An entity that can be sent or received with an HTTP message.
 * <p>
 * There are three distinct types of entities in HttpCore,
 * depending on where their {@link #getContent content} originates:
 * </p>
 * <ul>
 * <li><b>streamed</b>: The content is received from a stream, or
 *     generated on the fly. In particular, this category includes
 *     entities being received from a {@link HttpConnection connection}.
 *     {@link #isStreaming Streamed} entities are generally not
 *      {@link #isRepeatable repeatable}.
 *     </li>
 * <li><b>self-contained</b>: The content is in memory or obtained by
 *     means that are independent from a connection or other entity.
 *     Self-contained entities are generally {@link #isRepeatable repeatable}.
 *     </li>
 * <li><b>wrapping</b>: The content is obtained from another entity.
 *     </li>
 * </ul>
 * <p>
 * This distinction is important for connection management with incoming
 * entities. For entities that are created by an application and only sent
 * using the HTTP components framework, the difference between streamed
 * and self-contained is of little importance. In that case, it is suggested
 * to consider non-repeatable entities as streamed, and those that are
 * repeatable (without a huge effort) as self-contained.
 * </p>
 *
 * @since 4.0
 */
public interface HttpEntity extends EntityDetails, Closeable {

    /**
     * Tells if the entity is capable of producing its data more than once.
     * A repeatable entity's getContent() and writeTo(OutputStream) methods
     * can be called more than once whereas a non-repeatable entity's can not.
     * @return true if the entity is repeatable, false otherwise.
     */
    boolean isRepeatable();

    /**
     * Returns a content stream of the entity.
     * {@link #isRepeatable Repeatable} entities are expected
     * to create a new instance of {@link InputStream} for each invocation
     * of this method and therefore can be consumed multiple times.
     * Entities that are not {@link #isRepeatable repeatable} are expected
     * to return the same {@link InputStream} instance and therefore
     * may not be consumed more than once.
     * <p>
     * If this entity belongs to an incoming HTTP message, calling
     * {@link InputStream#close()} on the returned {@code InputStream} will
     * try to consume the complete entity content to keep the connection
     * alive. In cases where this is undesired, e.g. when only a small part
     * of the content is relevant and consuming the complete entity content
     * would be too inefficient, <i>only</i> the HTTP message from which
     * this entity was obtained should be closed (if supported).
     * </p>
     * <p>
     * IMPORTANT: Please note all entity implementations must ensure that
     * all allocated resources are properly deallocated after
     * the {@link InputStream#close()} method is invoked.
     * </p>
     * @return content stream of the entity.
     *
     * @throws IOException if the stream could not be created
     * @throws UnsupportedOperationException
     *  if entity content cannot be represented as {@link java.io.InputStream}.
     *
     * @see #isRepeatable()
     */
    InputStream getContent() throws IOException, UnsupportedOperationException;

    /**
     * Writes the entity content out to the output stream.
     * <p>
     * IMPORTANT: Please note all entity implementations must ensure that
     * all allocated resources are properly deallocated when this method
     * returns.
     * </p>
     *
     * @param outStream the output stream to write entity content to
     *
     * @throws IOException if an I/O error occurs
     */
    void writeTo(OutputStream outStream) throws IOException;

    /**
     * Tells whether this entity depends on an underlying stream.
     * Streamed entities that read data directly from the socket should
     * return {@code true}. Self-contained entities should return
     * {@code false}. Wrapping entities should delegate this call
     * to the wrapped entity.
     *
     * @return  {@code true} if the entity content is streamed,
     *          {@code false} otherwise
     */
    boolean isStreaming(); // don't expect an exception here

    /**
     * Returns supplier of message trailers - headers sent after message body.
     * May return {@code null} if trailers are not available.
     *
     * @since 5.0
     */
    Supplier<List<? extends Header>> getTrailers();

}
