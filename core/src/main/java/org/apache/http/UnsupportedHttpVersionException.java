/*
 * $HeadURL:https://svn.apache.org/repos/asf/jakarta/httpcomponents/trunk/coyote-httpconnector/src/java/org/apache/http/tcconnector/UnsupportedHttpVersionException.java $
 * $Revision:379772 $
 * $Date:2006-02-22 14:52:29 +0100 (Wed, 22 Feb 2006) $
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

import org.apache.http.ProtocolException;

/**
 * Indicates an unsupported version of the HTTP protocol.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision:379772 $
 */
public class UnsupportedHttpVersionException extends ProtocolException {

	static final long serialVersionUID = 6838964812421632743L;
	
    /**
     * Creates an exception without a detail message.
     */
    public UnsupportedHttpVersionException() {
        super();
    }

    /**
     * Creates an exception with the specified detail message.
     * 
     * @param message The exception detail message 
     */
    public UnsupportedHttpVersionException(final String message) {
        super(message);
    }

}
