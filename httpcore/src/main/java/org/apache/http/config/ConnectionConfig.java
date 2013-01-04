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

package org.apache.http.config;

import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;

import org.apache.http.Consts;
import org.apache.http.annotation.Immutable;
import org.apache.http.util.Args;

/**
 * HTTP connection configuration.
 *
 * @since 4.3
 */
@Immutable
public class ConnectionConfig implements Cloneable {

    public static final ConnectionConfig DEFAULT = new Builder().build();

    private final Charset charset;
    private final CodingErrorAction malformedInputAction;
    private final CodingErrorAction unmappableInputAction;
    private final MessageConstraints messageConstraints;

    ConnectionConfig(
            final Charset charset,
            final CodingErrorAction malformedInputAction,
            final CodingErrorAction unmappableInputAction,
            final MessageConstraints messageConstraints) {
        super();
        this.charset = charset;
        this.malformedInputAction = malformedInputAction;
        this.unmappableInputAction = unmappableInputAction;
        this.messageConstraints = messageConstraints;
    }

    public Charset getCharset() {
        return charset;
    }

    public CodingErrorAction getMalformedInputAction() {
        return malformedInputAction;
    }

    public CodingErrorAction getUnmappableInputAction() {
        return unmappableInputAction;
    }

    public MessageConstraints getMessageConstraints() {
        return messageConstraints;
    }

    @Override
    protected ConnectionConfig clone() throws CloneNotSupportedException {
        return (ConnectionConfig) super.clone();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("[charset=").append(this.charset)
                .append(", malformedInputAction=").append(this.malformedInputAction)
                .append(", unmappableInputAction=").append(this.unmappableInputAction)
                .append(", messageConstraints=").append(this.messageConstraints)
                .append("]");
        return builder.toString();
    }

    public static ConnectionConfig.Builder custom() {
        return new Builder();
    }

    public static ConnectionConfig.Builder copy(final ConnectionConfig config) {
        Args.notNull(config, "Connection config");
        return new Builder()
            .setCharset(config.getCharset())
            .setMalformedInputAction(config.getMalformedInputAction())
            .setUnmappableInputAction(config.getUnmappableInputAction())
            .setMessageConstraints(config.getMessageConstraints());
    }

    public static class Builder {

        private Charset charset;
        private CodingErrorAction malformedInputAction;
        private CodingErrorAction unmappableInputAction;
        private MessageConstraints messageConstraints;

        Builder() {
        }

        public Charset getCharset() {
            return charset;
        }

        public Builder setCharset(final Charset charset) {
            this.charset = charset;
            return this;
        }

        public Builder setMalformedInputAction(final CodingErrorAction malformedInputAction) {
            this.malformedInputAction = malformedInputAction;
            if (malformedInputAction != null && this.charset == null) {
                this.charset = Consts.ASCII;
            }
            return this;
        }

        public Builder setUnmappableInputAction(final CodingErrorAction unmappableInputAction) {
            this.unmappableInputAction = unmappableInputAction;
            if (unmappableInputAction != null && this.charset == null) {
                this.charset = Consts.ASCII;
            }
            return this;
        }

        public Builder setMessageConstraints(final MessageConstraints messageConstraints) {
            this.messageConstraints = messageConstraints;
            return this;
        }

        public ConnectionConfig build() {
            Charset cs = charset;
            if (cs == null && (malformedInputAction != null || unmappableInputAction != null)) {
                cs = Consts.ASCII;
            }
            return new ConnectionConfig(
                    charset,
                    malformedInputAction,
                    unmappableInputAction,
                    messageConstraints);
        }

    }

}
