package org.apache.http.contrib.benchmark;

public class Stats {

    private long startTime = -1;
    private long finishTime = -1;
    private int successCount = 0;
    private int failureCount = 0;
    private String serverName = null;
    private long total = 0;
    private long contentLength = -1;
    
    public Stats() {
        super();
    }

    public void start() {
        this.startTime = System.currentTimeMillis();
    }

    public void finish() {
        this.finishTime = System.currentTimeMillis();
    }

    public long getFinishTime() {
        return this.finishTime;
    }

    public long getStartTime() {
        return this.startTime;
    }

    public long getDuration() {
        if (this.startTime < 0 || this.finishTime < 0) {
            throw new IllegalStateException();
        }
        return this.finishTime - this.startTime; 
    }
    
    public void incSuccessCount() {
        this.successCount++;
    }
    
    public void incFailureCount() {
        this.failureCount++;
    }

    public int getFailureCount() {
        return this.failureCount;
    }

    public int getSuccessCount() {
        return this.successCount;
    }

    public long getTotal() {
        return this.total;
    }
    
    public void incTotal(int n) {
        this.total += n;
    }
    
    public long getContentLength() {
        return this.contentLength;
    }

    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    public String getServerName() {
        return this.serverName;
    }

    public void setServerName(final String serverName) {
        this.serverName = serverName;
    }   
    
}
