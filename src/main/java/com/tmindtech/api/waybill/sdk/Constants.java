package com.tmindtech.api.waybill.sdk;

import java.time.format.DateTimeFormatter;

/**
 * 常量定义
 */
public class Constants {
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static final Number NONE = 0;
    public static final Number SUCCESS = NONE;
    public static final Number UNKNOWN = -1;
    public static final Number USER_CANCEL = -2;
    public static final Number LABEL_NOT_EXIST = 0x00000001;
    public static final Number LABEL_NOT_READY = 0x00000002;
    public static final Number UNICODE_NOT_EXIST = 0x00000003;
    public static final Number SALEORDER_NOT_EXIST = 0x00000004;
    public static final Number PRINTER_NOT_EXIST = 0x00000100;
    public static final Number PRINTER_NOT_SUPPORT = 0x00000200;
    public static final Number MEDIA_NOT_SUPPORT = 0x00000300;
    public static final Number NETWORK_ERROR = 0x00010000;
}
