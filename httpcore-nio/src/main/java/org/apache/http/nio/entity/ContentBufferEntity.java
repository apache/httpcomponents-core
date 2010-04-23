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

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.nio.util.ContentInputBuffer;

/**
 * HTTP entity wrapper whose content is provided by a
 * {@link ContentInputBuffer}.
 *
 * @since 4.0
 */
public class ContentBufferEntity extends BasicHttpEntity {

    private HttpEntity wrappedEntity;

    /**
     * Creates new instance of ContentBufferEntity.
     *
     * @param entity the original entity.
     * @param buffer the content buffer.
     */
    public ContentBufferEntity(final HttpEntity entity, final ContentInputBuffer buffer) {
        super();
        if (entity == null) {
            throw new IllegalArgumentException("HTTP entity may not be null");
        }
        this.wrappedEntity = entity;
        setContent(new ContentInputStream(buffer));
    }

    @Override
    public boolean isChunked() {
        return this.wrappedEntity.isChunked();
    }

    @Override
    public long getContentLength() {
        return this.wrappedEntity.getContentLength();
    }

    @Override
    public Header getContentType() {
        return this.wrappedEntity.getContentType();
    }

    @Override
    public Header getContentEncoding() {
        return this.wrappedEntity.getContentEncoding();
    }

}
