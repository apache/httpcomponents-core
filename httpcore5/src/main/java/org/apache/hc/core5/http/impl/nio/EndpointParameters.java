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
package org.apache.hc.core5.http.impl.nio;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.URIScheme;

/**
 * Endpoint initialization parameters
 *
 * @since 5.1
 */
@Internal
public final class EndpointParameters {

    public final String scheme;
    public final Object attachment;

    public EndpointParameters(final String scheme, final Object attachment) {
        this.scheme = scheme != null ? scheme : URIScheme.HTTP.id;
        this.attachment = attachment;
    }

    @Override
    public String toString() {
        return "EndpointParameters{" +
                "scheme=" + scheme +
                ", attachment=" + attachment +
                '}';
    }

}
