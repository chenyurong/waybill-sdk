package com.tmindtech.api.waybill.sdk;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface PictureService {


    @GET("api/logistics_label_address/get_label_image_by_uuid")
    Call<ResponseBody> getOrderPictureByPath(@Query("uuid") String uuid);

}
