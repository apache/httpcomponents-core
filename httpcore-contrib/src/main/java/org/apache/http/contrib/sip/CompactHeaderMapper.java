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



/**
 * A mapper between full and compact header names.
 * RFC 3261 (SIP/2.0), section 7.3.3 specifies that some header field
 * names have an abbreviated form which is equivalent to the full name.
 * All compact header names defined for SIP are registered at
 * <a href="http://www.iana.org/assignments/sip-parameters">
 * http://www.iana.org/assignments/sip-parameters
 * </a>.
 * <br/>
 * While all compact names defined so far are single-character names,
 * RFC 3261 does not mandate that. This interface therefore allows for
 * strings as the compact name.
 *
 *
 */
public interface CompactHeaderMapper {

    /**
     * Obtains the compact name for the given full name.
     *
     * @param fullname  the header name for which to look up the compact form
     *
     * @return  the compact form of the argument header name, or
     *          <code>null</code> if there is none
     */
    String getCompactName(String fullname)
        ;


    /**
     * Obtains the full name for the given compact name.
     *
     * @param compactname  the compact name for which to look up the full name
     *
     * @return  the full name of the argument compact header name, or
     *          <code>null</code> if there is none
     */
    String getFullName(String compactname)
        ;


    /**
     * Obtains the alternate name for the given header name.
     * This performs a lookup in both directions, if necessary.
     * <br/>
     * If the returned name is shorter than the argument name,
     * the argument was a full header name and the result is
     * the compact name.
     * If the returned name is longer than the argument name,
     * the argument was a compact header name and the result
     * is the full name.
     * If the returned name has the same length as the argument name,
     * somebody didn't understand the concept of a <i>compact form</i>
     * when defining the mapping. You should expect malfunctioning
     * applications in this case.
     *
     * @param name      the header name to map, either a full or compact name
     *
     * @return  the alternate header name, or
     *          <code>null</code> if there is none
     */
    String getAlternateName(String name)
        ;
}
