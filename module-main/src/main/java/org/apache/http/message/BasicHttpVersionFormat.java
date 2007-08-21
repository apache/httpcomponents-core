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


/**
 * Parser and formatter for {@link HttpVersion HttpVersion}.
 * 
 * @author <a href="mailto:oleg@ural.ru">Oleg Kalnichevski</a>
 *
 *
 * <!-- empty lines above to avoid 'svn diff' context problems -->
 * @version $Revision$
 * 
 * @since 4.0
 */
public class BasicHttpVersionFormat {

    /** Disabled default constructor. */
    private BasicHttpVersionFormat() {
        // no body
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
