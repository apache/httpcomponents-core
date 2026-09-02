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

package org.apache.hc.core5.http.structured;

import java.math.BigDecimal;
import java.util.Objects;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.LangUtils;

/**
 * Immutable Structured Field Item.
 *
 * @since 5.5
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class StructuredFieldItem implements StructuredFieldValue, StructuredFieldMember {

    private final StructuredFieldBareItem bareItem;
    private final StructuredFieldParameters parameters;

    private StructuredFieldItem(final StructuredFieldBareItem bareItem, final StructuredFieldParameters parameters) {
        this.bareItem = Args.notNull(bareItem, "Bare Item");
        this.parameters = Args.notNull(parameters, "Parameters");
    }

    /**
     * Creates an Item without parameters.
     *
     * @param bareItem the bare Item value.
     * @return the immutable Item.
     */
    public static StructuredFieldItem of(final StructuredFieldBareItem bareItem) {
        return new StructuredFieldItem(bareItem, StructuredFieldParameters.EMPTY);
    }

    /**
     * @param value the Integer value.
     * @return an Integer Item without parameters.
     */
    public static StructuredFieldItem ofInteger(final long value) {
        return of(StructuredFieldBareItem.ofInteger(value));
    }

    /**
     * @param value the Decimal value.
     * @return a Decimal Item without parameters.
     */
    public static StructuredFieldItem ofDecimal(final BigDecimal value) {
        return of(StructuredFieldBareItem.ofDecimal(value));
    }

    /**
     * @param value the printable ASCII String value.
     * @return a String Item without parameters.
     */
    public static StructuredFieldItem ofString(final String value) {
        return of(StructuredFieldBareItem.ofString(value));
    }

    /**
     * @param value the Token value.
     * @return a Token Item without parameters.
     */
    public static StructuredFieldItem ofToken(final String value) {
        return of(StructuredFieldBareItem.ofToken(value));
    }

    /**
     * @param value the Byte Sequence value.
     * @return a Byte Sequence Item without parameters.
     */
    public static StructuredFieldItem ofByteSequence(final byte[] value) {
        return of(StructuredFieldBareItem.ofByteSequence(value));
    }

    /**
     * @param value the Boolean value.
     * @return a Boolean Item without parameters.
     */
    public static StructuredFieldItem ofBoolean(final boolean value) {
        return of(StructuredFieldBareItem.ofBoolean(value));
    }

    /**
     * @param epochSeconds seconds since the Unix epoch.
     * @return a Date Item without parameters.
     */
    public static StructuredFieldItem ofDate(final long epochSeconds) {
        return of(StructuredFieldBareItem.ofDate(epochSeconds));
    }

    /**
     * @param value the Unicode Display String value.
     * @return a Display String Item without parameters.
     */
    public static StructuredFieldItem ofDisplayString(final String value) {
        return of(StructuredFieldBareItem.ofDisplayString(value));
    }

    /**
     * Creates an Item with parameters.
     *
     * @param bareItem the bare Item value.
     * @param parameters the ordered parameters.
     * @return the immutable Item.
     */
    public static StructuredFieldItem of(
            final StructuredFieldBareItem bareItem, final StructuredFieldParameters parameters) {
        return new StructuredFieldItem(bareItem, parameters);
    }

    /**
     * @return the bare Item value.
     */
    public StructuredFieldBareItem getBareItem() {
        return bareItem;
    }

    @Override
    public StructuredFieldParameters getParameters() {
        return parameters;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof StructuredFieldItem) {
            final StructuredFieldItem that = (StructuredFieldItem) obj;
            return Objects.equals(this.bareItem, that.bareItem)
                    && Objects.equals(this.parameters, that.parameters);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.bareItem);
        hash = LangUtils.hashCode(hash, this.parameters);
        return hash;
    }

    @Override
    public String toString() {
        return StructuredFieldSerializer.serializeItem(this);
    }
}
