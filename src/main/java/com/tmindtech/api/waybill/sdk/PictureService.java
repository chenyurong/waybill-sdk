package com.tmindtech.api.waybill.sdk;

import com.tmindtech.api.waybill.sdk.model.ImageData;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface PictureService {


    @GET("api/logistics_label_address/get_label_image_by_uuid")
    Call<ImageData> getOrderPictureByPath(@Query("uuid") String uuid);

}
