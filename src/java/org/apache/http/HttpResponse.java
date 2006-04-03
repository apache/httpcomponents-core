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

/**
 * An HTTP response.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public interface HttpResponse extends HttpMessage {

    /**
     * Returns the status line that belongs to this response as set by
     * @link #setStatusLine(StatusLine).
     */
    StatusLine getStatusLine();

    /**
     * Sets the status line that belongs to this response.
     * @param statusline the status line of this response.
     */
    void setStatusLine(StatusLine statusline);
    
    /**
     * Convenience method that creates and sets a new status line of this
     * response that is initialized with the specified status code.
     * 
     * @param code the HTTP status code.
     * @see HttpStatus
     */
    void setStatusCode(int code);
    
    /**
     * Returns the response entity of this response as set by
     * @link #setEntity(HttpEntity).
     * @return the response entity or <code>null</code> if there is none.
     */
    HttpEntity getEntity();
    
    /**
     * Associates a response entity with this response.
     * @param entity the entity to associate with this response.
     */
    void setEntity(HttpEntity entity);
    
}
