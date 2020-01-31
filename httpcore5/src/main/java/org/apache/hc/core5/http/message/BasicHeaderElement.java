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

import java.util.Objects;

import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.NameValuePair;

/**
 * Basic implementation of {@link HeaderElement}
 *
 * @since 4.0
 */
public class BasicHeaderElement implements HeaderElement {

    private static final NameValuePair[] EMPTY_NAME_VALUE_PAIR_ARRAY = new NameValuePair[0];

    private final String name;
    private final String value;
    private final NameValuePair[] parameters;

    /**
     * Constructor with name, value and parameters.
     *
     * @param name header element name
     * @param value header element value. May be {@code null}
     * @param parameters header element parameters. May be {@code null}.
     *   Parameters are copied by reference, not by value
     */
    public BasicHeaderElement(
            final String name,
            final String value,
            final NameValuePair[] parameters) {
        super();
        this.name = Objects.requireNonNull(name, "Name");
        this.value = value;
        if (parameters != null) {
            this.parameters = parameters;
        } else {
            this.parameters = EMPTY_NAME_VALUE_PAIR_ARRAY;
        }
    }

    /**
     * Constructor with name and value.
     *
     * @param name header element name
     * @param value header element value. May be {@code null}
     */
    public BasicHeaderElement(final String name, final String value) {
       this(name, value, null);
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
    public NameValuePair[] getParameters() {
        return this.parameters.clone();
    }

    @Override
    public int getParameterCount() {
        return this.parameters.length;
    }

    @Override
    public NameValuePair getParameter(final int index) {
        // ArrayIndexOutOfBoundsException is appropriate
        return this.parameters[index];
    }

    @Override
    public NameValuePair getParameterByName(final String name) {
        Objects.requireNonNull(name, "Name");
        NameValuePair found = null;
        for (final NameValuePair current : this.parameters) {
            if (current.getName().equalsIgnoreCase(name)) {
                found = current;
                break;
            }
        }
        return found;
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder();
        buffer.append(this.name);
        if (this.value != null) {
            buffer.append("=");
            buffer.append(this.value);
        }
        for (final NameValuePair parameter : this.parameters) {
            buffer.append("; ");
            buffer.append(parameter);
        }
        return buffer.toString();
    }

}

