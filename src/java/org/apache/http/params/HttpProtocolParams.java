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

package org.apache.http.params;

import org.apache.http.HttpVersion;

/**
 * This class implements an adaptor around the {@link HttpParams} interface
 * to simplify manipulation of the HTTP connection specific parameters.
 * 
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * 
 * @version $Revision$
 * 
 * @since 4.0
 */
public class HttpProtocolParams {

    /**
     * Defines the {@link HttpVersion HTTP protocol version} used per default.
     * <p>
     * This parameter expects a value of type {@link HttpVersion}.
     * </p>
     */
    public static final String PROTOCOL_VERSION = "http.protocol.version"; 

    /**
     * Defines the charset to be used when encoding 
     * {@link org.apache.commons.httpclient.Credentials}. If not defined then the 
     * {@link #HTTP_ELEMENT_CHARSET} should be used.
     * <p>
     * This parameter expects a value of type {@link String}.
     * </p>
     */
    public static final String CREDENTIAL_CHARSET = "http.protocol.credential-charset"; 
    
    /**
     * Defines the charset to be used for encoding HTTP protocol elements.
     * <p>
     * This parameter expects a value of type {@link String}.
     * </p>
     */
    public static final String HTTP_ELEMENT_CHARSET = "http.protocol.element-charset"; 
    
    /**
     * Defines the charset to be used per default for encoding content body.
     * <p>
     * This parameter expects a value of type {@link String}.
     * </p>
     */
    public static final String HTTP_CONTENT_CHARSET = "http.protocol.content-charset"; 
    
    private final HttpParams params;
    
    /**
     */
    public HttpProtocolParams(final HttpParams params) {
        super();
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.params = params;
    }

    public Object getParameter(final String name) {
        return this.params.getParameter(name);
    }

    public HttpProtocolParams setParameter(final String name, final Object value) {
        this.params.setParameter(name, value);
        return this;
    }
        
    public long getLongParameter(final String name, long defaultValue) { 
        return this.params.getLongParameter(name, defaultValue);
    }
    
    public HttpProtocolParams setLongParameter(final String name, long value) {
        this.params.setLongParameter(name, value);
        return this;
    }

    public int getIntParameter(final String name, int defaultValue) { 
        return this.params.getIntParameter(name, defaultValue);
    }

    public HttpProtocolParams setIntParameter(final String name, int value) {
        this.params.setIntParameter(name, value);
        return this;
    }

    public double getDoubleParameter(final String name, double defaultValue) { 
        return this.params.getDoubleParameter(name, defaultValue);
    }
    
    public HttpProtocolParams setDoubleParameter(final String name, double value) {
        this.params.setDoubleParameter(name, value);
        return this;
    }

    public boolean getBooleanParameter(final String name, boolean defaultValue) { 
        return this.params.getBooleanParameter(name, defaultValue);
    }
    
    public HttpProtocolParams setBooleanParameter(final String name, boolean value) {
        this.params.setBooleanParameter(name, value);
        return this;
    }

    /**
     * Returns the charset to be used for writing HTTP headers.
     * @return The charset
     */
    public String getHttpElementCharset() {
        String charset = (String) getParameter(HTTP_ELEMENT_CHARSET);
        if (charset == null) {
            charset = "US-ASCII";
        }
        return charset;
    }
    
    /**
     * Sets the charset to be used for writing HTTP headers.
     * @param charset The charset
     */
    public HttpProtocolParams setHttpElementCharset(final String charset) {
        return setParameter(HTTP_ELEMENT_CHARSET, charset);
    }

    /**
     * Returns the default charset to be used for writing content body, 
     * when no charset explicitly specified.
     * @return The charset
     */
    public String getContentCharset() {
        String charset = (String) getParameter(HTTP_CONTENT_CHARSET);
        if (charset == null) {
            charset = "ISO-8859-1";
        }
        return charset;
    }
    
    /**
     * Sets the default charset to be used for writing content body,
     * when no charset explicitly specified.
     * @param charset The charset
     */
    public HttpProtocolParams setContentCharset(final String charset) {
        return setParameter(HTTP_CONTENT_CHARSET, charset);
    }

    /**
     * Returns the charset to be used for {@link org.apache.commons.httpclient.Credentials}. If
     * not configured the {@link #HTTP_ELEMENT_CHARSET HTTP element charset} is used.
     * @return The charset
     */
    public String getCredentialCharset() {
        String charset = (String) getParameter(CREDENTIAL_CHARSET);
        if (charset == null) {
            charset = getHttpElementCharset();
        }
        return charset;
    }
    
    /**
     * Sets the charset to be used for writing HTTP headers.
     * @param charset The charset
     */
    public HttpProtocolParams setCredentialCharset(final String charset) {
        return setParameter(CREDENTIAL_CHARSET, charset);
    }
    
    /**
     * Returns {@link HttpVersion HTTP protocol version} to be used per default. 
     *
     * @return {@link HttpVersion HTTP protocol version}
     */
    public HttpVersion getVersion() { 
        Object param = getParameter(PROTOCOL_VERSION);
        if (param == null) {
            return HttpVersion.HTTP_1_1;
        }
        return (HttpVersion)param;
    }
    
    /**
     * Assigns the {@link HttpVersion HTTP protocol version} to be used by the 
     * {@link org.apache.commons.httpclient.HttpMethod HTTP methods} that 
     * this collection of parameters applies to. 
     *
     * @param version the {@link HttpVersion HTTP protocol version}
     */
    public HttpProtocolParams setVersion(final HttpVersion version) {
        return setParameter(PROTOCOL_VERSION, version);
    }

}
