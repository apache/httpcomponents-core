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

package org.apache.hc.core5.http.message;

import java.io.Serializable;
import java.util.Objects;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.util.Args;

/**
 * Immutable {@link Header}.
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class BasicHeader implements Header, Serializable {

    private static final long serialVersionUID = -5427236326487562174L;

    private final String name;
    private final String value;
    private final boolean sensitive;

    /**
     * Constructor with sensitivity flag
     *
     * @param name the header name
     * @param value the header value, taken as the value's {@link #toString()}.
     * @param sensitive sensitive flag
     *
     * @since 5.0
     */
    public BasicHeader(final String name, final Object value, final boolean sensitive) {
        super();
        this.name = Args.notNull(name, "Name");
        this.value = Objects.toString(value, null);
        this.sensitive = sensitive;
    }

    /**
     * Default constructor
     *
     * @param name the header name
     * @param value the header value, taken as the value's {@link #toString()}.
     */
    public BasicHeader(final String name, final Object value) {
        this(name, value, false);
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getValue() {
        return this.value;
    }

    @Override
    public boolean isSensitive() {
        return this.sensitive;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append(this.name).append(": ");
        if (this.value != null) {
            buf.append(this.value);
        }
        return buf.toString();
    }

    @Override
    public boolean equals(final Object obj) {
        // Helpful:  http://stackoverflow.com/questions/8180430/how-to-override-equals-method-in-java

        if (obj == null) {
            return false;
        }
        if (!BasicHeader.class.isAssignableFrom(obj.getClass())) {
            return false;
        }
        final BasicHeader other = (BasicHeader) obj;
        /*
         * If one is sensitive, do a sensitive comparison.
         */
        final boolean sensitive = this.sensitive || other.sensitive;

        // name cannot be null
        final String checkedName = sensitive ? this.name : this.name.toLowerCase();
        final String checkedOtherName = sensitive ? other.name : other.name.toLowerCase();
        final String checkedValue = sensitive ?
                                        this.value :
                                        (this.value == null) ? this.value : this.value.toLowerCase();
        final String checkedOtherValue = sensitive ?
                                             other.value :
                                             (other.value == null) ? other.value : other.value.toLowerCase();

        // name cannot be null
        if (!checkedName.equals(checkedOtherName)) {
            return false;
        }
        if ((checkedValue == null) ? (checkedOtherValue != null) : !checkedValue.equals(checkedOtherValue)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        // Helpful:  http://stackoverflow.com/questions/8180430/how-to-override-equals-method-in-java

        int hash = 13; // random prime number

        /*
         * Note that since equals returns true for headers with names (or values) that are different
         * but the same with a case insensitive comparison, we have to remove the case before
         * getting the hashcode of the name (or value).  This way the hashcode will be the
         * same even for headers that are equal but with a case-sensitive different name (or value).
         */

        // name cannot be null
        hash = 19 /* another random prime number */ * hash + name.toLowerCase().hashCode();
        hash = 19 * hash + (value != null ? value.toLowerCase().hashCode() : 0);
        return hash;
    }
}
