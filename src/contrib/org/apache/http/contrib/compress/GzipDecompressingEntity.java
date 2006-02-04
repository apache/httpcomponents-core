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

package org.apache.http.contrib.compress;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;

/**
 * Wrapping entity that decompresses {@link #getContent content}.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class GzipDecompressingEntity extends HttpEntityWrapper {

      private InputStream instream = null;
      
      public GzipDecompressingEntity(final HttpEntity entity) {
          super(entity);
      }
  
      public InputStream getContent() throws IOException {
          if (this.instream == null) {
              this.instream = new GZIPInputStream(wrappedEntity.getContent()); 
          }
          return this.instream;
      }
  
      public long getContentLength() {
          return -1;
      }
  
      public boolean isRepeatable() {
          // not repeatable, GZIPInputStream is created only once
          return false;
      }
  
} // class GzipDecompressingEntity