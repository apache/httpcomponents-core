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
package org.apache.hc.core5.jackson2.http;

import java.io.IOException;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.nio.AsyncDataProducer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;

class JsonMessageProducer implements AsyncDataProducer {

    private final AsyncEntityProducer entityProducer;

    JsonMessageProducer(final AsyncEntityProducer entityProducer) {
        this.entityProducer = entityProducer;
    }

    EntityDetails getEntityDetails() {
        return entityProducer;
    }

    boolean isRepeatable() {
        return entityProducer == null || entityProducer.isRepeatable();
    }

    @Override
    public final int available() {
        return entityProducer != null ? entityProducer.available() : 0;
    }

    @Override
    public final void produce(final DataStreamChannel channel) throws IOException {
        if (entityProducer != null) {
            entityProducer.produce(channel);
        }
    }

    @Override
    public final void releaseResources() {
        if (entityProducer != null) {
            entityProducer.releaseResources();
        }
    }

}
