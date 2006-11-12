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

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpException;
import org.apache.http.HttpMessage;
import org.apache.http.ProtocolException;
import org.apache.http.message.BufferedHeader;
import org.apache.http.nio.impl.SessionInputBuffer;
import org.apache.http.util.CharArrayBuffer;

public abstract class HttpMessageParser {
    
    private final SessionInputBuffer buffer;    
    
    private static final int READ_HEAD_LINE = 0;
    private static final int READ_HEADERS   = 1;
    private static final int COMPLETED      = 2;
    
    private int state;
    private boolean endOfStream;

    private HttpMessage message;
    private CharArrayBuffer lineBuf;
    private final List headerBufs;

    public HttpMessageParser(final SessionInputBuffer buffer) {
        super();
        if (buffer == null) {
            throw new IllegalArgumentException("Session input buffer may not be null");
        }
        this.buffer = buffer;
        this.state = READ_HEAD_LINE;
        this.endOfStream = false;
        this.headerBufs = new ArrayList();        
    }
    
    public void reset() {
        this.state = READ_HEAD_LINE;
        this.endOfStream = false;
        this.headerBufs.clear();
        this.message = null;
    }
    
    public int fillBuffer(final ReadableByteChannel channel) throws IOException {
        int bytesRead = this.buffer.fill(channel);
        if (bytesRead == -1) {
            this.endOfStream = true;
        }
        return bytesRead;
    }
    
    protected abstract HttpMessage createMessage(CharArrayBuffer buffer) 
        throws HttpException;
    
    private void parseHeadLine() throws HttpException {
        this.message = createMessage(this.lineBuf);
    }
    
    private void parseHeader() {
        CharArrayBuffer current = this.lineBuf;
        int count = this.headerBufs.size();
        if ((this.lineBuf.charAt(0) == ' ' || this.lineBuf.charAt(0) == '\t') && count > 0) {
            // Handle folded header line
            CharArrayBuffer previous = (CharArrayBuffer)this.headerBufs.get(count - 1);
            int i = 0;
            while (i < current.length()) {
                char ch = current.charAt(i);
                if (ch != ' ' && ch != '\t') {
                    break;
                }
                i++;
            }
            previous.append(' ');
            previous.append(current, i, current.length() - i);
        } else {
            this.headerBufs.add(current);
            this.lineBuf = null;
        }
    }

    public HttpMessage parse() throws IOException, HttpException {
        while (this.state != COMPLETED) {
            if (this.lineBuf == null) {
                this.lineBuf = new CharArrayBuffer(64);
            } else {
                this.lineBuf.clear();
            }
            if (!this.buffer.readLine(this.lineBuf, this.endOfStream)) {
                break;
            }
            switch (this.state) {
            case READ_HEAD_LINE:
                parseHeadLine();
                this.state = READ_HEADERS;
                break;
            case READ_HEADERS:
                if (this.lineBuf.length() > 0) {
                    parseHeader();
                } else {
                    this.state = COMPLETED;
                }
                break;
            }
            if (this.endOfStream && !this.buffer.hasData()) {
                this.state = COMPLETED;
            }
        }
        if (this.state == COMPLETED) {
            for (int i = 0; i < this.headerBufs.size(); i++) {
                CharArrayBuffer buffer = (CharArrayBuffer) this.headerBufs.get(i);
                try {
                    this.message.addHeader(new BufferedHeader(buffer));
                } catch (IllegalArgumentException ex) {
                    throw new ProtocolException(ex.getMessage());
                }
            }
            return this.message;
        } else {
            return null;
        }
    }

}
