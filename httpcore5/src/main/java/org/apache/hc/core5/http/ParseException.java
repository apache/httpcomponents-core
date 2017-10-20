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

package org.apache.hc.core5.http;

/**
 * Signals a protocol exception due to failure to parse a message element.
 *
 * @since 4.0
 */
public class ParseException extends ProtocolException {

    private static final long serialVersionUID = -7288819855864183578L;

    private final int errorOffset;

    /**
     * Creates a {@link ParseException} without details.
     */
    public ParseException() {
        super();
        this.errorOffset = -1;
    }

    /**
     * Creates a {@link ParseException} with a detail message.
     *
     * @param message the exception detail message, or {@code null}
     */
    public ParseException(final String message) {
        super(message);
        this.errorOffset = -1;
    }

    /**
     * Creates a {@link ParseException} with parsing context details.
     *
     * @since 5.0
     */
    public ParseException(final String description, final CharSequence text, final int off, final int len, final int errorOffset) {
        super(description +
                (errorOffset >= 0 ? "; error at offset " + errorOffset : "") +
                (text != null && len < 1024 ? ": <" + text.subSequence(off, off + len) + ">" : ""));
        this.errorOffset = errorOffset;
    }

    /**
     * Creates a {@link ParseException} with parsing context details.
     *
     * @since 5.0
     */
    public ParseException(final String description, final CharSequence text, final int off, final int len) {
        this(description, text, off, len, -1);
    }

    /**
     * @since 5.0
     */
    public int getErrorOffset() {
        return errorOffset;
    }

}
