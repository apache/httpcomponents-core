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

import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.CharArrayBuffer;

public class BasicHttpVersion {

    /**
     * Parses the textual representation of the given HTTP protocol version.
     * 
     * @return HTTP protocol version.
     * 
     * @throws ProtocolException if the string is not a valid HTTP protocol version. 
     */
    public static HttpVersion parse(
            final CharArrayBuffer buffer, final int indexFrom, final int indexTo) 
            throws ProtocolException {
        if (buffer == null) {
            throw new IllegalArgumentException("Char array buffer may not be null");
        }
        if (indexFrom < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (indexTo > buffer.length()) {
            throw new IndexOutOfBoundsException();
        }
        if (indexFrom > indexTo) {
            throw new IndexOutOfBoundsException();
        }
        try {
            int major, minor;

            int i = indexFrom;
            while (HTTP.isWhitespace(buffer.charAt(i))) {
                i++;
            }            
            if (buffer.charAt(i    ) != 'H' 
             || buffer.charAt(i + 1) != 'T'
             || buffer.charAt(i + 2) != 'T'
             || buffer.charAt(i + 3) != 'P'
             || buffer.charAt(i + 4) != '/') {
                throw new ProtocolException("Not a valid HTTP version string: " + 
                        buffer.substring(indexFrom, indexTo));
            }
            i += 5;
            int period = buffer.indexOf('.', i, indexTo);
            if (period == -1) {
                throw new ProtocolException("Invalid HTTP version number: " + 
                        buffer.substring(indexFrom, indexTo));
            }
            try {
                major = Integer.parseInt(buffer.substringTrimmed(i, period)); 
            } catch (NumberFormatException e) {
                throw new ProtocolException("Invalid HTTP major version number: " + 
                        buffer.substring(indexFrom, indexTo));
            }
            try {
                minor = Integer.parseInt(buffer.substringTrimmed(period + 1, indexTo)); 
            } catch (NumberFormatException e) {
                throw new ProtocolException("Invalid HTTP minor version number: " + 
                        buffer.substring(indexFrom, indexTo));
            }
            return new HttpVersion(major, minor);
            
        } catch (IndexOutOfBoundsException e) {
            throw new ProtocolException("Invalid HTTP version string: " + 
                    buffer.substring(indexFrom, indexTo)); 
        }
    }

    public static final HttpVersion parse(final String s)
            throws ProtocolException {
        if (s == null) {
            throw new IllegalArgumentException("String may not be null");
        }
        CharArrayBuffer buffer = new CharArrayBuffer(s.length()); 
        buffer.append(s);
        return parse(buffer, 0, buffer.length());
    }

    public static void format(final CharArrayBuffer buffer, final HttpVersion ver) {
        if (buffer == null) {
            throw new IllegalArgumentException("String buffer may not be null");
        }
        if (ver == null) {
            throw new IllegalArgumentException("Version may not be null");
        }
        buffer.append("HTTP/"); 
        buffer.append(Integer.toString(ver.getMajor())); 
        buffer.append('.'); 
        buffer.append(Integer.toString(ver.getMinor())); 
    }
 
    public static String format(final HttpVersion ver) {
        CharArrayBuffer buffer = new CharArrayBuffer(16);
        format(buffer, ver);
        return buffer.toString();
    }
        
}
