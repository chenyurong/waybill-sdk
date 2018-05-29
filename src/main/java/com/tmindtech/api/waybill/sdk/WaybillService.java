package com.tmindtech.api.waybill.sdk;

import com.tmindtech.api.waybill.sdk.model.Data;
import com.tmindtech.api.waybill.sdk.model.ExampleModel;
import com.tmindtech.api.waybill.sdk.model.ImageData;
import com.tmindtech.api.waybill.sdk.model.LabelData;
import com.tmindtech.api.waybill.sdk.model.StatusModel;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
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
     * @param body RequestBody
     * @return 面单唯一码列表
     */
    @Headers({"Content-Type: application/json", "Accept: application/json"})
    @POST("request")
    Call<Data> getLabelInfo(@Body RequestBody body);

    /**
     * 根据批次号重新下单获取面单唯一码列表
     *
     * @param body RequestBody
     * @return 面单唯一码列表
     */
    @Headers({"Content-Type: application/json", "Accept: application/json"})
    @POST("request")
    Call<Data> splitPackage(@Body RequestBody body);

    @Headers({"Content-Type: application/json", "Accept: application/json"})
    @POST("request")
    Call<LabelData> findPictureByPath(@Body RequestBody body);

    @Headers({"Content-Type: application/json", "Accept: application/json"})
    @POST("request")
    Call<ImageData> getOrderPictureByPath(@Body RequestBody body);

    @Headers({"Content-Type: application/json", "Accept: application/json"})
    @POST("request")
    Call<ResponseBody> savePrintResultLog(@Body RequestBody body);
}
