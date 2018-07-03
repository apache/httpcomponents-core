package org.apache.hc.core5.reactor;

import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.util.TimerTask;
import org.apache.hc.core5.util.WheelTimeout;

public class ReadTimeoutTask implements TimerTask {
	
	private InternalDataChannel dataChannel;
	
	@Override
	public void run(WheelTimeout timeout) throws Exception {
		final long currentTime = System.currentTimeMillis();
		if(dataChannel != null && !dataChannel.isClosed() && dataChannel.checkTimeout(currentTime)){
			final long delayTime = dataChannel.getLastReadTime() + dataChannel.getTimeout() - currentTime;
			SingleCoreIOReactor.timeWheel.newTimeout(this,delayTime,TimeUnit.MILLISECONDS);
		}
	}

	public ReadTimeoutTask(InternalDataChannel dataChannel) {
		this.dataChannel = dataChannel;
	}
	
	

}
