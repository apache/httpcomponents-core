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
		CountDownLatch latch = new CountDownLatch(1);
		WheelTimeout timeout = timer.newTimeout(new TimerTask(){
			@Override
			public void run(WheelTimeout timeout) throws Exception {
				long current =  System.currentTimeMillis();
				long deadLine = beforeTimeoutRunTime + 1 * 1000;
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
		WheelTimeout timeout = timer.newTimeout(new TimerTask(){
			@Override
			public void run(WheelTimeout timeout) throws Exception {
				long current =  System.currentTimeMillis();
				long deadLine = beforeTimeoutRunTime + 1 * 1000;
				Assert.assertTrue(current >= deadLine && 
						current <= deadLine + timDeviation);
			}
			
		}, 1, TimeUnit.SECONDS);
		boolean canceld = timeout.cancel();
		Thread.sleep(2000);
		Assert.assertTrue(canceld);
		Assert.assertTrue(timeout.isCancelled());
	}
}
