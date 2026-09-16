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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.MessageHeaders;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.structured.StructuredFieldDictionary;
import org.apache.hc.core5.http.structured.StructuredFieldHeaders;
import org.apache.hc.core5.http.structured.StructuredFieldInnerList;
import org.apache.hc.core5.http.structured.StructuredFieldItem;
import org.apache.hc.core5.http.structured.StructuredFieldMember;
import org.apache.hc.core5.http.structured.StructuredFieldType;
import org.apache.hc.core5.util.Args;

/**
 * RFC 9421 parsing and serialization of {@code Signature-Input} and {@code Signature}.
 *
 * @since 5.5
 */
public final class MessageSignatureFields {

    /**
     * The {@code Signature-Input} header name.
     *
     * @since 5.5
     */
    public static final String SIGNATURE_INPUT = "Signature-Input";

    /**
     * The {@code Signature} header name.
     *
     * @since 5.5
     */
    public static final String SIGNATURE = "Signature";

    private MessageSignatureFields() {
    }

    /**
     * Parses the {@code Signature-Input} field into its labeled inputs, in wire order.
     *
     * @param headers the message headers.
     * @return the parsed signature inputs.
     * @throws ParseException if the field is malformed or a label is repeated.
     * @since 5.5
     */
    public static List<MessageSignatureInput> parseSignatureInput(final MessageHeaders headers) throws ParseException {
        Args.notNull(headers, "Message headers");
        final List<MessageSignatureInput> result = new ArrayList<>();
        final Set<String> labels = new HashSet<>();
        for (final Map.Entry<String, StructuredFieldMember> entry
                : StructuredFieldHeaders.parseDictionaryEntries(headers, SIGNATURE_INPUT)) {
            if (!labels.add(entry.getKey())) {
                throw new ParseException("Duplicate Signature-Input label: " + entry.getKey());
            }
            if (!(entry.getValue() instanceof StructuredFieldInnerList)) {
                throw new ParseException("Signature-Input member must be an Inner List: " + entry.getKey());
            }
            result.add(MessageSignatureInput.fromInnerList(
                    entry.getKey(), (StructuredFieldInnerList) entry.getValue()));
        }
        return result;
    }

    /**
     * Parses the {@code Signature} field into its labeled signatures, in wire order.
     *
     * @param headers the message headers.
     * @return the parsed signatures.
     * @throws ParseException if the field is malformed or a label is repeated.
     * @since 5.5
     */
    public static List<MessageSignature> parseSignature(final MessageHeaders headers) throws ParseException {
        Args.notNull(headers, "Message headers");
        final List<MessageSignature> result = new ArrayList<>();
        final Set<String> labels = new HashSet<>();
        for (final Map.Entry<String, StructuredFieldMember> entry
                : StructuredFieldHeaders.parseDictionaryEntries(headers, SIGNATURE)) {
            if (!labels.add(entry.getKey())) {
                throw new ParseException("Duplicate Signature label: " + entry.getKey());
            }
            if (!(entry.getValue() instanceof StructuredFieldItem)) {
                throw new ParseException("Signature member must be a Byte Sequence Item: " + entry.getKey());
            }
            final StructuredFieldItem item = (StructuredFieldItem) entry.getValue();
            if (item.getBareItem().getType() != StructuredFieldType.BYTE_SEQUENCE
                    || !item.getParameters().isEmpty()) {
                throw new ParseException("Signature member must be an unparameterized Byte Sequence: "
                        + entry.getKey());
            }
            result.add(new MessageSignature(entry.getKey(), item.getBareItem().getByteSequenceValue()));
        }
        return result;
    }

    /**
     * Serializes the signature inputs into a {@code Signature-Input} header.
     *
     * @param inputs the signature inputs.
     * @return the formatted header.
     * @since 5.5
     */
    public static Header formatSignatureInput(final List<MessageSignatureInput> inputs) {
        final List<MessageSignatureInput> source = Args.notNull(inputs, "Signature inputs");
        final StructuredFieldDictionary.Builder builder = StructuredFieldDictionary.builder();
        final Set<String> labels = new HashSet<>();
        for (final MessageSignatureInput input : source) {
            final MessageSignatureInput checked = Args.notNull(input, "Signature input");
            Args.check(labels.add(checked.getLabel()), "Duplicate Signature-Input label: %s", checked.getLabel());
            builder.put(checked.getLabel(), checked.toInnerList());
        }
        return StructuredFieldHeaders.format(SIGNATURE_INPUT, builder.build());
    }

    /**
     * Serializes the signatures into a {@code Signature} header.
     *
     * @param signatures the signatures.
     * @return the formatted header.
     * @since 5.5
     */
    public static Header formatSignature(final List<MessageSignature> signatures) {
        final List<MessageSignature> source = Args.notNull(signatures, "Signatures");
        final StructuredFieldDictionary.Builder builder = StructuredFieldDictionary.builder();
        final Set<String> labels = new HashSet<>();
        for (final MessageSignature signature : source) {
            final MessageSignature checked = Args.notNull(signature, "Signature");
            Args.check(labels.add(checked.getLabel()), "Duplicate Signature label: %s", checked.getLabel());
            builder.put(checked.getLabel(), StructuredFieldItem.ofByteSequence(checked.getValue()));
        }
        return StructuredFieldHeaders.format(SIGNATURE, builder.build());
    }

    /**
     * Verifies the RFC 9421 requirement that Signature-Input and Signature carry the same labels.
     *
     * @param inputs     the parsed signature inputs.
     * @param signatures the parsed signatures.
     * @throws MessageSignatureException if either side repeats a label or the label sets differ.
     * @since 5.5
     */
    public static void validateMatchingLabels(
            final List<MessageSignatureInput> inputs, final List<MessageSignature> signatures)
            throws MessageSignatureException {
        final List<MessageSignatureInput> inputList = Args.notNull(inputs, "Signature inputs");
        final List<MessageSignature> signatureList = Args.notNull(signatures, "Signatures");
        final Set<String> inputLabels = new HashSet<>();
        for (final MessageSignatureInput input : inputList) {
            final MessageSignatureInput checked = Args.notNull(input, "Signature input");
            if (!inputLabels.add(checked.getLabel())) {
                throw new MessageSignatureException("Duplicate Signature-Input label: " + checked.getLabel());
            }
        }
        final Set<String> signatureLabels = new HashSet<>();
        for (final MessageSignature signature : signatureList) {
            final MessageSignature checked = Args.notNull(signature, "Signature");
            if (!signatureLabels.add(checked.getLabel())) {
                throw new MessageSignatureException("Duplicate Signature label: " + checked.getLabel());
            }
        }
        if (!inputLabels.equals(signatureLabels)) {
            throw new MessageSignatureException("Signature-Input and Signature labels do not match");
        }
    }

}
