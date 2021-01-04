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
import java.util.NoSuchElementException;

import org.apache.hc.core5.http.FormattedHeader;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.util.Args;

/**
 * {@link java.util.Iterator} of {@link org.apache.hc.core5.http.HeaderElement}s.
 *
 * @since 5.0
 */
abstract class AbstractHeaderElementIterator<T> implements Iterator<T> {

    private final Iterator<Header> headerIt;

    private T currentElement;
    private CharSequence buffer;
    private ParserCursor cursor;

    /**
     * Creates a new instance of BasicHeaderElementIterator
     */
    AbstractHeaderElementIterator(final Iterator<Header> headerIterator) {
        this.headerIt = Args.notNull(headerIterator, "Header iterator");
    }

    private void bufferHeaderValue() {
        this.cursor = null;
        this.buffer = null;
        while (this.headerIt.hasNext()) {
            final Header h = this.headerIt.next();
            if (h instanceof FormattedHeader) {
                this.buffer = ((FormattedHeader) h).getBuffer();
                this.cursor = new ParserCursor(0, this.buffer.length());
                this.cursor.updatePos(((FormattedHeader) h).getValuePos());
                break;
            }
            final String value = h.getValue();
            if (value != null) {
                this.buffer = value;
                this.cursor = new ParserCursor(0, value.length());
                break;
            }
        }
    }

    abstract T parseHeaderElement(CharSequence buf, ParserCursor cursor);

    private void parseNextElement() {
        // loop while there are headers left to parse
        while (this.headerIt.hasNext() || this.cursor != null) {
            if (this.cursor == null || this.cursor.atEnd()) {
                // get next header value
                bufferHeaderValue();
            }
            // Anything buffered?
            if (this.cursor != null) {
                // loop while there is data in the buffer
                while (!this.cursor.atEnd()) {
                    final T e = parseHeaderElement(this.buffer, this.cursor);
                    if (e != null) {
                        // Found something
                        this.currentElement = e;
                        return;
                    }
                }
                // if at the end of the buffer
                if (this.cursor.atEnd()) {
                    // discard it
                    this.cursor = null;
                    this.buffer = null;
                }
            }
        }
    }

    @Override
    public boolean hasNext() {
        if (this.currentElement == null) {
            parseNextElement();
        }
        return this.currentElement != null;
    }

    @Override
    public T next() throws NoSuchElementException {
        if (this.currentElement == null) {
            parseNextElement();
        }

        if (this.currentElement == null) {
            throw new NoSuchElementException("No more header elements available");
        }

        final T element = this.currentElement;
        this.currentElement = null;
        return element;
    }

    @Override
    public void remove() throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Remove not supported");
    }

}
