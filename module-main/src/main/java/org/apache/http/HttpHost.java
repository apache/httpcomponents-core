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

import org.apache.http.util.CharArrayBuffer;
import org.apache.http.util.LangUtils;

/**
 * Holds all of the variables needed to describe an HTTP connection to a host. This includes 
 * remote host, port and scheme.
 * 
 * @author <a href="mailto:becke@u.washington.edu">Michael Becke</a>
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * @author Laura Werner
 * 
 * @since 3.0 
 */
public class HttpHost {
    /** The default scheme is "http". */
    public static final String DEFAULT_SCHEME_NAME = "http";
    
    /** The host to use. */
    private String hostname = null;

    /** The port to use. */
    private int port = -1;

    /** The scheme */
    private String schemeName = null;

    /**
     * Constructor for HttpHost.
     *   
     * @param hostname the hostname (IP or DNS name). Can be <code>null</code>.
     * @param port the port. Value <code>-1</code> can be used to set default scheme port
     * @param schemeName the name of the scheme. Value <code>null</code> can be used to set 
     *        default scheme
     */
    public HttpHost(final String hostname, int port, final String schemeName) {
        super();
        if (hostname == null) {
            throw new IllegalArgumentException("Host name may not be null");
        }
        this.hostname = hostname;
        if (schemeName != null) {
            this.schemeName = schemeName.toLowerCase();
        } else {
            this.schemeName = DEFAULT_SCHEME_NAME;
        }
        this.port = port;
    }

    /**
     * Constructor for HttpHost.
     *   
     * @param hostname the hostname (IP or DNS name). Can be <code>null</code>.
     * @param port the port. Value <code>-1</code> can be used to set default scheme port
     */
    public HttpHost(final String hostname, int port) {
        this(hostname, port, null);
    }
    
    /**
     * Constructor for HttpHost.
     *   
     * @param hostname the hostname (IP or DNS name). Can be <code>null</code>.
     */
    public HttpHost(final String hostname) {
        this(hostname, -1, null);
    }
    
    /**
     * Copy constructor for HttpHost
     * 
     * @param httphost the HTTP host to copy details from
     */
    public HttpHost (final HttpHost httphost) {
        super();
        this.hostname = httphost.hostname;
        this.port = httphost.port;
        this.schemeName = httphost.schemeName;
    }

    /**
     * Returns the host name (IP or DNS name).
     * 
     * @return the host name (IP or DNS name), or <code>null</code> if not set
     */
    public String getHostName() {
        return this.hostname;
    }

    /**
     * Returns the port.
     * 
     * @return the host port, or <code>-1</code> if not set
     */
    public int getPort() {
        return this.port;
    }

    /**
     * Returns the scheme.
     * @return The scheme.
     */
    public String getSchemeName() {
        return this.schemeName;
    }

    /**
     * Return the host uri.
     * 
     * @return The host uri.
     */
    public String toURI() {
    	CharArrayBuffer buffer = new CharArrayBuffer(32);        
        buffer.append(this.schemeName);
        buffer.append("://");
        buffer.append(this.hostname);
        if (this.port != -1) {
            buffer.append(':');
            buffer.append(Integer.toString(this.port));
        }
        return buffer.toString();
    }

    public String toHostString() {
    	CharArrayBuffer buffer = new CharArrayBuffer(32);        
        buffer.append(this.hostname);
        if (this.port != -1) {
            buffer.append(':');
            buffer.append(Integer.toString(this.port));
        }
        return buffer.toString();
    }

    /**
     * @see java.lang.Object#toString()
     */
    public String toString() {
        return toURI();
    }    
    
    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(final Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (obj instanceof HttpHost) {
            HttpHost that = (HttpHost) obj;
            return this.hostname.equalsIgnoreCase(that.hostname) 
                && this.port == that.port
                && this.schemeName.equals(that.schemeName);
        } else {
            return false;
        }
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.hostname.toUpperCase());
        hash = LangUtils.hashCode(hash, this.port);
        hash = LangUtils.hashCode(hash, this.schemeName);
        return hash;
    }

}
