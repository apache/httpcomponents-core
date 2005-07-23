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

package org.apache.http;

import org.apache.http.util.ExceptionUtil;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class HttpRuntime {

    public static int JAVA_MAJOR_VER = 1;
    public static int JAVA_MINOR_VER = 0;
    
    static {
        initHttpRuntime();
    }
    
    private static void initHttpRuntime() {
        String verstr = (String) System.getProperty("java.specification.version");
        if (verstr == null) {
            throw new FatalError("Failed to determine Java specification version");
        }
        int i = verstr.indexOf(".");
        if (i == -1) {
            throw new FatalError("Invalid Java specification version: " + verstr);
        }
        try {
            JAVA_MAJOR_VER = Integer.parseInt(verstr.substring(0, i));
        } catch (NumberFormatException ex) {
            throw new FatalError("Invalid Java specification major version: " + verstr);
        }
        try {
            JAVA_MINOR_VER = Integer.parseInt(verstr.substring(i + 1));
        } catch (NumberFormatException ex) {
            throw new FatalError("Invalid Java specification minor version: " + verstr);
        }
    }
    
    public static boolean isNIOCapable() {
        return (JAVA_MAJOR_VER == 1 && JAVA_MINOR_VER >= 4) || JAVA_MAJOR_VER > 1;
    }
    
    public static boolean isSSLNIOCapable() {
        return (JAVA_MAJOR_VER == 1 && JAVA_MINOR_VER >= 5) || JAVA_MAJOR_VER > 1;
    }
    
    private HttpRuntime() {
        super();
    }
    
    public static class FatalError extends Error {
        
    	static final long serialVersionUID = 7154545611894022392L;
    	
        public FatalError(final String message) {
            super(message);
        }
        
        public FatalError(final String message, final Throwable cause) {
            super(message);
            ExceptionUtil.initCause(this, cause);
        }
        
    }
    
}
