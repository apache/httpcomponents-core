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

package org.apache.http.params;

import org.apache.http.HttpVersion;
import org.apache.http.protocol.HTTP;

/**
 * This class implements an adaptor around the {@link HttpParams} interface
 * to simplify manipulation of the HTTP protocol specific parameters.
 * 
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * 
 * @version $Revision$
 * 
 * @since 4.0
 */
public final class HttpProtocolParams {

    /**
     * Defines the {@link HttpVersion HTTP protocol version} used per default.
     * <p>
     * This parameter expects a value of type {@link HttpVersion}.
     * </p>
     */
    public static final String PROTOCOL_VERSION = "http.protocol.version"; 

    /**
     * Defines the charset to be used when encoding credentials.
     * If not defined then the 
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
    
    /**
     * Defines the content of the <tt>User-Agent</tt> header.
     * <p>
     * This parameter expects a value of type {@link String}.
     * </p>
     */
    public static final String USER_AGENT = "http.useragent"; 

    /**
     * Defines the content of the <tt>Server</tt> header.
     * <p>
     * This parameter expects a value of type {@link String}.
     * </p>
     */
    public static final String ORIGIN_SERVER = "http.origin-server"; 

    /**
     * Defines the maximum number of ignorable lines before we expect
     * a HTTP response's status code.
     * <p>
     * With HTTP/1.1 persistent connections, the problem arises that
     * broken scripts could return a wrong Content-Length
     * (there are more bytes sent than specified).<br />
     * Unfortunately, in some cases, this is not possible after the bad response,
     * but only before the next one. <br />
     * So, HttpClient must be able to skip those surplus lines this way.
     * </p>
     * <p>
     * Set this to 0 to disallow any garbage/empty lines before the status line.<br />
     * To specify no limit, use {@link java.lang.Integer#MAX_VALUE} (default in lenient mode).
     * </p>
     *  
     * This parameter expects a value of type {@link Integer}.
     */
    public static final String STATUS_LINE_GARBAGE_LIMIT = "http.protocol.status-line-garbage-limit";

    /**
     * The key used to look up the date patterns used for parsing. The String patterns are stored
     * in a {@link java.util.Collection} and must be compatible with 
     * {@link java.text.SimpleDateFormat}.
     * <p>
     * This parameter expects a value of type {@link java.util.Collection}.
     * </p>
     */
    public static final String DATE_PATTERNS = "http.dateparser.patterns";

    /**
     * Defines the virtual host name.
     * <p>
     * This parameter expects a value of type {@link java.lang.String}. 
     * </p>
     */
    public static final String VIRTUAL_HOST = "http.virtual-host"; 

    /**
     * Defines whether responses with an invalid <tt>Transfer-Encoding</tt> header should be 
     * rejected.
     * <p>
     * This parameter expects a value of type {@link Boolean}.
     * </p>
     */
    public static final String STRICT_TRANSFER_ENCODING = "http.protocol.strict-transfer-encoding"; 

    /**
     * <p>
     * Activates 'Expect: 100-Continue' handshake for the 
     * entity enclosing methods. The purpose of the 'Expect: 100-Continue'
     * handshake to allow a client that is sending a request message with 
     * a request body to determine if the origin server is willing to 
     * accept the request (based on the request headers) before the client
     * sends the request body.
     * </p>
     * 
     * <p>
     * The use of the 'Expect: 100-continue' handshake can result in 
     * noticable peformance improvement for entity enclosing requests
     * (such as POST and PUT) that require the target server's 
     * authentication.
     * </p>
     * 
     * <p>
     * 'Expect: 100-continue' handshake should be used with 
     * caution, as it may cause problems with HTTP servers and 
     * proxies that do not support HTTP/1.1 protocol.
     * </p>
     * 
     * This parameter expects a value of type {@link Boolean}.
     */
    public static final String USE_EXPECT_CONTINUE = "http.protocol.expect-continue"; 

    /**
     */
    private HttpProtocolParams() {
        super();
    }

    /**
     * Returns the charset to be used for writing HTTP headers.
     * @return The charset
     */
    public static String getHttpElementCharset(final HttpParams params) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        String charset = (String) params.getParameter(HTTP_ELEMENT_CHARSET);
        if (charset == null) {
            charset = HTTP.DEFAULT_PROTOCOL_CHARSET;
        }
        return charset;
    }
    
    /**
     * Sets the charset to be used for writing HTTP headers.
     * @param charset The charset
     */
    public static void setHttpElementCharset(final HttpParams params, final String charset) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        params.setParameter(HTTP_ELEMENT_CHARSET, charset);
    }

    /**
     * Returns the default charset to be used for writing content body, 
     * when no charset explicitly specified.
     * @return The charset
     */
    public static String getContentCharset(final HttpParams params) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        String charset = (String) params.getParameter(HTTP_CONTENT_CHARSET);
        if (charset == null) {
            charset = HTTP.DEFAULT_CONTENT_CHARSET;
        }
        return charset;
    }
    
    /**
     * Sets the default charset to be used for writing content body,
     * when no charset explicitly specified.
     * @param charset The charset
     */
    public static void setContentCharset(final HttpParams params, final String charset) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        params.setParameter(HTTP_CONTENT_CHARSET, charset);
    }

    /**
     * Returns the charset to be used for credentials.
     * If not configured the {@link #HTTP_ELEMENT_CHARSET HTTP element charset}
     * is used.
     * @return The charset
     */
    public static String getCredentialCharset(final HttpParams params) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        String charset = (String) params.getParameter(CREDENTIAL_CHARSET);
        if (charset == null) {
            charset = getHttpElementCharset(params);
        }
        return charset;
    }
    
    /**
     * Sets the charset to be used for writing HTTP headers.
     * @param charset The charset
     */
    public static void setCredentialCharset(final HttpParams params, final String charset) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        params.setParameter(CREDENTIAL_CHARSET, charset);
    }
    
    /**
     * Returns {@link HttpVersion HTTP protocol version} to be used per default. 
     *
     * @return {@link HttpVersion HTTP protocol version}
     */
    public static HttpVersion getVersion(final HttpParams params) { 
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        Object param = params.getParameter(PROTOCOL_VERSION);
        if (param == null) {
            return HttpVersion.HTTP_1_1;
        }
        return (HttpVersion)param;
    }
    
    /**
     * Assigns the {@link HttpVersion HTTP protocol version} to be used by the 
     * HTTP methods that this collection of parameters applies to. 
     *
     * @param version the {@link HttpVersion HTTP protocol version}
     */
    public static void setVersion(final HttpParams params, final HttpVersion version) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        params.setParameter(PROTOCOL_VERSION, version);
    }

    /**
     * Sets the virtual host name.
     * 
     * @param hostname The host name
     */
    public static void setVirtualHost(final HttpParams params, final String hostname) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        params.setParameter(VIRTUAL_HOST, hostname);
    }

    /**
     * Returns the virtual host name.
     * 
     * @return The virtual host name
     */
    public static String getVirtualHost(final HttpParams params) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        return (String) params.getParameter(VIRTUAL_HOST);
    }
    
    public static String getUserAgent(final HttpParams params) { 
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        return (String) params.getParameter(USER_AGENT);
    }
    
    public static void setUserAgent(final HttpParams params, final String useragent) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        params.setParameter(USER_AGENT, useragent);
    }

    public static boolean useExpectContinue(final HttpParams params) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        return params.getBooleanParameter(HttpProtocolParams.USE_EXPECT_CONTINUE, false);
    }
    
    public static void setUseExpectContinue(final HttpParams params, boolean b) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        params.setBooleanParameter(HttpProtocolParams.USE_EXPECT_CONTINUE, b);
    }
}
