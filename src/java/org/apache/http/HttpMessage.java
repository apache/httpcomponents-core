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

package org.apache.http;

import java.util.Iterator;

import org.apache.http.params.HttpParams;

/**
 * A generic HTTP message.
 * Holds what is common between requests and responses.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public interface HttpMessage {
    
    /**
     * Returns the HTTP version this message is compatible with.
     */
    HttpVersion getHttpVersion();

    /**
     * Checks if a certain header is present in this message. Header values are
     * ignored.
     * 
     * @param name the header name to check for.
     * @return true if at least one header with this name is present.
     */
    boolean containsHeader(String name);
    
    /**
     * Returns all the headers with a specified name of this message. Header values
     * are ignored. Headers are orderd in the sequence they will be sent over a
     * connection.
     * 
     * @param name the name of the headers to return.
     * @return the headers whose name property equals <code>name</code>.
     */
    Header[] getHeaders(String name);

    /**
     * Returns the first header with a specified name of this message. Header
     * values are ignored. If there is more than one matching header in the
     * message the first element of
     * 
     * @link #getHeaders(String) is returned.
     * @param name the name of the header to return.
     * @return the first header whose name property equals <code>name</code>.
     */
    Header getFirstHeader(String name);

    /**
     * Returns the last header with a specified name of this message. Header values
     * are ignored. If there is more than one matching header in the message the
     * last element of
     * 
     * @link #getHeaders(String) is returned.
     * @param name the name of the header to return.
     * @return the last header whose name property equals <code>name</code>.
     */
    Header getLastHeader(String name);

    /**
     * Returns all the headers of this message. Headers are orderd in the sequence
     * they will be sent over a connection.
     * 
     * @return
     */
    Header[] getAllHeaders();

    /**
     * Adds a header to this message. The header will be appended to the end of
     * the list.
     * 
     * @param header the header to append.
     */
    void addHeader(Header header);

    /**
     * Adds a header to this message. The new header will be appended to the end
     * of the list. Existing headers with the same name will be removed.
     * 
     * @param header the header to add.
     */
    void setHeader(Header header);

    /**
     * Removes a header from this message.
     * 
     * @param header the header to remove.
     */
    void removeHeader(Header header);
    
    /**
     * Removes all headers with a certain name from this message.
     * 
     * @param name The name of the headers to remove.
     */
    void removeHeaders(String name);
    
    /**
     * Returns an iterator of all the headers.
     * 
     * @return Iterator that returns Header objects in the sequence they are
     *         sent over a connection.
     */
    Iterator headerIterator();

    /**
     * Returns the parameters effective for this message as set by
     * @link #setParams(HttpParams).
     */
    HttpParams getParams();

    /**
     * Provides parameters to be used for the processing of this message.
     * @param params the parameters
     */
    void setParams(HttpParams params);
        
}
