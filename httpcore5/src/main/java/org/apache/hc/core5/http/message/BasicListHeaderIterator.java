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
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;

/**
 * {@link java.util.Iterator} of {@link org.apache.hc.core5.http.Header}s. For use by {@link HeaderGroup}.
 *
 * @since 4.0
 */
class BasicListHeaderIterator implements Iterator<Header> {

    /**
     * A list of headers to iterate over.
     * Not all elements of this array are necessarily part of the iteration.
     */
    private final List<Header> allHeaders;

    /**
     * Lock used while reading {@link #allHeaders allHeaders}.
     */
    private final ReadWriteLock headerLock;

    /**
     * The position of the next header in {@link #allHeaders allHeaders}.
     * Negative if the iteration is over.
     */
    private int currentIndex;

    /**
     * The position of the last returned header.
     * Negative if none has been returned so far.
     */
    private int lastIndex;

    /**
     * The header name to filter by.
     * {@code null} to iterate over all headers in the array.
     */
    private final String headerName;

    /**
     * Creates a new header iterator.
     *
     * @param headers   a list of headers over which to iterate
     * @param headerLock lock to avoid unexpected concurrent modification
     * @param name      the name of the headers over which to iterate, or
     *                  {@code null} for any
     */
    public BasicListHeaderIterator(final List<Header> headers, final ReadWriteLock headerLock, final String name) {
        super();
        this.allHeaders = Args.notNull(headers, "Header list");
        this.headerLock = Args.notNull(headerLock, "Header lock");
        this.headerName = name;
        headerLock.readLock().lock();
        try {
            this.currentIndex = findNext(-1);
        } finally {
            headerLock.readLock().unlock();
        }
        this.lastIndex = -1;
    }

    /**
     * Creates a new header iterator.
     *
     * @param headers   a list of headers over which to iterate
     * @param name      the name of the headers over which to iterate, or
     *                  {@code null} for any
     * @deprecated Please use {@link BasicListHeaderIterator#BasicListHeaderIterator(List, ReadWriteLock, String)}.
     */
    @Deprecated
    public BasicListHeaderIterator(final List<Header> headers, final String name) {
        this(headers, DummyReadWriteLock.INSTANCE, name);
    }

    /**
     * Determines the index of the next header.
     *
     * @param pos       one less than the index to consider first,
     *                  -1 to search for the first header
     *
     * @return  the index of the next header that matches the filter name,
     *          or negative if there are no more headers
     */
    protected int findNext(final int pos) {
        int from = pos;
        if (from < -1) {
            return -1;
        }

        final int to = this.allHeaders.size()-1;
        boolean found = false;
        while (!found && (from < to)) {
            from++;
            found = filterHeader(from);
        }
        return found ? from : -1;
    }

    /**
     * Checks whether a header is part of the iteration.
     *
     * @param index     the index of the header to check
     *
     * @return  {@code true} if the header should be part of the
     *          iteration, {@code false} to skip
     */
    private boolean filterHeader(final int index) {
        if (this.headerName == null) {
            return true;
        }

        // non-header elements, including null, will trigger exceptions
        final String name = (this.allHeaders.get(index)).getName();

        return this.headerName.equalsIgnoreCase(name);
    }

    @Override
    public boolean hasNext() {
        return this.currentIndex >= 0;
    }

    /**
     * Obtains the next header from this iteration.
     *
     * @return  the next header in this iteration
     *
     * @throws NoSuchElementException   if there are no more headers
     */
    @Override
    public Header next() throws NoSuchElementException {
        final int current = this.currentIndex;
        if (current < 0) {
            throw new NoSuchElementException("Iteration already finished.");
        }

        headerLock.readLock().lock();
        try {
            this.lastIndex = current;
            this.currentIndex = findNext(current);

            return this.allHeaders.get(current);
        } finally {
            headerLock.readLock().unlock();
        }
    }

    /**
     * Removes the header that was returned last.
     */
    @Override
    public void remove() throws UnsupportedOperationException {
        Asserts.check(this.lastIndex >= 0, "No header to remove");
        headerLock.writeLock().lock();
        try {
            Asserts.check(this.lastIndex < allHeaders.size(), "Headers have been concurrently removed");
            this.allHeaders.remove(this.lastIndex);
            this.lastIndex = -1;
            this.currentIndex--; // adjust for the removed element
        } finally {
            headerLock.writeLock().unlock();
        }
    }

    private static final class DummyReadWriteLock implements ReadWriteLock {
        static final ReadWriteLock INSTANCE = new DummyReadWriteLock();

        @Override
        public Lock readLock() {
            return DummyLock.INSTANCE;
        }

        @Override
        public Lock writeLock() {
            return DummyLock.INSTANCE;
        }
    }

    private static final class DummyLock implements Lock {
        static final Lock INSTANCE = new DummyLock();

        @Override
        public void lock() {
            // no-op
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            // no-op
        }

        @Override
        public boolean tryLock() {
            return true;
        }

        @Override
        public boolean tryLock(final long time, final TimeUnit unit) throws InterruptedException {
            return true;
        }

        @Override
        public void unlock() {
            // no-op
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException("newCondition has not been implemented");
        }
    }
}
