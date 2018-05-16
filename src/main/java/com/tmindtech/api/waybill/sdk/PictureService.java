package com.tmindtech.api.waybill.sdk;

import com.tmindtech.api.waybill.sdk.model.ImageData;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface PictureService {

    @Headers({"Content-Type: application/json", "Accept: application/json"})
    @POST("request")
    Call<ImageData> getOrderPictureByPath(@Body RequestBody body);

}
