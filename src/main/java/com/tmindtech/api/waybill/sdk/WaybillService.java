package com.tmindtech.api.waybill.sdk;

import com.tmindtech.api.waybill.sdk.model.Data;
import com.tmindtech.api.waybill.sdk.model.ExampleModel;
import com.tmindtech.api.waybill.sdk.model.LabelData;
import com.tmindtech.api.waybill.sdk.model.StatusModel;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

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
    @GET("health")
    Call<StatusModel> getStatus();

    /**
     * 这是一个带参数的示例
     * 如果参数为 null, 则最终请求不会带有这个参数
     */
    @GET("api/v1/catlog")
    Call<ExampleModel> getCatlog(@Query("id") long id, @Query("flag") boolean flag, @Query("type") String type);

    /**
     * 对于一些包含固定值的参数的 API, 可以为其建立一个快捷调用
     */
    default Call<ExampleModel> getCatlog(long id, boolean flag) {
        return getCatlog(id, flag, "json");
    }

    /**
     * 根据批次号获取生成面单的唯一码列表
     *
     * @param yxMessage yxMessage
     * @return 面单唯一码列表
     */
    @POST
    Call<Data> getLabelInfo(@Field("yx_message") String yxMessage);

    /**
     * 根据批次号重新下单获取面单唯一码列表
     *
     * @param yxMessage yxMessage
     * @return 面单唯一码列表
     */
    @POST
    Call<Data> splitPackage(@Field("yx_message") String yxMessage);

    @GET
    Call<LabelData> findPictureByPath(@Field("yx_message") String yxMessage);
}
