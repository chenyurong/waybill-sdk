package com.tmindtech.api.waybill.sdk;

import com.tmindtech.api.waybill.sdk.exception.ErrorCode;
import javax.servlet.http.HttpServletResponse;

public class Config {

    /**
     * 模块编号
     */
    public static final int MODEL_CODE = 1;

    public static final ErrorCode SALE_ORDER_ERROR_OR_NOT_EXIST
            = new ErrorCode(HttpServletResponse.SC_BAD_REQUEST, MODEL_CODE,
            1, "批次号错误或不存在");

    public static final ErrorCode LOGIC_URI_ERROR_OR_NOT_EXIST
            = new ErrorCode(HttpServletResponse.SC_BAD_REQUEST, MODEL_CODE,
            2, "逻辑链接错误或不存在");

    public static final ErrorCode WAY_BILL_NOT_READY
            = new ErrorCode(HttpServletResponse.SC_BAD_REQUEST, MODEL_CODE,
            3, "图片还在生成");

    public static final ErrorCode ERROR_EXECUTOR_INTERRUPT
            = new ErrorCode(HttpServletResponse.SC_BAD_REQUEST, MODEL_CODE,
            4, "线程连接池中断异常");
}
