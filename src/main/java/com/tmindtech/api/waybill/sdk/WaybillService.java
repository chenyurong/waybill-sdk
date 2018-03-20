package com.tmindtech.api.waybill.sdk;

import com.tmindtech.api.waybill.sdk.model.StatusModel;
import retrofit2.Call;
import retrofit2.http.GET;

/**
 * 这是一个接口, 方法将被自动实现
 */
public interface WaybillService {
    /**
     * 获取一个微服务的 UP 状态
     *
     * @return 返回值是一个 Call, 用于异步回调, 也可以指定同步执行
     * @see Call
     */
    @GET("/health")
    Call<StatusModel> getStatus();
}
