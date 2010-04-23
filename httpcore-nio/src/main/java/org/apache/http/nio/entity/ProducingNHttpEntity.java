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

package org.apache.http.nio.entity;

import java.io.IOException;

import org.apache.http.HttpEntity;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;

/**
 * An {@link HttpEntity} that can stream content out into a
 * {@link ContentEncoder}.
 *
 * @since 4.0
 */
public interface ProducingNHttpEntity extends HttpEntity {

    /**
     * Notification that content should be written to the encoder.
     * {@link IOControl} instance passed as a parameter to the method can be
     * used to suspend output events if the entity is temporarily unable to
     * produce more content.
     * <p>
     * When all content is finished, this <b>MUST</b> call {@link ContentEncoder#complete()}.
     * Failure to do so could result in the entity never being written.
     *
     * @param encoder content encoder.
     * @param ioctrl I/O control of the underlying connection.
     */
    void produceContent(ContentEncoder encoder, IOControl ioctrl) throws IOException;

    /**
     * Notification that any resources allocated for writing can be released.
     */
    void finish() throws IOException;

}
