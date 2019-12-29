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

package org.apache.hc.core5.http2.frame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.Chars;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.config.H2Param;

@Internal
public final class FramePrinter {

    public void printFrameInfo(final RawFrame frame, final Appendable appendable) throws IOException {
        appendable.append("stream ").append(Integer.toString(frame.getStreamId())).append(" frame: ");
        final FrameType type = FrameType.valueOf(frame.getType());
        appendable.append(Objects.toString(type))
                .append(" (0x").append(Integer.toHexString(frame.getType())).append("); flags: ");
        final int flags = frame.getFlags();
        if (flags > 0) {
            switch (type) {
                case SETTINGS:
                case PING:
                    if ((flags & FrameFlag.ACK.value) > 0) {
                        appendable.append(FrameFlag.ACK.name()).append(" ");
                    }
                    break;
                case DATA:
                    if ((flags & FrameFlag.END_STREAM.value) > 0) {
                        appendable.append(FrameFlag.END_STREAM.name()).append(" ");
                    }
                    if ((flags & FrameFlag.PADDED.value) > 0) {
                        appendable.append(FrameFlag.PADDED.name()).append(" ");
                    }
                    break;
                case HEADERS:
                    if ((flags & FrameFlag.END_STREAM.value) > 0) {
                        appendable.append(FrameFlag.END_STREAM.name()).append(" ");
                    }
                    if ((flags & FrameFlag.END_HEADERS.value) > 0) {
                        appendable.append(FrameFlag.END_HEADERS.name()).append(" ");
                    }
                    if ((flags & FrameFlag.PADDED.value) > 0) {
                        appendable.append(FrameFlag.PADDED.name()).append(" ");
                    }
                    if ((flags & FrameFlag.PRIORITY.value) > 0) {
                        appendable.append(FrameFlag.PRIORITY.name()).append(" ");
                    }
                    break;
                case PUSH_PROMISE:
                    if ((flags & FrameFlag.END_HEADERS.value) > 0) {
                        appendable.append(FrameFlag.END_HEADERS.name()).append(" ");
                    }
                    if ((flags & FrameFlag.PADDED.value) > 0) {
                        appendable.append(FrameFlag.PADDED.name()).append(" ");
                    }
                    break;
                case CONTINUATION:
                    if ((flags & FrameFlag.END_HEADERS.value) > 0) {
                        appendable.append(FrameFlag.END_HEADERS.name()).append(" ");
                    }
            }
        }
        appendable.append("(0x").append(Integer.toHexString(flags)).append("); length: ").append(Integer.toString(frame.getLength()));
    }

    public void printPayload(final RawFrame frame, final Appendable appendable) throws IOException {

        final FrameType type = FrameType.valueOf(frame.getType());
        final ByteBuffer buf = frame.getPayloadContent();
        if (buf != null) {

            switch (type) {
                case SETTINGS:
                    if ((buf.remaining() % 6) == 0) {
                        while (buf.hasRemaining()) {
                            final int code = buf.getShort();
                            final H2Param param = H2Param.valueOf(code);
                            final int value = buf.getInt();
                            if (param != null) {
                                appendable.append(param.name());
                            } else {
                                appendable.append("0x").append(Integer.toHexString(code));
                            }
                            appendable.append(": ").append(Integer.toString(value)).append("\r\n");
                        }
                    } else {
                        appendable.append("Invalid\r\n");
                    }
                    break;
                case RST_STREAM:
                    if (buf.remaining() == 4) {
                        appendable.append("Code ");
                        final int code = buf.getInt();
                        final H2Error error = H2Error.getByCode(code);
                        if (error != null) {
                            appendable.append(error.name());
                        } else {
                            appendable.append("0x").append(Integer.toHexString(code));
                        }
                        appendable.append("\r\n");
                    } else {
                        appendable.append("Invalid\r\n");
                    }
                    break;
                case GOAWAY:
                    if (buf.remaining() >= 8) {
                        final int lastStream = buf.getInt();
                        appendable.append("Last stream ").append(Integer.toString(lastStream)).append("\r\n");
                        appendable.append("Code ");
                        final int code2 = buf.getInt();
                        final H2Error error2 = H2Error.getByCode(code2);
                        if (error2 != null) {
                            appendable.append(error2.name());
                        } else {
                            appendable.append("0x").append(Integer.toHexString(code2));
                        }
                        appendable.append("\r\n");
                        final byte[] tmp = new byte[buf.remaining()];
                        buf.get(tmp);
                        appendable.append(new String(tmp, StandardCharsets.US_ASCII));
                        appendable.append("\r\n");
                    } else {
                        appendable.append("Invalid\r\n");
                    }
                    break;
                case WINDOW_UPDATE:
                    if (buf.remaining() == 4) {
                        final int increment = buf.getInt();
                        appendable.append("Increment ").append(Integer.toString(increment)).append("\r\n");
                    } else {
                        appendable.append("Invalid\r\n");
                    }
                    break;
                case PUSH_PROMISE:
                    if (buf.remaining() > 4) {
                        final int streamId = buf.getInt();
                        appendable.append("Promised stream ").append(Integer.toString(streamId)).append("\r\n");
                        printData(buf, appendable);
                    } else {
                        appendable.append("Invalid\r\n");
                    }
                    break;
                default:
                    printData(frame.getPayload(), appendable);
            }
        }
    }

    public void printData(final ByteBuffer data, final Appendable appendable) throws IOException {
            final ByteBuffer buf = data.duplicate();
            final byte[] line = new byte[16];
            while (buf.hasRemaining()) {
                final int chunk = Math.min(buf.remaining(), line.length);
                buf.get(line, 0, chunk);

                for (int i = 0; i < chunk; i++) {
                    final char ch = (char) line[i];
                    if (ch > Chars.SP && ch <= Chars.DEL) {
                        appendable.append(ch);
                    } else if (Character.isWhitespace(ch)) {
                        appendable.append(' ');
                    } else {
                        appendable.append('.');
                    }
                }
                for (int i = chunk; i < 17; i++) {
                    appendable.append(' ');
                }

                for (int i = 0; i < chunk; i++) {
                    appendable.append(' ');
                    final int b = line[i] & 0xff;
                    final String s = Integer.toHexString(b);
                    if (s.length() == 1) {
                        appendable.append("0");
                    }
                    appendable.append(s);
                }
                appendable.append("\r\n");
            }
    }

}
