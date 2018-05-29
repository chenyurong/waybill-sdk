package com.tmindtech.api.waybill.sdk.model;

public class PrintLog {
    public Integer countLimit;
    public Long id;
    public String logTime;
    public String target;
    public Long timeOut;
    public String uuidCode;

    public PrintLog(String logTime, String target, Long timeOut, String uuidCode) {
        this.countLimit = 0;
        this.logTime = logTime;
        this.target = target;
        this.timeOut = timeOut;
        this.uuidCode = uuidCode;
    }
}
