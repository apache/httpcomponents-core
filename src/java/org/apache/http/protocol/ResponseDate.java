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

package org.apache.http.protocol;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpMutableResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpStatus;
import org.apache.http.util.DateUtils;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class ResponseDate implements HttpResponseInterceptor {

    private static final String DATE_DIRECTIVE = "Date";
    
    private final DateFormat rfc1123; 
    
    public ResponseDate() {
        super();
        this.rfc1123 = new SimpleDateFormat(DateUtils.PATTERN_RFC1123, Locale.US);
        this.rfc1123.setTimeZone(DateUtils.GMT);
    }

    private String getCurrentDate() {
        synchronized (this.rfc1123) {
            return this.rfc1123.format(new Date());
        }
    }
    
    public void process(final HttpMutableResponse response, final HttpContext context) 
        throws HttpException, IOException {
        if (response == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        int status = response.getStatusLine().getStatusCode();
        if (status >= HttpStatus.SC_OK) {
            response.setHeader(new Header(DATE_DIRECTIVE, getCurrentDate(), true)); 
        }
    }
    
}
