package com.tmindtech.api.waybill.sdk;

import com.tmindtech.api.waybill.sdk.exception.ErrorCode;
import javax.servlet.http.HttpServletResponse;

public class Config {

    /**
     * 模块编号
     */
    public static final int MODEL_CODE = 1;

    public static final ErrorCode OrderNotExist
            = new ErrorCode(HttpServletResponse.SC_BAD_REQUEST, MODEL_CODE,
            1, "批次号错误或不存在");

    public static final ErrorCode LabelNotExist
            = new ErrorCode(HttpServletResponse.SC_BAD_REQUEST, MODEL_CODE,
            2, "逻辑链接错误或不存在");

    public static final ErrorCode LabelNotReady
            = new ErrorCode(HttpServletResponse.SC_BAD_REQUEST, MODEL_CODE,
            3, "图片还在生成");

    public static final ErrorCode ErrorEexcutorInterrupt
            = new ErrorCode(HttpServletResponse.SC_BAD_REQUEST, MODEL_CODE,
            4, "线程连接池中断异常");

    public static final ErrorCode PrinterNotExist
            = new ErrorCode(HttpServletResponse.SC_BAD_REQUEST, MODEL_CODE,
            5, "该打印机不存在");
}
