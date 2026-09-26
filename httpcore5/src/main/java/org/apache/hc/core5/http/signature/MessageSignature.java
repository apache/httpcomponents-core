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
package org.apache.hc.core5.http.signature;

import java.util.Arrays;
import java.util.Objects;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.structured.StructuredFieldItem;
import org.apache.hc.core5.http.structured.StructuredFieldSerializer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.LangUtils;

/**
 * One labeled Byte Sequence value from the RFC 9421 {@code Signature} field.
 *
 * @since 5.5
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class MessageSignature {

    private final String label;
    private final byte[] value;

    /**
     * Creates a message signature from the given label and Byte Sequence value.
     *
     * @param label the signature label.
     * @param value the raw signature bytes.
     * @since 5.5
     */
    public MessageSignature(final String label, final byte[] value) {
        this.label = MessageSignatureSupport.validateLabel(label);
        this.value = Args.notNull(value, "Signature value").clone();
    }

    /**
     * Returns the signature label.
     *
     * @since 5.5
     */
    public String getLabel() {
        return label;
    }

    /**
     * Returns a copy of the raw signature bytes.
     *
     * @since 5.5
     */
    public byte[] getValue() {
        return value.clone();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof MessageSignature) {
            final MessageSignature that = (MessageSignature) obj;
            return Objects.equals(this.label, that.label)
                    && Arrays.equals(this.value, that.value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.label);
        hash = LangUtils.hashCode(hash, Arrays.hashCode(this.value));
        return hash;
    }

    @Override
    public String toString() {
        return label + "=" + StructuredFieldSerializer.serializeItem(StructuredFieldItem.ofByteSequence(value));
    }

}
