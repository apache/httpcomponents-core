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

package org.apache.hc.core5.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestHashedWheelTimer {

    private HashedWheelTimer timer;

    private long beforeTimeoutRunTime;

    private long timDeviation = 200l;

    @Before
    public void init(){
        timer = new HashedWheelTimer();
        beforeTimeoutRunTime = System.currentTimeMillis();
    }

    @Test
    public void testTimeoutInSSecond() throws InterruptedException{
        final CountDownLatch latch = new CountDownLatch(1);
        final WheelTimeout timeout = timer.newTimeout(new TimerTask(){
            @Override
            public void run(final WheelTimeout timeout) throws Exception {
                final long current =  System.currentTimeMillis();
                final long deadLine = beforeTimeoutRunTime + 1 * 1000;
                Assert.assertTrue(current >= deadLine &&
                        current <= deadLine + timDeviation);
                latch.countDown();
            }

        }, 1, TimeUnit.SECONDS);
        latch.await();
        Assert.assertTrue(timeout.isExpired());
    }

    @Test
    public void testTimeoutCancel() throws InterruptedException{
        final WheelTimeout timeout = timer.newTimeout(new TimerTask(){
            @Override
            public void run(final WheelTimeout timeout) throws Exception {
                final long current =  System.currentTimeMillis();
                final long deadLine = beforeTimeoutRunTime + 1 * 1000;
                Assert.assertTrue(current >= deadLine &&
                        current <= deadLine + timDeviation);
            }
        }, 1, TimeUnit.SECONDS);
        final boolean canceld = timeout.cancel();
        Thread.sleep(2000);
        Assert.assertTrue(canceld);
        Assert.assertTrue(timeout.isCancelled());
    }
}
