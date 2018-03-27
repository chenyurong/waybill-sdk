package com.tmindtech.api.waybill.sdk.exception;

/**
 * 自定义异常基础类
 * 从RuntimeException继承以直接支持Transactional
 * Created by RexQian on 2017/2/9.
 */
public class AwesomeException extends RuntimeException {

    /**
     * HTTP请求返回码
     */
    public int statusCode;
    /**
     * 错误码
     */
    public int code;

    /**
     * @param statusCode HTTP请求返回码
     * @param code 错误码
     * @param message 错误消息
     */
    public AwesomeException(int statusCode, int code, String message) {
        super(message);
        this.statusCode = statusCode;
        this.code = code;
    }

    public AwesomeException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.statusCode = errorCode.getHttpStatus();
        this.code = errorCode.getCode();
    }

    @Override
    public String toString() {
        return "statusCode: " + statusCode
                + "\ncode: " + code
                + "\n" + super.toString();
    }
}
