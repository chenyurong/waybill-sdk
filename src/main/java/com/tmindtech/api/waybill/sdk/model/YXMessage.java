package com.tmindtech.api.waybill.sdk.model;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class YXMessage {
    private Integer async;
    private String topic;
    private String source;
    private String target;
    private String timestamp;
    private String sign;
    private String payload;

    public YXMessage(Integer async, String topic, String source, String target, String sign, String payload) {
        this.async = async;
        this.topic = topic;
        this.source = source;
        this.target = target;
        this.sign = sign;
        this.payload = payload;
        this.timestamp = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
