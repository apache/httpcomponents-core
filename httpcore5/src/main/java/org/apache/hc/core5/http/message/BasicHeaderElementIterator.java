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

import java.util.Iterator;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.util.Args;

/**
 * {@link java.util.Iterator} of {@link org.apache.hc.core5.http.HeaderElement}s.
 *
 * @since 4.0
 */
public class BasicHeaderElementIterator extends AbstractHeaderElementIterator<HeaderElement> {

    private final HeaderValueParser parser;

    /**
     * Creates a new instance of BasicHeaderElementIterator
     */
    public BasicHeaderElementIterator(
            final Iterator<Header> headerIterator,
            final HeaderValueParser parser) {
        super(headerIterator);
        this.parser = Args.notNull(parser, "Parser");
    }

    public BasicHeaderElementIterator(final Iterator<Header> headerIterator) {
        this(headerIterator, BasicHeaderValueParser.INSTANCE);
    }

    @Override
    HeaderElement parseHeaderElement(final CharSequence buf, final ParserCursor cursor) {
        final HeaderElement e = this.parser.parseHeaderElement(buf, cursor);
        if (!(e.getName().isEmpty() && e.getValue() == null)) {
            return e;
        }
        return null;
    }

}
