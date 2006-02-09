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

package org.apache.http.protocol;

import java.io.IOException;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpMutableResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpVersion;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class ResponseContent implements HttpResponseInterceptor {

    public ResponseContent() {
        super();
    }
    
    public void process(final HttpMutableResponse response, final HttpContext context) 
        throws HttpException, IOException {
        if (response == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        HttpVersion ver = response.getStatusLine().getHttpVersion();
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            long len = entity.getContentLength();
            if (entity.isChunked() && ver.greaterEquals(HttpVersion.HTTP_1_1)) {
                response.setHeader(new Header(HTTP.TRANSFER_ENCODING, HTTP.CHUNK_CODING, true));
                response.removeHeaders(HTTP.CONTENT_LEN);
            } else if (len >= 0) {
                response.setHeader(new Header(HTTP.CONTENT_LEN, 
                        Long.toString(entity.getContentLength()), true));
                response.removeHeaders(HTTP.TRANSFER_ENCODING);
            }
            // Specify a content type if known
            if (entity.getContentType() != null) {
                response.setHeader(entity.getContentType()); 
            }
            // Specify a content encoding if known
            if (entity.getContentEncoding() != null) {
                response.setHeader(entity.getContentEncoding()); 
            }
        }
    }
    
}
