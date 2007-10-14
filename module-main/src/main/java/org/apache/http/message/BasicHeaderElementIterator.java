/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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

import java.util.NoSuchElementException;
import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HeaderIterator;

/**
 * Basic implementation of a {@link HeaderElementIterator}.
 * 
 * @version $Revision$
 * 
 * @author Andrea Selva <selva.andre at gmail.com>
 */
public class BasicHeaderElementIterator implements HeaderElementIterator {
    
    private final HeaderIterator headerIt;

    private int currentElementIdx = -1;
    
    private HeaderElement[] currentElements = null;
    
    private HeaderElement currentElement = null;
    
    /**
     * Creates a new instance of BasicHeaderElementIterator
     */
    public BasicHeaderElementIterator(final HeaderIterator headerIterator) {
        if (headerIterator == null) {
            throw new IllegalArgumentException("Header iterator may not be null");
        }
        this.headerIt = headerIterator;
        
        if (this.headerIt.hasNext()) {
            this.currentElementIdx = 0;
            this.currentElements = this.headerIt.nextHeader().getElements();
            this.currentElement = findNext();
        } 
    }


    protected HeaderElement findNext() {
        HeaderElement tmpHeader;
        
        if (this.currentElementIdx == this.currentElements.length) {
            if (!this.headerIt.hasNext()) {
                return null;
            }
            this.currentElements = this.headerIt.nextHeader().getElements();
            this.currentElementIdx = 0;
        }

        tmpHeader = this.currentElements[this.currentElementIdx++];
        return tmpHeader;
         
    }
    
    public boolean hasNext() {
        return (this.currentElement != null);
    }

    public HeaderElement nextElement() throws NoSuchElementException {

        final HeaderElement current = this.currentElement;
        if (current == null) {
            throw new NoSuchElementException("Iteration already finished.");
        }

        this.currentElement = findNext();

        return current;
    }

    public final Object next() throws NoSuchElementException {
        return nextElement();
    }

    public void remove() throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Remove not supported");
    }

}