/*
 * $HeadURL:$
 * $Revision:$
 * $Date:$
 *
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

package org.apache.http.params;

import org.apache.http.params.HttpParams;


/**
 * Simple two level stack of HTTP parameters (child/parent).
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * 
 * @version $Revision:$
 */
public final class SimpleParamStack extends AbstractHttpParams {

    private final HttpParams params;
    private final HttpParams parent;
    
    public SimpleParamStack(final HttpParams params, final HttpParams parent) {
        super();
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.params = params;
        this.parent = parent;
    }

    public HttpParams copy() {
        HttpParams clone = this.params.copy();
        return new SimpleParamStack(clone, this.parent);
    }

    public Object getParameter(final String name) {
        Object obj = this.params.getParameter(name);
        if (obj == null && this.parent != null) {
            obj = this.parent.getParameter(name);
        }
        return obj;
    }

    public boolean isParameterSet(final String name) {
        return this.params.isParameterSet(name);
    }

    public boolean removeParameter(final String name) {
        return this.removeParameter(name);
    }

    public HttpParams setParameter(final String name, final Object value) {
        return this.params.setParameter(name, value);
    }

    public HttpParams getParent() {
        return this.parent;
    }
    
}
