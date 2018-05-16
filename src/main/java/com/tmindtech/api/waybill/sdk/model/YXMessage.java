package com.tmindtech.api.waybill.sdk.model;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class YXMessage {
    public String id;
    public Integer async;
    public String topic;
    public String source;
    public String target;
    public String timestamp;
    public String sign;
    public String payload;

    public YXMessage(String id, Integer async, String topic, String source, String target, String sign, String payload) {
        this.id = id;
        this.async = async;
        this.topic = topic;
        this.source = source;
        this.target = target;
        this.sign = sign;
        this.payload = payload;
        this.timestamp = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
