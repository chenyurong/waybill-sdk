package com.tmindtech.api.waybill.sdk;

import java.time.format.DateTimeFormatter;

/**
 * 常量定义
 */
public class Constants {
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final String BASE_URL = "http://localhost:8080/tmind/v1/";
    public static final String GET_PICTURE_URL = "http://localhost:8080/tmind/v1/pictures/";
}
