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

import java.io.Serializable;

import org.apache.http.ProtocolVersion;


/**
 * Represents an SIP version, as specified in RFC 3261.
 *
 *
 */
public final class SipVersion extends ProtocolVersion
    implements Serializable {

    private static final long serialVersionUID = 6112302080954348220L;

    /** The protocol name. */
    public static final String SIP = "SIP";

    /** SIP protocol version 2.0 */
    public static final SipVersion SIP_2_0 = new SipVersion(2, 0);


    /**
     * Create a SIP protocol version designator.
     *
     * @param major   the major version number of the SIP protocol
     * @param minor   the minor version number of the SIP protocol
     *
     * @throws IllegalArgumentException
     *         if either major or minor version number is negative
     */
    public SipVersion(int major, int minor) {
        super(SIP, major, minor);
    }


    /**
     * Obtains a specific SIP version.
     *
     * @param major     the major version
     * @param minor     the minor version
     *
     * @return  an instance of {@link SipVersion} with the argument version
     */
    @Override
    public ProtocolVersion forVersion(int major, int minor) {

        if ((major == this.major) && (minor == this.minor)) {
            return this;
        }

        if ((major == 2) && (minor == 0)) {
            return SIP_2_0;
        }

        // argument checking is done in the constructor
        return new SipVersion(major, minor);
    }

}
