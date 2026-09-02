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
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Objects;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.LangUtils;

/**
 * Immutable Structured Field bare Item.
 *
 * @since 5.5
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class StructuredFieldBareItem {

    private static final BigDecimal DECIMAL_ROUNDING_LIMIT = new BigDecimal("999999999999.9995");

    private final StructuredFieldType type;
    private final Object value;

    private StructuredFieldBareItem(final StructuredFieldType type, final Object value) {
        this.type = type;
        this.value = value;
    }

    /**
     * Creates an Integer bare Item.
     *
     * @param value the integer value.
     * @return the bare Item.
     * @throws IllegalArgumentException if the value is outside the Structured Field range.
     */
    public static StructuredFieldBareItem ofInteger(final long value) {
        checkInteger(value);
        return new StructuredFieldBareItem(StructuredFieldType.INTEGER, Long.valueOf(value));
    }

    /**
     * Creates a Decimal bare Item, applying the Structured Field rounding algorithm.
     *
     * @param value the decimal value.
     * @return the bare Item.
     * @throws IllegalArgumentException if the rounded value has more than 12 integer digits.
     */
    public static StructuredFieldBareItem ofDecimal(final BigDecimal value) {
        Args.notNull(value, "Decimal value");
        Args.check(value.abs().compareTo(DECIMAL_ROUNDING_LIMIT) < 0,
                "Structured Field Decimal has more than 12 integer digits after rounding");
        BigDecimal normalized = value.setScale(3, RoundingMode.HALF_EVEN).stripTrailingZeros();
        if (normalized.scale() < 1) {
            normalized = normalized.setScale(1);
        }
        final int integerDigits = Math.max(1, normalized.precision() - normalized.scale());
        Args.check(integerDigits <= 12,
                "Structured Field Decimal has more than 12 integer digits after rounding");
        return new StructuredFieldBareItem(StructuredFieldType.DECIMAL, normalized);
    }

    /**
     * Creates an ASCII String bare Item.
     *
     * @param value the printable ASCII value.
     * @return the bare Item.
     * @throws IllegalArgumentException if the value contains a character outside SP through {@code ~}.
     */
    public static StructuredFieldBareItem ofString(final String value) {
        StructuredFieldRules.validateString(value);
        return new StructuredFieldBareItem(StructuredFieldType.STRING, value);
    }

    /**
     * Creates a Token bare Item.
     *
     * @param value the token value.
     * @return the bare Item.
     * @throws IllegalArgumentException if the value is not an Structured Field token.
     */
    public static StructuredFieldBareItem ofToken(final String value) {
        StructuredFieldRules.validateToken(value);
        return new StructuredFieldBareItem(StructuredFieldType.TOKEN, value);
    }

    /**
     * Creates a Byte Sequence bare Item using a defensive copy.
     *
     * @param value the byte sequence.
     * @return the bare Item.
     */
    public static StructuredFieldBareItem ofByteSequence(final byte[] value) {
        Args.notNull(value, "Byte Sequence value");
        return new StructuredFieldBareItem(StructuredFieldType.BYTE_SEQUENCE, value.clone());
    }

    /**
     * Creates a Boolean bare Item.
     *
     * @param value the boolean value.
     * @return the bare Item.
     */
    public static StructuredFieldBareItem ofBoolean(final boolean value) {
        return new StructuredFieldBareItem(StructuredFieldType.BOOLEAN, Boolean.valueOf(value));
    }

    /**
     * Creates a Date bare Item expressed as seconds since the Unix epoch.
     *
     * @param epochSeconds seconds since the Unix epoch.
     * @return the bare Item.
     * @throws IllegalArgumentException if the value is outside the Structured Field Integer range.
     */
    public static StructuredFieldBareItem ofDate(final long epochSeconds) {
        checkInteger(epochSeconds);
        return new StructuredFieldBareItem(StructuredFieldType.DATE, Long.valueOf(epochSeconds));
    }

    /**
     * Creates a Unicode Display String bare Item.
     *
     * @param value the Unicode value.
     * @return the bare Item.
     * @throws IllegalArgumentException if the value contains an unpaired surrogate.
     */
    public static StructuredFieldBareItem ofDisplayString(final String value) {
        StructuredFieldRules.validateDisplayString(value);
        return new StructuredFieldBareItem(StructuredFieldType.DISPLAY_STRING, value);
    }

    private static void checkInteger(final long value) {
        Args.check(value >= -StructuredFieldRules.MAX_INTEGER && value <= StructuredFieldRules.MAX_INTEGER,
                "Structured Field Integer is outside the Structured Field range: %s", value);
    }

    /**
     * @return the bare Item type.
     */
    public StructuredFieldType getType() {
        return type;
    }

    /**
     * Returns the value using its Java representation. Byte sequences are defensively copied.
     *
     * @return the value.
     */
    public Object getValue() {
        return type == StructuredFieldType.BYTE_SEQUENCE ? ((byte[]) value).clone() : value;
    }

    /**
     * @return the Integer or Date value.
     * @throws IllegalStateException if this Item is not an Integer or Date.
     */
    public long getLongValue() {
        requireType(StructuredFieldType.INTEGER, StructuredFieldType.DATE);
        return ((Long) value).longValue();
    }

    /**
     * @return the Decimal value.
     * @throws IllegalStateException if this Item is not a Decimal.
     */
    public BigDecimal getDecimalValue() {
        requireType(StructuredFieldType.DECIMAL);
        return (BigDecimal) value;
    }

    /**
     * @return the String, Token, or Display String value.
     * @throws IllegalStateException if this Item is not one of those textual types.
     */
    public String getTextValue() {
        requireType(StructuredFieldType.STRING, StructuredFieldType.TOKEN, StructuredFieldType.DISPLAY_STRING);
        return (String) value;
    }

    /**
     * @return a defensive copy of the Byte Sequence value.
     * @throws IllegalStateException if this Item is not a Byte Sequence.
     */
    public byte[] getByteSequenceValue() {
        requireType(StructuredFieldType.BYTE_SEQUENCE);
        return ((byte[]) value).clone();
    }

    /**
     * @return the Boolean value.
     * @throws IllegalStateException if this Item is not a Boolean.
     */
    public boolean getBooleanValue() {
        requireType(StructuredFieldType.BOOLEAN);
        return ((Boolean) value).booleanValue();
    }

    private void requireType(final StructuredFieldType... expected) {
        for (final StructuredFieldType candidate : expected) {
            if (type == candidate) {
                return;
            }
        }
        throw new IllegalStateException("Bare Item is " + type);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof StructuredFieldBareItem) {
            final StructuredFieldBareItem that = (StructuredFieldBareItem) obj;
            if (this.type != that.type) {
                return false;
            }
            return this.type == StructuredFieldType.BYTE_SEQUENCE
                    ? Arrays.equals((byte[]) this.value, (byte[]) that.value)
                    : Objects.equals(this.value, that.value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.type);
        hash = LangUtils.hashCode(hash, this.type == StructuredFieldType.BYTE_SEQUENCE
                ? Arrays.hashCode((byte[]) this.value) : this.value.hashCode());
        return hash;
    }

    @Override
    public String toString() {
        return StructuredFieldSerializer.serializeBareItem(this);
    }
}
