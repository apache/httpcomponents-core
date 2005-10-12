/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2004 The Apache Software Foundation
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

package org.apache.http.util;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.io.HttpDataReceiver;

/**
 * A utility class for parsing http header values according to
 * RFC-2616 Section 4 and 19.3.
 * 
 * @author Michael Becke
 * @author <a href="mailto:oleg@ural.ru">Oleg Kalnichevski</a>
 * 
 * @version $Revision$
 * 
 * @since 4.0
 */
public class HeadersParser  {

    private HeadersParser() {
        super();
    }
    
    public static Header[] processHeaders(final HttpDataReceiver datareceiver) 
            throws HttpException, IOException {
        ArrayList headerLines = new ArrayList();
        for (;;) {
            String line = datareceiver.readLine();
            if ((line == null) || (line.length() < 1)) {
                break;
            }
            // Parse the header name and value
            // Check for folded headers first
            // Detect LWS-char see HTTP/1.0 or HTTP/1.1 Section 2.2
            // discussion on folded headers
            if ((line.charAt(0) == ' ' || line.charAt(0) == '\t') && !headerLines.isEmpty()) {
                // we have continuation folded header
                // so append value
                String previousLine = (String) headerLines.remove(headerLines.size() - 1);
                CharArrayBuffer buffer = new CharArrayBuffer(128);
                buffer.append(previousLine);
                buffer.append(' ');
                buffer.append(line.trim());
                headerLines.add(buffer.toString());
            } else {
                headerLines.add(line.trim());
            }
        }
        Header[] headers = new Header[headerLines.size()];
        for (int i = 0; i < headerLines.size(); i++) {
            headers[i] = Header.parse((String) headerLines.get(i));
        }
        return headers;
    }
    
}
