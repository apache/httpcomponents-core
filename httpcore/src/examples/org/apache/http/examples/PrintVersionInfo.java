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

package org.apache.http.examples;

import org.apache.http.util.VersionInfo;

/**
 * Prints version information for debugging purposes.
 * This can be used to verify that the correct versions of the
 * HttpComponent JARs are picked up from the classpath.
 *
 *
 */
public class PrintVersionInfo {

    /** A default list of module packages. */
    private final static String[] MODULE_LIST = {
        "org.apache.http",              // HttpCore
        "org.apache.http.nio",          // HttpCore NIO
        "org.apache.http.client",       // HttpClient
    };


    /**
     * Prints version information.
     *
     * @param args      command line arguments. Leave empty to print version
     *                  information for the default packages. Otherwise, pass
     *                  a list of packages for which to get version info.
     */
    public static void main(String args[]) {
        String[]    pckgs = (args.length > 0) ? args : MODULE_LIST;
        VersionInfo[] via = VersionInfo.loadVersionInfo(pckgs, null);
        System.out.println("version info for thread context classloader:");
        for (int i=0; i<via.length; i++)
            System.out.println(via[i]);

        System.out.println();

        // if the version information for the classloader of this class
        // is different from that for the thread context classloader,
        // there may be a problem with multiple versions in the classpath

        via = VersionInfo.loadVersionInfo
            (pckgs, PrintVersionInfo.class.getClassLoader());
        System.out.println("version info for static classloader:");
        for (int i=0; i<via.length; i++)
            System.out.println(via[i]);
    }
}

