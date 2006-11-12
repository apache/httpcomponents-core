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

package org.apache.http.nio.impl.codecs;

import java.nio.ByteBuffer;

import org.apache.http.nio.impl.reactor.SessionOutputBuffer;
import org.apache.http.util.CharArrayBuffer;

public class ChunkEncoder extends AbstractContentEncoder {
    
    private final SessionOutputBuffer outbuf;
    private final CharArrayBuffer lineBuffer;
    
    public ChunkEncoder(final SessionOutputBuffer outbuf) {
        super();
        if (outbuf == null) {
            throw new IllegalArgumentException("Session output buffer may not be null");
        }
        this.outbuf = outbuf;
        this.lineBuffer = new CharArrayBuffer(16);
    }

    public int write(final ByteBuffer src) {
        if (src == null) {
            return 0;
        }
        assertNotCompleted();
        int chunk = src.remaining();
        if (chunk == 0) {
            return 0;
        }
        this.lineBuffer.clear();
        this.lineBuffer.append(Integer.toHexString(chunk));
        this.outbuf.writeLine(this.lineBuffer);
        this.outbuf.write(src);
        this.lineBuffer.clear();
        this.outbuf.writeLine(this.lineBuffer);
        return chunk;
    }

    public void complete() {
        assertNotCompleted();
        this.lineBuffer.clear();
        this.lineBuffer.append("0");
        this.outbuf.writeLine(this.lineBuffer);
        this.lineBuffer.clear();
        this.outbuf.writeLine(this.lineBuffer);
        this.completed = true;
    }
    
}
