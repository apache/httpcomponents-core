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

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * Abstract resource holder.
 * <p>
 * Implementations are expected to ensure that {@link #releaseResources()} methods is idempotent and is
 * safe to invoke multiple times.
 * </p>
 * <p>
 * Implementations are expected to be thread-safe.
 * </p>
 *
 * @since 5.0
 * @since 5.2 extends AutoCloseable. This interface could go away in a future major release.
 */
@Contract(threading = ThreadingBehavior.SAFE)
public interface ResourceHolder extends AutoCloseable {

    /**
     * Closes this resource. The default implementation calls {@link #releaseResources()}.
     *
     * @since 5.2
     */
    @Override
    default void close() {
        releaseResources();
    }

    /**
     * Releases resources. The default implementation does nothing.
     *
     * @deprecated Use {@link #close()}.
     */
    @Deprecated
    default void releaseResources() {
        // do nothing
    }

}
