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

package org.apache.http.nio.buffer;

import java.io.IOException;
import java.io.OutputStream;

public class ContentOutputStream extends OutputStream {

    private final ContentOutputBuffer buffer;
    
    public ContentOutputStream(final ContentOutputBuffer buffer) {
        super();
        if (buffer == null) {
            throw new IllegalArgumentException("Output buffer may not be null");
        }
        this.buffer = buffer;
    }

    public void close() throws IOException {
        this.buffer.flush();
        this.buffer.shutdown();
    }

    public void flush() throws IOException {
        this.buffer.flush();
    }

    public void write(byte[] b, int off, int len) throws IOException {
        this.buffer.write(b, off, len);
    }

    public void write(byte[] b) throws IOException {
        this.buffer.write(b);
    }

    public void write(int b) throws IOException {
        this.buffer.write(b);
    }

}
