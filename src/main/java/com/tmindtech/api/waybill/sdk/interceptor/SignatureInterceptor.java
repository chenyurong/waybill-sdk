package com.tmindtech.api.waybill.sdk.interceptor;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

/**
 * 这是一个拦截器, 他会处理所有即将发出去请求, 在这里可以给所有请求统一增加请求头
 */
public class SignatureInterceptor implements Interceptor {
    private final String accessKey;
    private final String accessSecret;

    public SignatureInterceptor(String accessKey, String accessSecret) {
        this.accessKey = accessKey;
        this.accessSecret = accessSecret;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        //取得 Query 参数
        String query = request.url().query();
        //取得 HttpMethod
        String method = request.method();
        //TODO 把所有需要的东西取出来

        //然后就可以开始加密了
        //TODO 加密明天写

        return chain.proceed(chain.request());
    }
}
