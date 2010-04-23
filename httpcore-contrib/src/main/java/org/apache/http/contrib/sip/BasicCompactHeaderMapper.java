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

package org.apache.http.contrib.sip;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;



/**
 * Basic implementation of a {@link CompactHeaderMapper}.
 * Header names are assumed to be case insensitive.
 *
 *
 */
public class BasicCompactHeaderMapper implements CompactHeaderMapper {

    /**
     * The map from compact names to full names.
     * Keys are converted to lower case, values may be mixed case.
     */
    protected Map<String,String> mapCompactToFull;

    /**
     * The map from full names to compact names.
     * Keys are converted to lower case, values may be mixed case.
     */
    protected Map<String,String> mapFullToCompact;


    /**
     * The default mapper.
     * This mapper is initialized with the compact header names defined at
     * <a href="http://www.iana.org/assignments/sip-parameters">
     * http://www.iana.org/assignments/sip-parameters
     * </a>
     * on 2008-01-02.
     */
    // see below for static initialization
    public final static CompactHeaderMapper DEFAULT;


    /**
     * Creates a new header mapper with an empty mapping.
     */
    public BasicCompactHeaderMapper() {
        createMaps();
    }


    /**
     * Initializes the two maps.
     * The default implementation here creates an empty hash map for
     * each attribute that is <code>null</code>.
     * Derived implementations may choose to instantiate other
     * map implementations, or to populate the maps by default.
     * In the latter case, it is the responsibility of the dervied class
     * to guarantee consistent mappings in both directions.
     */
    protected void createMaps() {
        if (mapCompactToFull == null) {
            mapCompactToFull = new HashMap<String,String>();
        }
        if (mapFullToCompact == null) {
            mapFullToCompact = new HashMap<String,String>();
        }
    }


    /**
     * Adds a header name mapping.
     *
     * @param compact   the compact name of the header
     * @param full      the full name of the header
     */
    public void addMapping(final String compact, final String full) {
        if (compact == null) {
            throw new IllegalArgumentException
                ("The compact name must not be null.");
        }
        if (full == null) {
            throw new IllegalArgumentException
                ("The full name must not be null.");
        }
        if (compact.length() >= full.length()) {
            throw new IllegalArgumentException
                ("The compact name must be shorter than the full name. " +
                 compact + " -> " + full);
        }

        mapCompactToFull.put(compact.toLowerCase(), full);
        mapFullToCompact.put(full.toLowerCase(), compact);
    }


    /**
     * Switches this mapper to read-only mode.
     * Subsequent invocations of {@link #addMapping addMapping}
     * will trigger an exception.
     * <br/>
     * The implementation here should be called only once.
     * It replaces the internal maps with unmodifiable ones.
     */
    protected void makeReadOnly() {
        mapCompactToFull = Collections.unmodifiableMap(mapCompactToFull);
        mapFullToCompact = Collections.unmodifiableMap(mapFullToCompact);
    }


    // non-javadoc, see interface CompactHeaderMapper
    public String getCompactName(final String fullname) {
        if (fullname == null) {
            throw new IllegalArgumentException
                ("The full name must not be null.");
        }
        return mapFullToCompact.get(fullname.toLowerCase());
    }


    // non-javadoc, see interface CompactHeaderMapper
    public String getFullName(final String compactname) {
        if (compactname == null) {
            throw new IllegalArgumentException
                ("The compact name must not be null.");
        }
        return mapCompactToFull.get(compactname.toLowerCase());
    }


    // non-javadoc, see interface CompactHeaderMapper
    public String getAlternateName(final String name) {
        if (name == null) {
            throw new IllegalArgumentException
                ("The name must not be null.");
        }

        final String namelc = name.toLowerCase();
        String       result = null;

        // to minimize lookups, use a heuristic to determine the direction
        boolean iscompact = name.length() < 2;
        if (iscompact) {
            result = mapCompactToFull.get(namelc);
        }
        if (result == null) {
            result = mapFullToCompact.get(namelc);
        }
        if ((result == null) && !iscompact) {
            result = mapCompactToFull.get(namelc);
        }

        return result;
    }


    // initializes the default mapper and switches it to read-only mode
    static {
        BasicCompactHeaderMapper chm = new BasicCompactHeaderMapper();
        chm.addMapping("a", "Accept-Contact");
        chm.addMapping("u", "Allow-Events");
        chm.addMapping("i", "Call-ID");
        chm.addMapping("m", "Contact");
        chm.addMapping("e", "Content-Encoding");
        chm.addMapping("l", "Content-Length");
        chm.addMapping("c", "Content-Type");
        chm.addMapping("o", "Event");
        chm.addMapping("f", "From");
        chm.addMapping("y", "Identity");
        chm.addMapping("n", "Identity-Info");
        chm.addMapping("r", "Refer-To");
        chm.addMapping("b", "Referred-By");
        chm.addMapping("j", "Reject-Contact");
        chm.addMapping("d", "Request-Disposition");
        chm.addMapping("x", "Session-Expires");
        chm.addMapping("s", "Subject");
        chm.addMapping("k", "Supported");
        chm.addMapping("t", "To");
        chm.addMapping("v", "Via");
        chm.makeReadOnly();
        DEFAULT = chm;
    }
}
