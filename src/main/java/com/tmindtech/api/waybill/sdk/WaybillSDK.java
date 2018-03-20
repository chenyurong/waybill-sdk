package com.tmindtech.api.waybill.sdk;

import com.tmindtech.api.waybill.sdk.interceptor.SignatureInterceptor;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * 实际上的这个 SDK 功能相当复杂, 这个 demo 的结构可能是完全不对的
 * 这个 SDK 要判断当前仓库中, 各个本地服务是否可用, 如果一个本地服务不可用要自动切换使用另一个本地服务
 * 如果所有本地服务全部不可用则直接访问云端服务器(接口是一样的, 在内部实现切换)
 * 所有的一切都要在 SDK 内自动完成, 不需要外部干预
 */
public class WaybillSDK {
    private final String accessKey;
    private final String accessSecret;

    /**
     * SDK 初始化时传入必要的参数(由调用者传入)
     *
     * @param accessKey    accessKey
     * @param accessSecret accessSecret
     */
    public WaybillSDK(String accessKey, String accessSecret) {
        this.accessKey = accessKey;
        this.accessSecret = accessSecret;
    }

    public WaybillService getWaybillService() {
        //每个 OkHttpClient 都会有一个线程池, 如果拦截器不会变化的话, 可以缓存下来, 每次都重新生成一个 OkHttpClient 可能有性能问题
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new SignatureInterceptor(accessKey, accessSecret))
                .addNetworkInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))    //实际工作时不要用 BODY, 输出太多了
                .build();

        return new Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(okHttpClient)
                .build()
                .create(WaybillService.class);
    }
}
