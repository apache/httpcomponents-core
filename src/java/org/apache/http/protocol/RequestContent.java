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

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpMutableRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class RequestContent implements HttpRequestInterceptor {

    private static final String TRANSFER_ENC = "Transfer-Encoding";
    private static final String CONTENT_LEN  = "Content-Length";
    private static final String CONTENT_TYPE = "Content-Type";
    
    public RequestContent() {
        super();
    }
    
    public void process(final HttpMutableRequest request, final HttpContext context) 
        throws HttpException, IOException {
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        if (request instanceof HttpEntityEnclosingRequest) {
            HttpVersion ver = request.getRequestLine().getHttpVersion();
            HttpEntity entity = ((HttpEntityEnclosingRequest)request).getEntity();
            // Must specify a transfer encoding or a content length 
            if (entity.isChunked() || entity.getContentLength() < 0) {
                if (ver.lessEquals(HttpVersion.HTTP_1_0)) {
                    throw new ProtocolException(
                            "Chunked transfer encoding not allowed for " + ver);
                }
                request.setHeader(new Header(TRANSFER_ENC, "chunked", true));
                request.removeHeaders(CONTENT_LEN);
            } else {
                request.setHeader(new Header(CONTENT_LEN, 
                        Long.toString(entity.getContentLength()), true));
                request.removeHeaders(TRANSFER_ENC);
            }
            // Specify a content type if known
            if (entity.getContentType() != null) {
                request.setHeader(new Header(CONTENT_TYPE, entity.getContentType(), true)); 
            }
        }
    }
    
}
