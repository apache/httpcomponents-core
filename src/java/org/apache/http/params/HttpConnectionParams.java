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

/**
 * This class implements an adaptor around the {@link HttpParams} interface
 * to simplify manipulation of the HTTP connection specific parameters.
 * 
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * 
 * @version $Revision$
 * 
 * @since 3.0
 */
public final class HttpConnectionParams {

    /**
     * Defines the default socket timeout (<tt>SO_TIMEOUT</tt>) in milliseconds which is the 
     * timeout for waiting for data. A timeout value of zero is interpreted as an infinite 
     * timeout. This value is used when no socket timeout is set in the 
     * {@link HttpMethodParams HTTP method parameters}. 
     * <p>
     * This parameter expects a value of type {@link Integer}.
     * </p>
     * @see java.net.SocketOptions#SO_TIMEOUT
     */
    public static final String SO_TIMEOUT = "http.socket.timeout"; 

    /**
     * Determines whether Nagle's algorithm is to be used. The Nagle's algorithm 
     * tries to conserve bandwidth by minimizing the number of segments that are 
     * sent. When applications wish to decrease network latency and increase 
     * performance, they can disable Nagle's algorithm (that is enable TCP_NODELAY). 
     * Data will be sent earlier, at the cost of an increase in bandwidth consumption. 
     * <p>
     * This parameter expects a value of type {@link Boolean}.
     * </p>
     * @see java.net.SocketOptions#TCP_NODELAY
     */
    public static final String TCP_NODELAY = "http.tcp.nodelay"; 

    /**
     * Determines the size of the internal socket buffer used to buffer data
     * while receiving / transmitting HTTP messages 
     * <p>
     * This parameter expects a value of type {@link Integer}.
     * </p>
     */
    public static final String SOCKET_BUFFER_SIZE = "http.socket.buffer-size"; 

    /**
     * Sets SO_LINGER with the specified linger time in seconds. The maximum timeout 
     * value is platform specific. Value <tt>0</tt> implies that the option is disabled.
     * Value <tt>-1</tt> implies that the JRE default is used. The setting only affects 
     * socket close.  
     * <p>
     * This parameter expects a value of type {@link Integer}.
     * </p>
     * @see java.net.SocketOptions#SO_LINGER
     */
    public static final String SO_LINGER = "http.socket.linger"; 

    /**
     * Determines the timeout until a connection is etablished. A value of zero 
     * means the timeout is not used. The default value is zero.
     * <p>
     * This parameter expects a value of type {@link Integer}.
     * </p>
     */
    public static final String CONNECTION_TIMEOUT = "http.connection.timeout"; 

    /**
     * Determines whether stale connection check is to be used. Disabling 
     * stale connection check may result in slight performance improvement 
     * at the risk of getting an I/O error when executing a request over a
     * connection that has been closed at the server side. 
     * <p>
     * This parameter expects a value of type {@link Boolean}.
     * </p>
     */
    public static final String STALE_CONNECTION_CHECK = "http.connection.stalecheck"; 

    /**
     */
    private HttpConnectionParams() {
        super();
    }

    /**
     * Returns the default socket timeout (<tt>SO_TIMEOUT</tt>) in milliseconds which is the 
     * timeout for waiting for data. A timeout value of zero is interpreted as an infinite 
     * timeout. This value is used when no socket timeout is set in the 
     * {@link HttpMethodParams HTTP method parameters}. 
     *
     * @return timeout in milliseconds
     */
    public static int getSoTimeout(final HttpParams params) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        return params.getIntParameter(SO_TIMEOUT, 0);
    }

    /**
     * Sets the default socket timeout (<tt>SO_TIMEOUT</tt>) in milliseconds which is the 
     * timeout for waiting for data. A timeout value of zero is interpreted as an infinite 
     * timeout. This value is used when no socket timeout is set in the 
     * {@link HttpMethodParams HTTP method parameters}. 
     *
     * @param timeout Timeout in milliseconds
     */
    public static void setSoTimeout(final HttpParams params, int timeout) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        params.setIntParameter(SO_TIMEOUT, timeout);
        
    }

    /**
     * Tests if Nagle's algorithm is to be used.  
     *
     * @return <tt>true</tt> if the Nagle's algorithm is to NOT be used
     *   (that is enable TCP_NODELAY), <tt>false</tt> otherwise.
     */
    public static boolean getTcpNoDelay(final HttpParams params) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        return params.getBooleanParameter(TCP_NODELAY, true);
    }

    /**
     * Determines whether Nagle's algorithm is to be used. The Nagle's algorithm 
     * tries to conserve bandwidth by minimizing the number of segments that are 
     * sent. When applications wish to decrease network latency and increase 
     * performance, they can disable Nagle's algorithm (that is enable TCP_NODELAY). 
     * Data will be sent earlier, at the cost of an increase in bandwidth consumption. 
     *
     * @param value <tt>true</tt> if the Nagle's algorithm is to NOT be used
     *   (that is enable TCP_NODELAY), <tt>false</tt> otherwise.
     */
    public static void setTcpNoDelay(final HttpParams params, boolean value) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        params.setBooleanParameter(TCP_NODELAY, value);
    }

    public static int getSocketBufferSize(final HttpParams params) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        return params.getIntParameter(SOCKET_BUFFER_SIZE, -1);
    }
    
    public static void setSocketBufferSize(final HttpParams params, int size) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        params.setIntParameter(SOCKET_BUFFER_SIZE, size);
    }

    /**
     * Returns linger-on-close timeout. Value <tt>0</tt> implies that the option is 
     * disabled. Value <tt>-1</tt> implies that the JRE default is used.
     * 
     * @return the linger-on-close timeout
     */
    public static int getLinger(final HttpParams params) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        return params.getIntParameter(SO_LINGER, -1);
    }

    /**
     * Returns linger-on-close timeout. This option disables/enables immediate return 
     * from a close() of a TCP Socket. Enabling this option with a non-zero Integer 
     * timeout means that a close() will block pending the transmission and 
     * acknowledgement of all data written to the peer, at which point the socket is 
     * closed gracefully. Value <tt>0</tt> implies that the option is 
     * disabled. Value <tt>-1</tt> implies that the JRE default is used.
     *
     * @param value the linger-on-close timeout
     */
    public static void setLinger(final HttpParams params, int value) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        params.setIntParameter(SO_LINGER, value);
    }

    /**
     * Returns the timeout until a connection is etablished. A value of zero 
     * means the timeout is not used. The default value is zero.
     * 
     * @return timeout in milliseconds.
     */
    public static int getConnectionTimeout(final HttpParams params) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        return params.getIntParameter(CONNECTION_TIMEOUT, 0);
    }

    /**
     * Sets the timeout until a connection is etablished. A value of zero 
     * means the timeout is not used. The default value is zero.
     * 
     * @param timeout Timeout in milliseconds.
     */
    public static void setConnectionTimeout(final HttpParams params, int timeout) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        params.setIntParameter(CONNECTION_TIMEOUT, timeout);
    }
    
    /**
     * Tests whether stale connection check is to be used. Disabling 
     * stale connection check may result in slight performance improvement 
     * at the risk of getting an I/O error when executing a request over a
     * connection that has been closed at the server side. 
     * 
     * @return <tt>true</tt> if stale connection check is to be used, 
     *   <tt>false</tt> otherwise.
     */
    public static boolean isStaleCheckingEnabled(final HttpParams params) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        return params.getBooleanParameter(STALE_CONNECTION_CHECK, true);
    }

    /**
     * Defines whether stale connection check is to be used. Disabling 
     * stale connection check may result in slight performance improvement 
     * at the risk of getting an I/O error when executing a request over a
     * connection that has been closed at the server side. 
     * 
     * @param value <tt>true</tt> if stale connection check is to be used, 
     *   <tt>false</tt> otherwise.
     */
    public static void setStaleCheckingEnabled(final HttpParams params, boolean value) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        params.setBooleanParameter(STALE_CONNECTION_CHECK, value);
    }
}
