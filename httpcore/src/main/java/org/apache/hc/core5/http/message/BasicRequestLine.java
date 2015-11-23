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

package org.apache.hc.core5.http.message;

import java.io.Serializable;

import org.apache.hc.core5.annotation.Immutable;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.RequestLine;
import org.apache.hc.core5.util.Args;

/**
 * Basic implementation of {@link RequestLine}.
 *
 * @since 4.0
 */
@Immutable
public class BasicRequestLine implements RequestLine, Serializable {

    private static final long serialVersionUID = 2810581718468737193L;

    private final ProtocolVersion protoversion;
    private final String method;
    private final String uri;

    public BasicRequestLine(final String method,
                            final String uri,
                            final ProtocolVersion version) {
        super();
        this.method = Args.notNull(method, "Method");
        this.uri = Args.notNull(uri, "URI");
        this.protoversion = Args.notNull(version, "Version");
    }

    @Override
    public String getMethod() {
        return this.method;
    }

    @Override
    public ProtocolVersion getProtocolVersion() {
        return this.protoversion;
    }

    @Override
    public String getUri() {
        return this.uri;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append(this.method).append(" ").append(this.uri).append(" ").append(this.protoversion);
        return buf.toString();
    }

}
