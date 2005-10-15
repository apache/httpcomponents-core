/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 2002-2004 The Apache Software Foundation
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
 * Holds all of the variables needed to describe an HTTP connection to a proxy. Proxy hosts
 * always use plain HTTP connection when communicating with clients.
 * 
 * @author <a href="mailto:becke@u.washington.edu">Michael Becke</a>
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * @author <a href="mailto:oleg@ural.ru">Oleg Kalnichevski</a>
 * @author Laura Werner
 * 
 * @since 3.0 
 */
public class ProxyHost extends HttpHost {

    /**
     * Copy constructor for HttpHost
     * 
     * @param httpproxy the HTTP host to copy details from
     */
    public ProxyHost (final ProxyHost httpproxy) {
        super(httpproxy);
    }

    /**
     * Constructor for ProxyHost.
     *   
     * @param hostname the hostname (IP or DNS name). Can be <code>null</code>.
     * @param port the port. Value <code>-1</code> can be used to set default scheme port
     */
    public ProxyHost(final String hostname, int port) {
        super(hostname, port, Scheme.getScheme("http"));
    }
    
    /**
     * Constructor for HttpHost.
     *   
     * @param hostname the hostname (IP or DNS name). Can be <code>null</code>.
     */
    public ProxyHost(final String hostname) {
        this(hostname, -1);
    }
    
    /**
     * @see java.lang.Object#clone()
     */
    public Object clone() {
        return new ProxyHost(this);
    }    
    
}
