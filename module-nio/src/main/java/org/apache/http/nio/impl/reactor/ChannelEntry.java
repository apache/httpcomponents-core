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

package org.apache.http.nio.impl.reactor;

import java.nio.channels.SocketChannel;

public class ChannelEntry {

    private final SocketChannel channel;
    private final Object attachment;
    
    public ChannelEntry(final SocketChannel channel, final Object attachment) {
        super();
        if (channel == null) {
            throw new IllegalArgumentException("Socket channel may not be null");
        }
        this.channel = channel;
        this.attachment = attachment;
    }

    public ChannelEntry(final SocketChannel channel) {
        this(channel, null);
    }

    public Object getAttachment() {
        return this.attachment;
    }

    public SocketChannel getChannel() {
        return this.channel;
    }
    
}
