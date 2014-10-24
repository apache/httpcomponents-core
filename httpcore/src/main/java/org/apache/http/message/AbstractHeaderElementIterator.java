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

package org.apache.http.message;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.http.FormattedHeader;
import org.apache.http.Header;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.util.Args;
import org.apache.http.util.CharArrayBuffer;

/**
 * {@link java.util.Iterator} of {@link org.apache.http.HeaderElement}s.
 *
 * @since 5.0
 */
@NotThreadSafe
abstract class AbstractHeaderElementIterator<T> implements Iterator<T> {

    private final Iterator<Header> headerIt;

    private T currentElement = null;
    private CharArrayBuffer buffer = null;
    private ParserCursor cursor = null;

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
            } else {
                final String value = h.getValue();
                if (value != null) {
                    this.buffer = new CharArrayBuffer(value.length());
                    this.buffer.append(value);
                    this.cursor = new ParserCursor(0, this.buffer.length());
                    break;
                }
            }
        }
    }

    abstract T parseHeaderElement(CharArrayBuffer buf, ParserCursor cursor);

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
