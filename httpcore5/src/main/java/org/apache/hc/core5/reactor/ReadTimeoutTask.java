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

package org.apache.hc.core5.reactor;

import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.util.TimerTask;
import org.apache.hc.core5.util.WheelTimeout;

public class ReadTimeoutTask implements TimerTask {

    private InternalDataChannel dataChannel;

    @Override
    public void run(final WheelTimeout timeout) throws Exception {
        final long currentTime = System.currentTimeMillis();
        if(dataChannel != null && !dataChannel.isClosed() && dataChannel.checkTimeout(currentTime)){
            final long delayTime = dataChannel.getLastReadTime() + dataChannel.getTimeout() - currentTime;
            dataChannel.setWheelTimeOut(SingleCoreIOReactor.timeWheel.newTimeout(this,delayTime,TimeUnit.MILLISECONDS));
        }
    }

    public ReadTimeoutTask(final InternalDataChannel dataChannel) {
        this.dataChannel = dataChannel;
    }
}
