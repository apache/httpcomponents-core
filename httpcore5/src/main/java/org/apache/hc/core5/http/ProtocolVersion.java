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

package org.apache.hc.core5.http;

import java.io.Serializable;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;

/**
 * Represents a protocol version. The "major.minor" numbering
 * scheme is used to indicate versions of the protocol.
 * <p>
 * This class defines a protocol version as a combination of
 * protocol name, major version number, and minor version number.
 * Note that {@link #equals} and {@link #hashCode} are defined as
 * final here, they cannot be overridden in derived classes.
 * </p>
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class ProtocolVersion implements Serializable {

    private static final long serialVersionUID = 8950662842175091068L;


    /** Name of the protocol. */
    private final String protocol;

    /** Major version number of the protocol */
    private final int major;

    /** Minor version number of the protocol */
    private final int minor;

    /**
     * Create a protocol version designator.
     *
     * @param protocol   the name of the protocol, for example "HTTP"
     * @param major      the major version number of the protocol
     * @param minor      the minor version number of the protocol
     */
    public ProtocolVersion(final String protocol, final int major, final int minor) {
        this.protocol = Args.notNull(protocol, "Protocol name");
        this.major = Args.notNegative(major, "Protocol minor version");
        this.minor = Args.notNegative(minor, "Protocol minor version");
    }

    /**
     * Returns the name of the protocol.
     *
     * @return the protocol name
     */
    public final String getProtocol() {
        return protocol;
    }

    /**
     * Returns the major version number of the protocol.
     *
     * @return the major version number.
     */
    public final int getMajor() {
        return major;
    }

    /**
     * Returns the minor version number of the HTTP protocol.
     *
     * @return the minor version number.
     */
    public final int getMinor() {
        return minor;
    }


    /**
     * Obtains a hash code consistent with {@link #equals}.
     *
     * @return  the hashcode of this protocol version
     */
    @Override
    public final int hashCode() {
        return this.protocol.hashCode() ^ (this.major * 100000) ^ this.minor;
    }

    /**
     * Checks whether this instance has the same major and minor version as the arguments.
     *
     * @param major the major version to check.
     * @param minor the minor version to check.
     * @return whether this instance has the same major and minor version as the arguments.
     * @since 5.0
     */
    public final boolean equals(final int major, final int minor) {
        return this.major == major && this.minor == minor;
    }

    /**
     * Checks equality of this protocol version with an object.
     * The object is equal if it is a protocol version with the same
     * protocol name, major version number, and minor version number.
     * The specific class of the object is <i>not</i> relevant,
     * instances of derived classes with identical attributes are
     * equal to instances of the base class and vice versa.
     *
     * @param obj       the object to compare with
     *
     * @return  {@code true} if the argument is the same protocol version,
     *          {@code false} otherwise
     */
    @Override
    public final boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ProtocolVersion)) {
            return false;
        }
        final ProtocolVersion that = (ProtocolVersion) obj;

        return (this.protocol.equals(that.protocol) &&
               (this.major == that.major) &&
               (this.minor == that.minor));
    }

    /**
     * Formats this protocol version as a string.
     *
     * @return a protocol version string, like "HTTP/1.1"
     * @since 5.0
     */
    public String format() {
        final StringBuilder buffer = new StringBuilder();
        buffer.append(this.protocol);
        buffer.append('/');
        buffer.append(this.major);
        buffer.append('.');
        buffer.append(this.minor);
        return buffer.toString();
    }

    /**
     * Checks whether this protocol can be compared to another one.
     * Only protocol versions with the same protocol name can be
     * {@link #compareToVersion compared}.
     *
     * @param that      the protocol version to consider
     *
     * @return  {@code true} if {@link #compareToVersion compareToVersion}
     *          can be called with the argument, {@code false} otherwise
     */
    public boolean isComparable(final ProtocolVersion that) {
        return (that != null) && this.protocol.equals(that.protocol);
    }


    /**
     * Compares this protocol version with another one.
     * Only protocol versions with the same protocol name can be compared.
     * This method does <i>not</i> define a total ordering, as it would be
     * required for {@link java.lang.Comparable}.
     *
     * @param that      the protocol version to compare with
     *
     * @return   a negative integer, zero, or a positive integer
     *           as this version is less than, equal to, or greater than
     *           the argument version.
     *
     * @throws IllegalArgumentException
     *         if the argument has a different protocol name than this object,
     *         or if the argument is {@code null}
     */
    public int compareToVersion(final ProtocolVersion that) {
        Args.notNull(that, "Protocol version");
        Args.check(this.protocol.equals(that.protocol),
                "Versions for different protocols cannot be compared: %s %s", this, that);
        int delta = getMajor() - that.getMajor();
        if (delta == 0) {
            delta = getMinor() - that.getMinor();
        }
        return delta;
    }


    /**
     * Tests if this protocol version is greater or equal to the given one.
     *
     * @param version   the version against which to check this version
     *
     * @return  {@code true} if this protocol version is
     *          {@link #isComparable comparable} to the argument
     *          and {@link #compareToVersion compares} as greater or equal,
     *          {@code false} otherwise
     */
    public final boolean greaterEquals(final ProtocolVersion version) {
        return isComparable(version) && (compareToVersion(version) >= 0);
    }


    /**
     * Tests if this protocol version is less or equal to the given one.
     *
     * @param version   the version against which to check this version
     *
     * @return  {@code true} if this protocol version is
     *          {@link #isComparable comparable} to the argument
     *          and {@link #compareToVersion compares} as less or equal,
     *          {@code false} otherwise
     */
    public final boolean lessEquals(final ProtocolVersion version) {
        return isComparable(version) && (compareToVersion(version) <= 0);
    }


    /**
     * Converts this protocol version to a string.
     *
     * @return  a protocol version string, like "HTTP/1.1"
     */
    @Override
    public String toString() {
        return format();
    }

}
