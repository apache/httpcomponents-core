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

package org.apache.hc.core5.http.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.apache.hc.core5.http.Header;

/**
 * Abstract HTTP content encoder. HTTP content encoders can be used
 * to apply the required coding transformation and write entity
 * content to the underlying channel in small chunks.
 *
 * @since 4.0
 */
public interface ContentEncoder {

    /**
     * Writes a portion of entity content to the underlying channel.
     *
     * @param src The buffer from which content is to be retrieved
     * @return The number of bytes read, possibly zero
     * @throws IOException if I/O error occurs while writing content
     */
    int write(ByteBuffer src) throws IOException;

    /**
     * Terminates the content stream.
     *
     * @throws IOException if I/O error occurs while writing content
     */
    void complete(List<? extends Header> trailers) throws IOException;

    /**
     * Returns {@code true} if the entity has been transferred in its
     * entirety.
     *
     * @return {@code true} if all the content has been produced,
     * {@code false} otherwise.
     */
    boolean isCompleted();

}
