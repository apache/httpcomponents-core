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
package org.apache.hc.core5.http.signature;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.MessageHeaders;
import org.apache.hc.core5.util.Args;

/**
 * Message context needed to resolve RFC 9421 components. Its thread-safety depends on
 * the supplied HTTP message and trailer instances.
 * This object intentionally contains no key lookup, cryptography, or signature policy.
 *
 * @since 5.5
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public final class MessageSignatureContext {

    private final HttpMessage target;
    private final HttpRequest relatedRequest;
    private final MessageHeaders trailers;
    private final MessageHeaders relatedRequestTrailers;
    private final Map<String, StructuredFieldValueType> structuredFieldTypes;

    private MessageSignatureContext(final Builder builder) {
        this.target = Args.notNull(builder.target, "Target message");
        this.relatedRequest = builder.relatedRequest;
        this.trailers = builder.trailers;
        this.relatedRequestTrailers = builder.relatedRequestTrailers;
        this.structuredFieldTypes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.structuredFieldTypes));
    }

    public static Builder builder(final HttpMessage target) {
        return new Builder(target);
    }

    public HttpMessage getTarget() {
        return target;
    }

    /**
     * Returns the related request of a response target, or {@code null} if none.
     */
    public HttpRequest getRelatedRequest() {
        return relatedRequest;
    }

    /**
     * Returns the target message trailers, or {@code null} if none.
     */
    public MessageHeaders getTrailers() {
        return trailers;
    }

    /**
     * Returns the related request trailers, or {@code null} if none.
     */
    public MessageHeaders getRelatedRequestTrailers() {
        return relatedRequestTrailers;
    }

    /**
     * Returns the registered type of the given field, matched case-insensitively, or {@code null} if none.
     */
    public StructuredFieldValueType getStructuredFieldType(final String fieldName) {
        return structuredFieldTypes.get(fieldName.toLowerCase(Locale.ROOT));
    }

    public static final class Builder {
        private final HttpMessage target;
        private HttpRequest relatedRequest;
        private MessageHeaders trailers;
        private MessageHeaders relatedRequestTrailers;
        private final Map<String, StructuredFieldValueType> structuredFieldTypes = new LinkedHashMap<>();

        private Builder(final HttpMessage target) {
            this.target = Args.notNull(target, "Target message");
        }

        public Builder relatedRequest(final HttpRequest relatedRequest) {
            this.relatedRequest = relatedRequest;
            return this;
        }

        public Builder trailers(final MessageHeaders trailers) {
            this.trailers = trailers;
            return this;
        }

        public Builder relatedRequestTrailers(final MessageHeaders trailers) {
            this.relatedRequestTrailers = trailers;
            return this;
        }

        /**
         * Registers the Structured Field type of a field; field names are matched case-insensitively.
         */
        public Builder structuredField(
                final String fieldName, final StructuredFieldValueType valueType) {
            structuredFieldTypes.put(
                    Args.notBlank(fieldName, "Field name").toLowerCase(Locale.ROOT),
                    Args.notNull(valueType, "Structured Field type"));
            return this;
        }

        public MessageSignatureContext build() {
            return new MessageSignatureContext(this);
        }
    }

}
