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
import org.apache.http.ParseException;
import org.apache.http.util.CharArrayBuffer;
import org.apache.http.message.BasicLineParser;


/**
 * Basic parser for lines in the head section of an SIP message.
 *
 *
 */
public class BasicSipLineParser extends BasicLineParser {

    /** The header name mapper to use, never <code>null</code>. */
    protected final CompactHeaderMapper mapper;


    /**
     * A default instance of this class, for use as default or fallback.
     */
    public final static
        BasicSipLineParser DEFAULT = new BasicSipLineParser(null);


    /**
     * Creates a new line parser for SIP protocol.
     *
     * @param mapper    the header name mapper, or <code>null</code> for the
     *                  {@link BasicCompactHeaderMapper#DEFAULT default}
     */
    public BasicSipLineParser(CompactHeaderMapper mapper) {
        super(SipVersion.SIP_2_0);
        this.mapper = (mapper != null) ?
            mapper : BasicCompactHeaderMapper.DEFAULT;
    }


    // non-javadoc, see interface LineParser
    @Override
    public Header parseHeader(CharArrayBuffer buffer)
        throws ParseException {

        // the actual parser code is in the constructor of BufferedHeader
        return new BufferedCompactHeader(buffer, mapper);
    }

}

