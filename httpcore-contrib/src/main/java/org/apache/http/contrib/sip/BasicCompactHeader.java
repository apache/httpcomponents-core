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

import org.apache.http.message.BasicHeader;


/**
 * Represents a SIP (or HTTP) header field with optional compact name.
 *
 *
 */
public class BasicCompactHeader extends BasicHeader
    implements CompactHeader {

    private static final long serialVersionUID = -8275767773930430518L;

    /** The compact name, if there is one. */
    private final String compact;


    /**
     * Constructor with names and value.
     *
     * @param fullname          the full header name
     * @param compactname       the compact header name, or <code>null</code>
     * @param value             the header value
     */
    public BasicCompactHeader(final String fullname,
                              final String compactname,
                              final String value) {
        super(fullname, value);

        if ((compactname != null) &&
            (compactname.length() >= fullname.length()))  {
            throw new IllegalArgumentException
                ("Compact name must be shorter than full name. " +
                 compactname + " -> " + fullname);
        }

        this.compact = compactname;
    }


    // non-javadoc, see interface CompactHeader
    public String getCompactName() {
        return this.compact;
    }


    /**
     * Creates a compact header with automatic lookup.
     *
     * @param name      the header name, either full or compact
     * @param value     the header value
     * @param mapper    the header name mapper, or <code>null</code> for the
     *                  {@link BasicCompactHeaderMapper#DEFAULT default}
     */
    public static
        BasicCompactHeader newHeader(final String name, final String value,
                                     CompactHeaderMapper mapper) {
        if (name == null) {
            throw new IllegalArgumentException
                ("The name must not be null.");
        }
        // value will be checked by constructor later

        if (mapper == null)
            mapper = BasicCompactHeaderMapper.DEFAULT;

        final String altname = mapper.getAlternateName(name);

        String fname = name;
        String cname = altname;

        if ((altname != null) && (name.length() < altname.length())) {
            // we were called with the compact name
            fname = altname;
            cname = name;
        }

        return new BasicCompactHeader(fname, cname, value);
    }


    // we might want a toString method that includes the short name, if defined

    // default cloning implementation is fine
}
