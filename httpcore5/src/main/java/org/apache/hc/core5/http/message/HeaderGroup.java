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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.MessageHeaders;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * A class for combining a set of headers.
 * This class allows for multiple headers with the same name and
 * keeps track of the order in which headers were added.
 *
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.SAFE)
public class HeaderGroup implements MessageHeaders, Serializable {


    // HTTPCORE-361 : we don't use the for-each syntax, when iterating headers
    // as that creates an Iterator that needs to be garbage-collected

    private static final long serialVersionUID = 2608834160639271617L;

    private final Header[] EMPTY = new Header[] {};

    /** The list of headers for this group, in the order in which they were added */
    private final List<Header> headers;
    private final ReadWriteLock headerLock;

    /**
     * Constructor for HeaderGroup.
     */
    public HeaderGroup() {
        this.headers = new ArrayList<>(16);
        this.headerLock = new ReentrantReadWriteLock();
    }

    /**
     * Removes any contained headers.
     */
    public void clear() {
        headerLock.writeLock().lock();
        try {
            headers.clear();
        } finally {
            headerLock.writeLock().unlock();
        }
    }

    /**
     * Adds the given header to the group.  The order in which this header was
     * added is preserved.
     *
     * @param header the header to add
     */
    public void addHeader(final Header header) {
        if (header == null) {
            return;
        }
        headerLock.writeLock().lock();
        try {
            headers.add(header);
        } finally {
            headerLock.writeLock().unlock();
        }
    }

    /**
     * Removes the given header.
     *
     * @param header the header to remove
     */
    public void removeHeader(final Header header) {
        if (header == null) {
            return;
        }
        headerLock.writeLock().lock();
        try {
            headers.remove(header);
        } finally {
            headerLock.writeLock().unlock();
        }
    }

    /**
     * Replaces the first occurrence of the header with the same name. If no header with
     * the same name is found the given header is added to the end of the list.
     *
     * @param header the new header that should replace the first header with the same
     * name if present in the list.
     *
     * @since 5.0
     */
    public void setHeader(final Header header) {
        if (header == null) {
            return;
        }
        headerLock.writeLock().lock();
        try {
            for (int i = 0; i < this.headers.size(); i++) {
                final Header current = this.headers.get(i);
                if (current.getName().equalsIgnoreCase(header.getName())) {
                    this.headers.set(i, header);
                    return;
                }
            }
            this.headers.add(header);
        } finally {
            headerLock.writeLock().unlock();
        }
    }

    /**
     * Sets all of the headers contained within this group overriding any
     * existing headers. The headers are added in the order in which they appear
     * in the array.
     *
     * @param headers the headers to set
     */
    public void setHeaders(final Header[] headers) {
        headerLock.writeLock().lock();
        try {
            clear();
            if (headers == null) {
                return;
            }
            Collections.addAll(this.headers, headers);
        } finally {
            headerLock.writeLock().unlock();
        }
    }

    /**
     * Gets a header representing all of the header values with the given name.
     * If more that one header with the given name exists the values will be
     * combined with a "," as per RFC 2616.
     *
     * <p>Header name comparison is case insensitive.
     *
     * @param name the name of the header(s) to get
     * @return a header with a condensed value or {@code null} if no
     * headers by the given name are present
     */
    public Header getCondensedHeader(final String name) {
        final Header[] hdrs = getHeaders(name);

        if (hdrs.length == 0) {
            return null;
        } else if (hdrs.length == 1) {
            return hdrs[0];
        } else {
            final CharArrayBuffer valueBuffer = new CharArrayBuffer(128);
            valueBuffer.append(hdrs[0].getValue());
            for (int i = 1; i < hdrs.length; i++) {
                valueBuffer.append(", ");
                valueBuffer.append(hdrs[i].getValue());
            }

            return new BasicHeader(name.toLowerCase(Locale.ROOT), valueBuffer.toString());
        }
    }

    /**
     * Gets all of the headers with the given name.  The returned array
     * maintains the relative order in which the headers were added.
     *
     * <p>Header name comparison is case insensitive.
     *
     * @param name the name of the header(s) to get
     *
     * @return an array of length &ge; 0
     */
    @Override
    public Header[] getHeaders(final String name) {
        List<Header> headersFound = null;
        headerLock.readLock().lock();
        try {
            for (int i = 0; i < this.headers.size(); i++) {
                final Header header = this.headers.get(i);
                if (header.getName().equalsIgnoreCase(name)) {
                    if (headersFound == null) {
                        headersFound = new ArrayList<>();
                    }
                    headersFound.add(header);
                }
            }
        } finally {
            headerLock.readLock().unlock();
        }
        return headersFound != null ? headersFound.toArray(new Header[headersFound.size()]) : EMPTY;
    }

    /**
     * Gets the first header with the given name.
     *
     * <p>Header name comparison is case insensitive.
     *
     * @param name the name of the header to get
     * @return the first header or {@code null}
     */
    @Override
    public Header getFirstHeader(final String name) {
        headerLock.readLock().lock();
        try {
            for (int i = 0; i < this.headers.size(); i++) {
                final Header header = this.headers.get(i);
                if (header.getName().equalsIgnoreCase(name)) {
                    return header;
                }
            }
        } finally {
            headerLock.readLock().unlock();
        }
        return null;
    }

    /**
     * Gets single first header with the given name.
     *
     * <p>Header name comparison is case insensitive.
     *
     * @param name the name of the header to get
     * @return the first header or {@code null}
     * @throws ProtocolException in case multiple headers with the given name are found.
     */
    @Override
    public Header getSingleHeader(final String name) throws ProtocolException {
        int count = 0;
        Header singleHeader = null;
        headerLock.readLock().lock();
        try {
            for (int i = 0; i < this.headers.size(); i++) {
                final Header header = this.headers.get(i);
                if (header.getName().equalsIgnoreCase(name)) {
                    singleHeader = header;
                    count++;
                }
            }
        } finally {
            headerLock.readLock().unlock();
        }
        if (count > 1) {
            throw new ProtocolException("Multiple headers '" + name + "' found");
        }
        return singleHeader;
    }

    /**
     * Gets the last header with the given name.
     *
     * <p>Header name comparison is case insensitive.
     *
     * @param name the name of the header to get
     * @return the last header or {@code null}
     */
    @Override
    public Header getLastHeader(final String name) {
        headerLock.readLock().lock();
        try {
            // start at the end of the list and work backwards
            for (int i = headers.size() - 1; i >= 0; i--) {
                final Header header = headers.get(i);
                if (header.getName().equalsIgnoreCase(name)) {
                    return header;
                }
            }
        } finally {
            headerLock.readLock().unlock();
        }

        return null;
    }

    /**
     * Gets all of the headers contained within this group.
     *
     * @return an array of length &ge; 0
     */
    @Override
    public Header[] getAllHeaders() {
        headerLock.readLock().lock();
        try {
            return headers.toArray(new Header[headers.size()]);
        } finally {
            headerLock.readLock().unlock();
        }
    }

    /**
     * Tests if headers with the given name are contained within this group.
     *
     * <p>Header name comparison is case insensitive.
     *
     * @param name the header name to test for
     * @return {@code true} if at least one header with the name is
     * contained, {@code false} otherwise
     */
    @Override
    public boolean containsHeader(final String name) {
        headerLock.readLock().lock();
        try {
            // HTTPCORE-361 : we don't use the for-each syntax, i.e.
            //     for (Header header : headers)
            // as that creates an Iterator that needs to be garbage-collected
            for (int i = 0; i < this.headers.size(); i++) {
                final Header header = this.headers.get(i);
                if (header.getName().equalsIgnoreCase(name)) {
                    return true;
                }
            }
        } finally {
            headerLock.readLock().unlock();
        }

        return false;
    }

    /**
     * Checks if a certain header is present in this message and how many times.
     * <p>Header name comparison is case insensitive.
     *
     * @param name the header name to check for.
     * @return number of occurrences of the header in the message.
     */
    @Override
    public int containsHeaders(final String name) {
        int count = 0;
        headerLock.readLock().lock();
        try {
            // HTTPCORE-361 : we don't use the for-each syntax, i.e.
            //     for (Header header : headers)
            // as that creates an Iterator that needs to be garbage-collected
            for (int i = 0; i < this.headers.size(); i++) {
                final Header header = this.headers.get(i);
                if (header.getName().equalsIgnoreCase(name)) {
                    count++;
                }
            }
        } finally {
            headerLock.readLock().unlock();
        }
        return count;
    }

    /**
     * Returns an iterator over this group of headers.
     *
     * @return iterator over this group of headers.
     *
     * @since 5.0
     */
    @Override
    public Iterator<Header> headerIterator() {
        return new BasicListHeaderIterator(this.headers, headerLock, null);
    }

    /**
     * Returns an iterator over the headers with a given name in this group.
     *
     * @param name      the name of the headers over which to iterate, or
     *                  {@code null} for all headers
     *
     * @return iterator over some headers in this group.
     *
     * @since 5.0
     */
    @Override
    public Iterator<Header> headerIterator(final String name) {
        return new BasicListHeaderIterator(this.headers, headerLock, name);
    }

    /**
     * Removes all headers with a given name in this group.
     *
     * @param name      the name of the headers to be removed.
     *
     * @since 5.0
     */
    public void removeHeaders(final String name) {
        if (name == null) {
            return;
        }
        headerLock.writeLock().lock();
        try {
            for (final Iterator<Header> i = headerIterator(); i.hasNext(); ) {
                final Header header = i.next();
                if (header.getName().equalsIgnoreCase(name)) {
                    i.remove();
                }
            }
        } finally {
            headerLock.writeLock().unlock();
        }
    }

    @Override
    public String toString() {
        headerLock.readLock().lock();
        try {
            return this.headers.toString();
        } finally {
            headerLock.readLock().unlock();
        }
    }

}
