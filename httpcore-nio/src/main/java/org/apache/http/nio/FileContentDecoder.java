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

package org.apache.http.nio;

import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * A content decoder capable of transferring data directly to a {@link FileChannel}
 *
 * @since 4.0
 */
public interface FileContentDecoder extends ContentDecoder {

    /**
     * Transfers a portion of entity content from the underlying network channel
     * into the given file channel.<br>
     *
     * <b>Warning</b>: Many implementations cannot write beyond the length of the file.
     *             If the position exceeds the channel's size, some implementations
     *             may throw an IOException.
     *
     * @param  dst the target FileChannel to transfer data into.
     * @param  position
     *         The position within the file at which the transfer is to begin;
     *         must be non-negative.
     *         <b>Must be less than or equal to the size of the file</b>
     * @param  count
     *         The maximum number of bytes to be transferred; must be
     *         non-negative
     * @throws IOException, if some I/O error occurs.
     * @return  The number of bytes, possibly zero,
     *          that were actually transferred
     */
    long transfer(FileChannel dst, long position, long count) throws IOException;

}
