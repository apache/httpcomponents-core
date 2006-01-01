/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2004 The Apache Software Foundation
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

package org.apache.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public interface HttpEntity {

    public static final String CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_ENCODING = "Content-Encoding";
    
    /**
     * Tells if the entity is capable to produce its data more than once.
     * A repeatable entity's getContent() and writeTo(OutputStream) methods
     * can be called more than once whereas a non-repeatable entity's can not.
     * @return true if the entity is repeatable, false otherwise.
     */
    boolean isRepeatable();

    boolean isChunked();

    long getContentLength();
    
    Header getContentType();
    
    Header getContentEncoding();
    
    /**
     * Creates a new InputStream object of the entity. It is a programming error
     * to return the same InputStream object more than once.
     * @return a new input stream that returns the entity data.
     * @throws IOException if the stream could not be created
     */
    InputStream getContent() throws IOException;
    
    /**
     * Writes the entity content to the output stream either partially or entirely.
     * This method may either write the entire content in one go, if it is feasible 
     * to do so, or store the output stream as a local variable and use it internally 
     * to write the content in parts. If the former case this method MUST return
     * <tt>true</tt> to indicate that the output stream can be closed. In the latter
     * case the output stream MUST be closed once the last content part is written
     * in order to ensure that content codings that emit a closing chunk are properly
     * terminated.  
     * 
     * @param outstream the output stream to write entity content to
     * @return <tt>true</tt> if the entire entity content has been written, 
     *   <tt>false</tt> otherwise.
     * @throws IOException if an I/O error occurs
     */
    boolean writeTo(OutputStream outstream) throws IOException;
    
}
