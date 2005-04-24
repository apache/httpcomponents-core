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

package org.apache.http.entity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpIncomingEntity;
import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.params.HttpProtocolParams;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class EntityConsumer {
    
    private final HttpMessage message;
    private final HttpIncomingEntity entity;
    
    public EntityConsumer(final HttpResponse response) {
        super();
        if (response == null) {
            throw new IllegalArgumentException("HTTP response may not be null");
        }
        this.message = response;
        this.entity = (HttpIncomingEntity)response.getEntity();
    }

    public EntityConsumer(final HttpEntityEnclosingRequest request) {
        super();
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        this.message = request;
        this.entity = (HttpIncomingEntity)request.getEntity();
    }

    public static byte[] toByteArray(final HttpIncomingEntity entity) throws IOException {
        if (entity == null) {
            throw new IllegalArgumentException("HTTP entity may not be null");
        }
        InputStream instream = entity.getInputStream();
        if (instream == null) {
            return new byte[] {};
        }
        if (entity.getContentLength() > Integer.MAX_VALUE) {
            throw new IllegalStateException("HTTP entity too large to be buffered in memory");
        }
        int i = (int)entity.getContentLength();
        if (i == -1) {
            i = 0;
        }
        ByteArrayOutputStream outstream = new ByteArrayOutputStream(i);
        byte[] tmp = new byte[2048];
        int l;
        while((l = instream.read(tmp)) != -1) {
            outstream.write(tmp, 0, l);
        }
        return outstream.toByteArray();
    }
        
    public static String getContentCharSet(final HttpEntity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("HTTP entity may not be null");
        }
        String charset = null;
        if (entity.getContentType() != null) { 
            HeaderElement values[] = HeaderElement.parseElements(entity.getContentType());
            if (values.length > 0) {
                NameValuePair param = values[0].getParameterByName("charset");
                if (param != null) {
                    charset = param.getValue();
                }
            }
        }
        return charset;
    }

    public static String toString(
            final HttpIncomingEntity entity, final String defaultCharset) throws IOException {
        if (entity == null) {
            throw new IllegalArgumentException("HTTP entity may not be null");
        }
        if (entity.getInputStream() == null) {
            return "";
        }
        if (entity.getContentLength() > Integer.MAX_VALUE) {
            throw new IllegalStateException("HTTP entity too large to be buffered in memory");
        }
        String charset = getContentCharSet(entity);
        if (charset == null) {
            charset = defaultCharset;
        }
        if (charset == null) {
            charset = "ISO-8859-1";
        }
        Reader reader = new InputStreamReader(entity.getInputStream(), charset);
        StringBuffer buffer = new StringBuffer(); 
        char[] tmp = new char[1024];
        int l;
        while((l = reader.read(tmp)) != -1) {
            buffer.append(tmp, 0, l);
        }
        return buffer.toString();
    }

    public static String toString(final HttpIncomingEntity entity) throws IOException {
        return toString(entity, null);
    }

    public byte[] asByteArray() throws IOException {
        if (this.entity == null) {
            return new byte[] {};
        }
        return toByteArray(this.entity);
    }
    
    public String asString() throws IOException {
        if (this.entity == null) {
            return "";
        }
        HttpProtocolParams params = new HttpProtocolParams(this.message.getParams());
        return toString(this.entity, params.getContentCharset());
    }
    
}
