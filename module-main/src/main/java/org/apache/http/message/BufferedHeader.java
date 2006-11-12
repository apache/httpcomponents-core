/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2006 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.message;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.util.CharArrayBuffer;

/**
 * This class represents a raw HTTP header whose content is parsed 'on demand' only when 
 * the header value needs to be consumed.
 * 
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 *
 * <!-- empty lines above to avoid 'svn diff' context problems -->
 * @version $Revision$ $Date$
 */
public class BufferedHeader implements Header {

    /**
     * Header name.
     */
    private final String name;

    /**
     * The buffer containing the entire header line.
     */
    private final CharArrayBuffer buffer;
    
    /**
     * The beginning of the header value in the buffer
     */
    private final int valuePos;


    /**
     * Creates a new header from a buffer.
     * The name of the header will be parsed immediately,
     * the value only if it is accessed.
     *
     * @param buffer    the buffer containing the header to represent
     */
    public BufferedHeader(final CharArrayBuffer buffer) {
        super();
        if (buffer == null) {
            throw new IllegalArgumentException("Char array buffer may not be null");
        }
        int colon = buffer.indexOf(':');
        if (colon == -1) {
            throw new IllegalArgumentException("Invalid header: " + buffer.toString());
        }
        String s = buffer.substringTrimmed(0, colon);
        if (s.equals("")) {
            throw new IllegalArgumentException("Invalid header: " + buffer.toString());
        }
        this.buffer = buffer;
        this.name = s;
        this.valuePos = colon + 1;
    }

    public String getName() {
        return this.name;
    }

    public String getValue() {
        return this.buffer.substringTrimmed(this.valuePos, this.buffer.length());
    }
    
    public HeaderElement[] getElements() {
        return BasicHeaderElement.parseAll(this.buffer, this.valuePos, this.buffer.length());
    }

    public int getValuePos() {
        return this.valuePos;
    }
    
    public CharArrayBuffer getBuffer() {
        return this.buffer;
    }

    public String toString() {
        return this.buffer.toString();
    }

}
