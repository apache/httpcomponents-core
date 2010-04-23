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


import org.apache.http.Header;


/**
 * Represents an SIP (or HTTP) header field with an optional compact name.
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
public interface CompactHeader extends Header {

    /**
     * Obtains the name of this header in compact form, if there is one.
     *
     * @return  the compact name of this header, or
     *          <code>null</code> if there is none
     */
    String getCompactName()
        ;

}
