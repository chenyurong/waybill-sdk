package com.tmindtech.api.waybill.sdk.exception;

/**
 * 错误码
 * Created by RexQian on 2017/2/16.
 */
public class ErrorCode {
    private int httpStatus;
    private int modelCode;
    private int errorCode;
    private String message;

    /**
     *
     * @param httpStatus HTTP状态码 {@link javax.servlet.http.HttpServletRequest}
     * @param modelCode 模块编号
     * @param errorCode 错误编号
     * @param message 错误消息
     */
    public ErrorCode(int httpStatus,
                     int modelCode,
                     int errorCode,
                     String message) {
        this.httpStatus = httpStatus;
        this.modelCode = modelCode;
        this.errorCode = errorCode;
        this.message = message;
    }

    /**
     * 格式化error message
     * @param args 格式化参数
     * @return 返回格式化后的新 {@link ErrorCode}
     */
    public ErrorCode format(Object... args) {
        return new ErrorCode(httpStatus, modelCode, errorCode,
                        String.format(this.message, args));
    }

    /**
     * 错误码定义
     * HTTP状态码（3位） + 模块编号(3位） + 错误编号（3位）
     * 位数不足补0
     * @return 错误码
     */
    public int getCode() {
        return httpStatus * 1000000 + modelCode * 1000 + errorCode;
    }

    public String getMessage() {
        return message;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
