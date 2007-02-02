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

package org.apache.http;

import java.io.Serializable;
import org.apache.http.util.CharArrayBuffer;

/**
 * Represents an HTTP version, as specified in RFC 2616.
 * 
 * @author <a href="mailto:oleg@ural.ru">Oleg Kalnichevski</a>
 * 
 * @version $Revision$ $Date$
 */
public final class HttpVersion implements Comparable, Serializable {

    static final long serialVersionUID = -3164547215216382904L;
    
    /** Major version number of the HTTP protocol */
    private int major = 0;

    /** Minor version number of the HTTP protocol */
    private int minor = 0;

    /** HTTP protocol version 0.9 */
    public static final HttpVersion HTTP_0_9 = new HttpVersion(0, 9);  

    /** HTTP protocol version 1.0 */
    public static final HttpVersion HTTP_1_0 = new HttpVersion(1, 0);  

    /** HTTP protocol version 1.1 */
    public static final HttpVersion HTTP_1_1 = new HttpVersion(1, 1);  
    
    /**
     * Create an HTTP protocol version designator.
     *
     * @param major   the major version number of the HTTP protocol
     * @param minor   the minor version number of the HTTP protocol
     * 
     * @throws IllegalArgumentException if either major or minor version number is negative
     */
    public HttpVersion(int major, int minor) {
        if (major < 0) {
            throw new IllegalArgumentException("HTTP major version number may not be negative");
        }
        this.major = major;
        if (minor < 0) {
            throw new IllegalArgumentException("HTTP minor version number may not be negative");
        }
        this.minor = minor;
    }
    
    /**
     * Returns the major version number of the HTTP protocol.
     * 
     * @return the major version number.
     */
    public int getMajor() {
        return major;
    }

    /**
     * Returns the minor version number of the HTTP protocol.
     * 
     * @return the minor version number.
     */
    public int getMinor() {
        return minor;
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    public int hashCode() {
        return this.major * 100000 + this.minor;
    }
        
    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof HttpVersion)) {
            return false;
        }
        return equals((HttpVersion)obj);  
    }

    /**
     * Compares this HTTP protocol version with another one.
     * 
     * @param anotherVer the version to be compared with.
     *  
     * @return a negative integer, zero, or a positive integer as this version is less than, 
     *    equal to, or greater than the specified version.     
     */
    public int compareTo(HttpVersion anotherVer) {
        if (anotherVer == null) {
            throw new IllegalArgumentException("Version parameter may not be null"); 
        }
        int delta = getMajor() - anotherVer.getMajor();
        if (delta == 0) {
            delta = getMinor() - anotherVer.getMinor();
        }
        return delta;
    }

    /**
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(Object o) {
        return compareTo((HttpVersion)o);
    }

    /**
     * Test if the HTTP protocol version is equal to the given number.
     * 
     * @return <tt>true</tt> if HTTP protocol version is given to the given number, 
     *         <tt>false</tt> otherwise.
     */
    public boolean equals(HttpVersion version) {
        return compareTo(version) == 0;  
    }

    /**
     * Test if the HTTP protocol version is greater or equal to the given number.
     * 
     * @return <tt>true</tt> if HTTP protocol version is greater or equal given to the 
     *         given number, <tt>false</tt> otherwise.
     */
    public boolean greaterEquals(HttpVersion version) {
        return compareTo(version) >= 0;
    }

    /**
     * Test if the HTTP protocol version is less or equal to the given number.
     * 
     * @return <tt>true</tt> if HTTP protocol version is less or equal to given to the 
     *         given number, <tt>false</tt> otherwise.
     */
    public boolean lessEquals(HttpVersion version) {
        return compareTo(version) <= 0;
    }

    /**
     * @see java.lang.Object#toString()
     */
    public String toString() {
        CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append("HTTP/"); 
        buffer.append(Integer.toString(this.major)); 
        buffer.append('.'); 
        buffer.append(Integer.toString(this.minor)); 
        return buffer.toString();
    }

}
