/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.hc.core5.util;
/**
 * This class can be used to parse other classes containing constant definitions
 * in public static final members.
 *
 * @since 5.1
 */
public class Constant {

    /**
     * Private constructor so that no instances can be created. This class
     * contains only static constant.
     */
    private Constant() {
    }
    /**
     * A String for at sign ("@").
     * @since 5.1
     */
    public static final String AT = "@";
    /**
     * A char Double quote ('\"').
     * @since 5.1
     */
    public static final char DOUBLE_QUOTE = '\"';
    /**
     /**
     * A char for Backward slash / escape character ('\\').
     * @since 5.1
     */
    public static final char ESCAPE = '\\';
    /**
     * A char for Colon (':').
     * @since 5.1
     */
    public static final char COLON = ':';
    /**
     * A String for line breaks as CRLF ("\r\n").
     * @since 5.1
     */
    public static final String CRLF = "\r\n";
    /**
     * A String for slash separator ("/").
     * @since 5.1
     */
    public static final String FORWARD_SLASH = "/";



}
