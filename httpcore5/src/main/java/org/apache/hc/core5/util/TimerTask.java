package org.apache.hc.core5.util;

import java.util.concurrent.TimeUnit;

/**
 * A task which is executed after the delay specified with
 * {@link Timer#newTimeout(TimerTask, long, TimeUnit)}.
 */
public interface TimerTask {

    /**
     * Executed after the delay specified with
     * {@link Timer#newTimeout(TimerTask, long, TimeUnit)}.
     *
     * @param timeout a handle which is associated with this task
     */
    void run(WheelTimeout timeout) throws Exception;
}
