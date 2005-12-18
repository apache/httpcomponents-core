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

package org.apache.http.impl;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class DefaultResponseStrategy implements ResponseStrategy {

	private static final String HEAD_METHOD = "HEAD"; 
	
	public DefaultResponseStrategy() {
		super();
	}
	
    public boolean canHaveEntity(final HttpRequest request, final HttpResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("HTTP response may not be null");
        }
        if (request != null) {
            String method = request.getRequestLine().getMethod();
            if (HEAD_METHOD.equalsIgnoreCase(method)) {
                return false;
            }
        }
        int status = response.getStatusLine().getStatusCode();
        if (status < HttpStatus.SC_OK) {
        	return false;
        }
        if (status == HttpStatus.SC_NO_CONTENT || 
        		status == HttpStatus.SC_RESET_CONTENT ||
        		status == HttpStatus.SC_NOT_MODIFIED) {
        	return false;
        }
        return true;
    }
            
}
